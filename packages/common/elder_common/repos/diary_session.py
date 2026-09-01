"""diary_session 仓储（§5.6 / §3.1.2）。"""
from __future__ import annotations

from uuid import UUID

from elder_common.schemas.diary_session import (
    Session,
    SessionCreate,
    SessionStatus,
    Turn,
)
from elder_common.storage.base import CollectionName, get_storage
from elder_common.time import now_utc


async def create(payload: SessionCreate) -> Session:
    """§3.1.2 第 1 步：建访谈会话。"""
    storage = get_storage()
    now = now_utc()
    doc: dict[str, object] = {
        "elder_id": str(payload.elder_id),
        "status": SessionStatus.ACTIVE.value,
        "turns": [],
        "created_at": now.isoformat(),
        "updated_at": now.isoformat(),
    }
    sid = await storage.insert(CollectionName.DIARY_SESSIONS, doc)
    return Session(
        id=sid,  # type: ignore[arg-type]
        elder_id=payload.elder_id,
        status=SessionStatus.ACTIVE,
        turns=[],
        created_at=now,
        updated_at=now,
    )


async def get_by_id(session_id: UUID) -> Session | None:
    storage = get_storage()
    doc = await storage.find_one(
        CollectionName.DIARY_SESSIONS, {"id": str(session_id)}
    )
    if doc is None:
        return None
    return Session.model_validate(doc)


async def find_active_by_elder(elder_id: UUID) -> Session | None:
    """§6.1：每 elder 同时仅允许一个 active session（§3.1.2 §E.5）。"""
    storage = get_storage()
    docs = await storage.find_many(
        CollectionName.DIARY_SESSIONS,
        {
            "elder_id": str(elder_id),
            "status": SessionStatus.ACTIVE.value,
        },
        limit=1,
        sort=[("created_at", -1)],
    )
    if not docs:
        return None
    return Session.model_validate(docs[0])


async def append_turn(session_id: UUID, turn: Turn) -> Session | None:
    """§3.1.2：追加一轮 turn。服务端做 turn_no 校验（单调递增）。"""
    storage = get_storage()
    existing_doc = await storage.find_one(
        CollectionName.DIARY_SESSIONS, {"id": str(session_id)}
    )
    if existing_doc is None:
        return None
    existing = Session.model_validate(existing_doc)
    if existing.status is not SessionStatus.ACTIVE:
        return existing  # 终态不可再追加
    new_turns = [*existing.turns, turn]
    await storage.update_one(
        CollectionName.DIARY_SESSIONS,
        {"id": str(session_id)},
        {
            "$set": {
                "turns": [t.model_dump(mode="json") for t in new_turns],
                "updated_at": now_utc().isoformat(),
            }
        },
    )
    updated_doc = await storage.find_one(
        CollectionName.DIARY_SESSIONS, {"id": str(session_id)}
    )
    if updated_doc is None:
        return None
    return Session.model_validate(updated_doc)


async def mark_status(session_id: UUID, status: SessionStatus) -> bool:
    """§3.1.2 / AGENTS.md §11：状态变更只走 transition()；本函数被 transition() 调。"""
    storage = get_storage()
    return await storage.update_one(
        CollectionName.DIARY_SESSIONS,
        {"id": str(session_id)},
        {"$set": {"status": status.value, "updated_at": now_utc().isoformat()}},
    )
