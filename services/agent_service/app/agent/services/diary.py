"""Agent 访谈业务编排（PRD §3.1.2 / §3.1.6 / §A.6）。

流程：
1. start_session：检查 active session（§3.1.5 限制 1 个 / 分钟 / elder）→ 建 session
   → DSH greeting → TTS greeting → 返 {session_id, greeting_text, greeting_audio_url, max_turns}
2. handle_turn：
   a. 老人端 auth 校验（principal.elder_id == session.elder_id）
   b. 关键词拦截（§B2 emergency → 触发 finalize；§B1/B4 money/medical → 走 canned）
   c. DSH chat（带 system + turns + 新 elder_text）→ 截断 25
   d. TTS reply → audio_url
   e. append turn → 更新 session.turns
   f. §C1/C2 启发式 → 服务端强制 finalize
3. finalize_session：transition → DSH save → 解析 JSON {text, summary} → 截断 100/60
   → AudioSegments 从 turns 组装 → DiaryEntry 落库 → 返回 {diary_id, text, summary}
"""

from __future__ import annotations

from typing import Any, cast
from uuid import UUID

from elder_common.auth import Principal
from elder_common.constants import ErrorCode
from elder_common.errors import AppError
from elder_common.events import (
    SESSION_ABANDONED,
    SESSION_FINALIZED,
    SESSION_STARTED,
    SESSION_TURN,
)
from elder_common.logging import emit_event
from elder_common.repos import diary_entry as entry_repo
from elder_common.repos import diary_session as session_repo
from elder_common.schemas.diary_entry import AudioSegment, DiaryEntryCreate
from elder_common.schemas.diary_session import (
    SessionCreate,
    SessionStatus,
    Turn,
)
from elder_common.time import now_utc
from elder_common.upstream import dsh, tts

from services.agent_service.app.agent.prompts import (
    greeting_prompt,
    save_prompt,
    system_prompt,
)
from services.agent_service.app.agent.safety import (
    count_dimensions,
    detect_emergency,
    detect_explicit_close,
    detect_money_or_medical,
)
from services.agent_service.app.agent.services.text import (
    MAX_REPLY_CHARS,
    MAX_SUMMARY_CHARS,
    MAX_TEXT_CHARS,
    MAX_TURNS,
    build_dsh_messages,
    estimate_duration_ms,
    parse_dsh_chat_response,
    parse_dsh_save_response,
    truncate_chars,
)
from services.agent_service.app.agent.state import (
    TransitionEvent,
)
from services.agent_service.app.agent.state import (
    transition as transition_status,
)

# ---- canned 回复（§3.1.4 §B1 / §B2 / §B4 服务端绕过 LLM）----
CANNED_MONEY_MEDICAL = "嗯，咱们聊点别的吧。"  # §B1 / §B4
CANNED_EMERGENCY_REPLY = "好的，咱们先把今天说的记下来。"  # §B2 收尾过渡


def _verify_elder(principal: Principal, elder_id: UUID) -> None:
    """路由层已 require_role('elder')；这里再校验 elder_id 归属。"""
    if principal.elder_id != str(elder_id):
        raise AppError(
            code=ErrorCode.FORBIDDEN,
            message="elder_id mismatch",
            http_status=403,
        )


async def start_session(principal: Principal) -> dict[str, object]:
    """§3.1.2 / §6.3：建访谈会话，返 greeting。"""
    elder_id = UUID(cast(str, principal.elder_id))
    # §E.5：每 elder 同时仅一个 active session
    existing = await session_repo.find_active_by_elder(elder_id)
    if existing is not None:
        raise AppError(
            code=ErrorCode.CONFLICT,
            message="active session already exists",
            http_status=409,
        )

    session = await session_repo.create(SessionCreate(elder_id=elder_id))
    emit_event(SESSION_STARTED, session_id=str(session.id), elder_id=str(elder_id))

    # Greeting —— mock LLM 也走通
    messages = [
        {"role": "system", "content": system_prompt()},
        {"role": "system", "content": greeting_prompt()},
        {"role": "user", "content": "你好"},
    ]
    try:
        raw = dsh.chat(messages)
    except AppError:
        raw = "今天都干啥了？"  # §7 容错：上游挂了用兜底问候
    greeting_text = truncate_chars(raw.strip(), MAX_REPLY_CHARS) or "今天都干啥了？"
    tts_audio = _safe_tts(greeting_text)

    return {
        "session_id": str(session.id),
        "created_at": session.created_at.isoformat(),
        "max_turns": MAX_TURNS,
        "greeting_text": greeting_text,
        "greeting_audio_url": tts_audio.url,
        "greeting_audio_expires_in": tts_audio.expires_in,
    }


