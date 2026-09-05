"""agent-service 访谈流端到端测试（PRD §3.1.2 / §6.3 / §A.4）。"""
from __future__ import annotations

from uuid import UUID, uuid4

from elder_common.auth import Principal, issue_token
from elder_common.repos import diary_entry as entry_repo
from elder_common.repos import diary_session as session_repo
from elder_common.schemas.diary_session import SessionCreate

from services.agent_service.app.agent.services.diary import (
    finalize_session,
    handle_turn,
)


def _principal() -> Principal:
    eid = uuid4()
    return Principal(user_id=str(eid), role="elder", elder_id=str(eid))


async def test_handle_turn_returns_reply() -> None:
    """§3.1.2 / §6.3：handle_turn 返 assistant_text + audio_url + should_finalize + turns_left。"""
    principal = _principal()
    eid = UUID(principal.elder_id or "")
    session = await session_repo.create(SessionCreate(elder_id=eid))

    resp = await handle_turn(
        session.id,
        1,
        "今天和老张下棋了",
        "elder/x/tmp/a.m4a",
        principal,
    )
    assert resp["turn_no"] == 1
    assert resp["assistant_text"]
    assert resp["assistant_audio_url"]
    assert resp["turns_left"] == 7


async def test_handle_turn_explicit_close_triggers_finalize() -> None:
    """§C3：「就到这」→ should_finalize=True。"""
    principal = _principal()
    eid = UUID(principal.elder_id or "")
    session = await session_repo.create(SessionCreate(elder_id=eid))

    resp = await handle_turn(session.id, 1, "就到这吧", "elder/x/tmp/a.m4a", principal)
    assert resp["should_finalize"] is True


async def test_handle_turn_output_truncated_to_25() -> None:
    """§A2：服务端兜底截断 reply ≤ 25 字。"""
    principal = _principal()
    eid = UUID(principal.elder_id or "")
    session = await session_repo.create(SessionCreate(elder_id=eid))
    resp = await handle_turn(
        session.id, 1, "今天天气如何", "elder/x/tmp/a.m4a", principal
    )
    assert len(resp["assistant_text"]) <= 25


async def test_finalize_writes_diary_entry() -> None:
    """§3.1.6 / §A.4 §D1/D2：finalize 落库 DiaryEntry（text ≤ 100, summary ≤ 60）。"""
    principal = _principal()
    eid = UUID(principal.elder_id or "")
    session = await session_repo.create(SessionCreate(elder_id=eid))

    await handle_turn(session.id, 1, "今天和老张下棋，赢了", "elder/x/tmp/a.m4a", principal)
    resp = await finalize_session(
        session.id, 2, "挺高兴的", "elder/x/tmp/b.m4a", principal
    )
    assert resp["diary_id"]
    entry = await entry_repo.get_by_id(UUID(resp["diary_id"]))
    assert entry is not None
    assert len(entry.text) <= 100
    assert len(entry.summary) <= 60
    assert len(entry.audio_segments) >= 1


async def test_asr_passthrough(agent_client: object) -> None:
    """§6.1 POST /v1/agent/asr —— passthrough 上游；mock 不可达返回 503（§7 容错由客户端处理）。"""
    eid = uuid4()
    token = issue_token(str(eid), "elder", elder_id=str(eid))
    r = await agent_client.post(  # type: ignore[attr-defined]
        "/v1/agent/asr",
        json={"audio_url": "https://mock.cos/x.m4a"},
        headers={"Authorization": f"Bearer {token}"},
    )
    # mock infra not running → 503；pr 4 客户端按 §3.1.2 容错
    assert r.status_code in (200, 503)
