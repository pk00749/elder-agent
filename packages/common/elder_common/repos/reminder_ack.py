"""reminder_ack 仓储（§5.5 + §11.21 服务端时间优先）。"""

from __future__ import annotations

from uuid import UUID

from elder_common.auth import Principal
from elder_common.constants import ErrorCode
from elder_common.errors import AppError
from elder_common.repos.reminder import _ensure_elder_access
from elder_common.schemas.reminder_ack import (
    ReminderAck,
    ReminderAckCreate,
)
from elder_common.storage.base import CollectionName, get_storage
from elder_common.time import now_utc


async def create(
    reminder_id: UUID,
    payload: ReminderAckCreate,
    principal: Principal,
) -> ReminderAck:
    """§6.1 POST /v1/reminders/:id/ack —— 仅 elder 角色可应答。"""
    if principal.role != "elder":
        raise AppError(
            code=ErrorCode.FORBIDDEN,
            message="only elder can ack reminder",
            http_status=403,
        )
    await _ensure_elder_access(principal, UUID(principal.elder_id or ""))
    storage = get_storage()

    at = payload.at or now_utc()

    # §11.21：服务端时间优先 —— 已经记过同 reminder_id + elder_id + action 的最早一条保留
    existing = await storage.find_one(
        CollectionName.REMINDER_ACKS,
        {
            "reminder_id": str(reminder_id),
            "elder_id": str(principal.elder_id),
            "action": payload.action.value,
        },
    )
    if existing is not None:
        return ReminderAck.model_validate(existing)

    doc: dict[str, object] = {
        "reminder_id": str(reminder_id),
        "elder_id": str(principal.elder_id),
        "action": payload.action.value,
        "at": at.isoformat(),
        "offline_buffered": payload.offline_buffered,
    }
    ack_id = await storage.insert(CollectionName.REMINDER_ACKS, doc)
    return ReminderAck(
        id=ack_id,  # type: ignore[arg-type]
        reminder_id=reminder_id,
        elder_id=UUID(principal.elder_id or ""),
        action=payload.action,
        at=at,
        offline_buffered=payload.offline_buffered,
    )


async def list_by_reminder(reminder_id: UUID, principal: Principal) -> list[ReminderAck]:
    storage = get_storage()
    docs = await storage.find_many(
        CollectionName.REMINDER_ACKS,
        {"reminder_id": str(reminder_id)},
        sort=[("at", -1)],
    )
    return [ReminderAck.model_validate(d) for d in docs]
