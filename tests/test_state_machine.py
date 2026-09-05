"""Agent 状态机测试（AGENTS.md §11 红线 —— session.status 只走 transition()）。"""
from __future__ import annotations

from uuid import UUID, uuid4

import pytest

from elder_common.auth import Principal
from elder_common.errors import AppError
from elder_common.repos import diary_session as session_repo
from elder_common.schemas.diary_session import SessionCreate, SessionStatus

from services.agent_service.app.agent.services.diary import (
    finalize_session,
    handle_turn,
)
from services.agent_service.app.agent.state import (
    TransitionEvent,
    transition,
)


async def _bootstrap_session(elder_id: UUID | None = None) -> tuple[Principal, UUID]:
    """建 session + 返 (elder principal, session_id)。"""
    eid = elder_id or uuid4()
    session = await session_repo.create(SessionCreate(elder_id=eid))
    principal = Principal(user_id=str(eid), role="elder", elder_id=str(eid))
    return principal, session.id


async def test_state_transition_active_to_finalized() -> None:
    """§3.1.4.C：active → finalized（正常路径）。"""
    _principal, sid = await _bootstrap_session()
    session = await session_repo.get_by_id(sid)
    assert session is not None
    assert session.status is SessionStatus.ACTIVE

    final = await transition(session, TransitionEvent.FINALIZE_REQUESTED)
    assert final.status is SessionStatus.FINALIZED


async def test_state_transition_active_to_abandoned() -> None:
    """§3.1.2 异常处理：active → abandoned（老人中途退出）。"""
    _principal, sid = await _bootstrap_session()
    session = await session_repo.get_by_id(sid)
    assert session is not None
    abandoned = await transition(session, TransitionEvent.ABANDON_REQUESTED)
    assert abandoned.status is SessionStatus.ABANDONED


async def test_state_transition_invalid_raises() -> None:
    """§11 红线：终态不可再转移（finalize → finalize 抛 CONFLICT）。"""
    _principal, sid = await _bootstrap_session()
    session = await session_repo.get_by_id(sid)
    assert session is not None
    final = await transition(session, TransitionEvent.FINALIZE_REQUESTED)
    with pytest.raises(AppError) as exc_info:
        await transition(final, TransitionEvent.FINALIZE_REQUESTED)
    assert exc_info.value.code == "CONFLICT"


async def test_handle_turn_emergency_triggers_finalize() -> None:
    """§B2：急救信号 → 绕过 LLM → should_finalize=True。"""
    principal, sid = await _bootstrap_session()
    resp = await handle_turn(
        sid, 1, "我摔了，腿疼", "elder/x/tmp/abc.m4a", principal
    )
    assert resp["should_finalize"] is True
    session = await session_repo.get_by_id(sid)
    assert session is not None
    assert len(session.turns) == 1


async def test_handle_turn_money_bypasses_llm() -> None:
    """§B1 / §B4：金钱 / 医疗问答 → canned 回复，不调 LLM。"""
    principal, sid = await _bootstrap_session()
    resp = await handle_turn(
        sid, 1, "我转点钱给你", "elder/x/tmp/abc.m4a", principal
    )
    assert resp["assistant_text"] == "嗯，咱们聊点别的吧。"
    assert resp["should_finalize"] is False