def _detect_finalize(turns: list[Turn], explicit_close: bool) -> bool:
    """§C1 + §C2 + §C3 收尾判断（服务端兜底，AGENTS.md §11）。"""
    if explicit_close:
        return True
    if len(turns) >= MAX_TURNS:
        return True  # §C2
    elder_texts = [t.elder_text for t in turns]
    return count_dimensions(elder_texts) >= 2  # §C1


def _safe_tts(text: str) -> Any:  # type: ignore[explicit-any] # 容错 fallback 用 Any（mock 不可达时）
    """§A.1：dev / test 阶段 mock TTS 不可达时容错（用占位 URL）。"""
    from collections import namedtuple

    from elder_common.errors import AppError as _AppError

    T = namedtuple("T", ["url", "expires_in"])
    try:
        return tts.synthesize(text)
    except _AppError:
        return T(url="https://mock.cos/tts-fallback.m4a", expires_in=600)


async def handle_turn(
    session_id: UUID,
    turn_no: int,
    elder_text: str,
    elder_audio_cos_key: str,
    principal: Principal,
) -> dict[str, object]:
    """§3.1.2 / §6.3：处理一轮 —— 返 reply + audio_url + should_finalize + turns_left。"""
    session = await session_repo.get_by_id(session_id)
    if session is None:
        raise AppError(code=ErrorCode.NOT_FOUND, message="session not found", http_status=404)
    _verify_elder(principal, session.elder_id)
    if session.status is not SessionStatus.ACTIVE:
        raise AppError(
            code=ErrorCode.CONFLICT,
            message=f"session not active ({session.status.value})",
            http_status=409,
        )
    if turn_no != len(session.turns) + 1:
        raise AppError(
            code=ErrorCode.BAD_REQUEST,
            message=f"turn_no must be {len(session.turns) + 1}",
            http_status=400,
        )

    assistant_text: str
    should_finalize: bool

    # §B2 急救信号 → 绕过 LLM，准备 finalize
    if detect_emergency(elder_text):
        assistant_text = CANNED_EMERGENCY_REPLY
        should_finalize = True
    # §B1 / §B4 → 绕过 LLM，返回 canned
    elif detect_money_or_medical(elder_text):
        assistant_text = CANNED_MONEY_MEDICAL
        should_finalize = False
    else:
        # 正常路径：调 DSH
        messages = build_dsh_messages(
            system_prompt(),
            [(t.elder_text, t.assistant_text) for t in session.turns],
            new_user_text=elder_text,
        )
        try:
            raw = dsh.chat(messages)
        except AppError:
            raw = "嗯，然后呢？"  # §7 容错
        reply, llm_should_finalize = parse_dsh_chat_response(raw)
        assistant_text = truncate_chars(reply.strip(), MAX_REPLY_CHARS) or "嗯。"
        should_finalize = llm_should_finalize or detect_explicit_close(elder_text)

    # TTS + 落 turn
    tts_audio = _safe_tts(assistant_text)
    new_turn = Turn(
        turn_no=turn_no,
        elder_text=elder_text,
        elder_audio_cos_key=elder_audio_cos_key,
        assistant_text=assistant_text,
        assistant_audio_cos_key=tts_audio.url,  # §5.8 暂以 URL 直接做 key（mock）
    )
    updated = await session_repo.append_turn(session_id, new_turn)
    assert updated is not None
    emit_event(
        SESSION_TURN,
        session_id=str(session_id),
        turn_no=turn_no,
        elder_id=str(session.elder_id),
    )

    # §C1 / §C2 服务端强制 finalize
    if not should_finalize:
        should_finalize = _detect_finalize(updated.turns, explicit_close=False)

    return {
        "turn_no": turn_no,
        "assistant_text": assistant_text,
        "assistant_audio_url": tts_audio.url,
        "assistant_audio_expires_in": tts_audio.expires_in,
        "should_finalize": should_finalize,
        "turns_left": max(0, MAX_TURNS - len(updated.turns)),
    }


