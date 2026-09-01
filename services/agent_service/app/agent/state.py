"""Agent 访谈状态机（AGENTS.md §11 红线 —— session.status 只走 transition()）。

事件：
- FINALIZE_REQUESTED：handle_turn 检测到收尾 / finalize API 被调
- ABANDON_REQUESTED：老人中途退出（PR 4 客户端触发，本 PR 仅 reserve 接口）
"""

from __future__ import annotations

from elder_common.constants import ErrorCode
from elder_common.errors import AppError
from elder_common.repos import diary_session as session_repo
from elder_common.schemas.diary_session import Session, SessionStatus


class TransitionEvent:
    FINALIZE_REQUESTED = "finalize"
    ABANDON_REQUESTED = "abandon"


_ALLOWED: dict[SessionStatus, set[str]] = {
    SessionStatus.ACTIVE: {TransitionEvent.FINALIZE_REQUESTED, TransitionEvent.ABANDON_REQUESTED},
    SessionStatus.FINALIZED: set(),
    SessionStatus.ABANDONED: set(),
}

_TARGET: dict[tuple[SessionStatus, str], SessionStatus] = {
    (SessionStatus.ACTIVE, TransitionEvent.FINALIZE_REQUESTED): SessionStatus.FINALIZED,
    (SessionStatus.ACTIVE, TransitionEvent.ABANDON_REQUESTED): SessionStatus.ABANDONED,
}


async def transition(session: Session, event: str) -> Session:
    """§11：session.status 唯一变更入口。非法转移抛 AppError。"""
    if event not in _ALLOWED[session.status]:
        raise AppError(
            code=ErrorCode.CONFLICT,
            message=f"invalid state transition: {session.status.value} -> {event}",
            http_status=409,
        )
    new_status = _TARGET[(session.status, event)]
    await session_repo.mark_status(session.id, new_status)
    return Session(
        id=session.id,
        elder_id=session.elder_id,
        status=new_status,
        turns=session.turns,
        created_at=session.created_at,
        updated_at=session.updated_at,
    )
