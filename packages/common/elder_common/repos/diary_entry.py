"""diary_entry 仓储（§5.7 / §3.1.6 / §A.6）。"""

from __future__ import annotations

from uuid import UUID

from elder_common.schemas.diary_entry import DiaryEntry, DiaryEntryCreate
from elder_common.storage.base import CollectionName, get_storage
from elder_common.time import now_utc


async def create(payload: DiaryEntryCreate) -> DiaryEntry:
    """§A.6：flush-pending 写入或 finalize 直接落库；服务端补 id + created_at。

    pending_id 幂等由 caller 保证（flush-pending 先查 find_by_pending_id）。
    """
    storage = get_storage()
    now = now_utc()
    doc: dict[str, object] = {
        "elder_id": str(payload.elder_id),
        "session_id": str(payload.session_id) if payload.session_id else None,
        "date": payload.date,
        "text": payload.text,
        "summary": payload.summary,
        "audio_segments": [s.model_dump(mode="json") for s in payload.audio_segments],
        "pending_id": str(payload.pending_id) if payload.pending_id else None,
        "created_at": now.isoformat(),
    }
    eid = await storage.insert(CollectionName.DIARY_ENTRIES, doc)
    return DiaryEntry(
        id=eid,  # type: ignore[arg-type]
        elder_id=payload.elder_id,
        session_id=payload.session_id,
        date=payload.date,
        text=payload.text,
        summary=payload.summary,
        audio_segments=payload.audio_segments,
        created_at=now,
    )


async def get_by_id(entry_id: UUID) -> DiaryEntry | None:
    storage = get_storage()
    doc = await storage.find_one(CollectionName.DIARY_ENTRIES, {"id": str(entry_id)})
    if doc is None:
        return None
    return DiaryEntry.model_validate(doc)


async def find_by_pending_id(pending_id: UUID) -> DiaryEntry | None:
    """§A.6：flush-pending 幂等去重。"""
    storage = get_storage()
    doc = await storage.find_one(CollectionName.DIARY_ENTRIES, {"pending_id": str(pending_id)})
    if doc is None:
        return None
    return DiaryEntry.model_validate(doc)


async def list_by_elder_range(
    elder_id: UUID, *, date_from: str | None = None, date_to: str | None = None
) -> list[DiaryEntry]:
    """§3.2.5 / §3.1.7：按 elder + 日期范围列日志。"""
    storage = get_storage()
    query: dict[str, object] = {"elder_id": str(elder_id)}
    if date_from or date_to:
        date_clause: dict[str, object] = {}
        if date_from:
            date_clause["$gte"] = date_from
        if date_to:
            date_clause["$lte"] = date_to
        query["date"] = date_clause
    docs = await storage.find_many(
        CollectionName.DIARY_ENTRIES, query, sort=[("date", -1), ("created_at", -1)]
    )
    return [DiaryEntry.model_validate(d) for d in docs]