async def finalize_session(
    session_id: UUID,
    turn_no: int,
    elder_text: str,
    elder_audio_cos_key: str,
    principal: Principal,
) -> dict[str, object]:
    """§3.1.6 / §6.3：finalize —— 整理日记 + 落库。"""
    session = await session_repo.get_by_id(session_id)
    if session is None:
        raise AppError(code=ErrorCode.NOT_FOUND, message="session not found", http_status=404)
    _verify_elder(principal, session.elder_id)
    if session.status is not SessionStatus.FINALIZED:
        # 把最后一轮追加后再 finalize（按 §6.3 finalize 也带 elder_text / cos_key）
        if turn_no == len(session.turns) + 1 and elder_text:
            # 服务端先用 LLM 模拟一轮回复再落库 —— MVP 简化：复用上轮 reply 或固定一句
            messages = build_dsh_messages(
                system_prompt(),
                [(t.elder_text, t.assistant_text) for t in session.turns],
                new_user_text=elder_text,
            )
            try:
                raw = dsh.chat(messages)
            except AppError:
                raw = "好的，记下来了。"
            reply, _ = parse_dsh_chat_response(raw)
            assistant_text = truncate_chars(reply.strip(), MAX_REPLY_CHARS) or "好的。"
            tts_audio = _safe_tts(assistant_text)
            new_turn = Turn(
                turn_no=turn_no,
                elder_text=elder_text,
                elder_audio_cos_key=elder_audio_cos_key,
                assistant_text=assistant_text,
                assistant_audio_cos_key=tts_audio.url,
            )
            session = await session_repo.append_turn(session_id, new_turn)
            assert session is not None

        session = await transition_status(session, TransitionEvent.FINALIZE_REQUESTED)
        assert session is not None

    # DSH save —— 把 turns 喂进去生成 text + summary
    save_messages = [
        {"role": "system", "content": save_prompt()},
        {
            "role": "user",
            "content": "对话内容：\n"
            + "\n".join(f"老人：{t.elder_text}\n助手：{t.assistant_text}" for t in session.turns),
        },
    ]
    try:
        raw = dsh.chat(save_messages)
    except AppError:
        # 兜底：用第一段 elder_text 当 text，summary 取前 60 字
        first_text = session.turns[0].elder_text if session.turns else "今天没聊什么。"
        raw = f'{{"text":"{first_text}","summary":"{first_text[:MAX_SUMMARY_CHARS]}"}}'
    text_raw, summary_raw = parse_dsh_save_response(raw)
    text_final = truncate_chars(text_raw.strip(), MAX_TEXT_CHARS)
    summary_final = truncate_chars(summary_raw.strip(), MAX_SUMMARY_CHARS)

    # AudioSegments from turns
    audio_segments: list[AudioSegment] = [
        AudioSegment(
            cos_key=t.elder_audio_cos_key,
            asr_text=t.elder_text,
            duration_ms=estimate_duration_ms(t.elder_text),
        )
        for t in session.turns
    ]

    # date 按 elder 时区 —— 简化用 UTC date（PR 4 client 端再调整）
    today = now_utc().strftime("%Y-%m-%d")

    entry = await entry_repo.create(
        DiaryEntryCreate(
            elder_id=session.elder_id,
            session_id=session.id,
            date=today,
            text=text_final,
            summary=summary_final,
            audio_segments=audio_segments,
            pending_id=None,
        )
    )
    emit_event(
        SESSION_FINALIZED,
        session_id=str(session.id),
        diary_id=str(entry.id),
        elder_id=str(session.elder_id),
    )
    return {
        "diary_id": str(entry.id),
        "text": entry.text,
        "summary": entry.summary,
    }


async def abandon_session(session_id: UUID, principal: Principal) -> None:
    """§3.1.2 异常处理 —— 老人中途退出；PR 4 客户端触发。"""
    session = await session_repo.get_by_id(session_id)
    if session is None:
        return
    _verify_elder(principal, session.elder_id)
    if session.status is not SessionStatus.ACTIVE:
        return
    final = await transition_status(session, TransitionEvent.ABANDON_REQUESTED)
    emit_event(
        SESSION_ABANDONED,
        session_id=str(final.id),
        elder_id=str(final.elder_id),
    )
