"""reminder 仓储（§5.4 + §A.4 + §10 / §18 越权校验放仓储层）。"""

from __future__ import annotations

from collections.abc import Sequence
from uuid import UUID

from elder_common.auth import Principal, assert_elder_match
from elder_common.constants import ErrorCode
from elder_common.errors import AppError
from elder_common.repos.binding import family_has_binding
from elder_common.schemas.reminder import (
    Reminder,
    ReminderCreate,
    ReminderPatch,
    ReminderStatus,
    ReminderType,
)
from elder_common.storage.base import CollectionName, get_storage
from elder_common.time import now_utc


async def _ensure_elder_access(principal: Principal, elder_id: UUID) -> None:
    """§10 / §18 红线：仓储层二次校验，越权直接 403。"""
    assert_elder_match(principal, str(elder_id))
    if principal.role == "family":
        ok = await family_has_binding(UUID(principal.user_id), elder_id)
        if not ok:
            raise AppError(
                code=ErrorCode.FORBIDDEN,
                message="family not bound to this elder",
                http_status=403,
            )


async def create(payload: ReminderCreate, principal: Principal) -> Reminder:
    """§3.2.3 / §3.2.4 / §A.4：创建提醒，channel_priority 由 type 自动设。"""
    if payload.channel_priority is None:
        msg = "channel_priority should be set by ReminderCreate validator"
        raise RuntimeError(msg)
    await _ensure_elder_access(principal, payload.elder_id)
    storage = get_storage()
    now = now_utc()
    doc: dict[str, object] = {
        "elder_id": str(payload.elder_id),
        "type": payload.type.value,
        "channel_priority": payload.channel_priority.value,
        "payload": payload.payload.model_dump(mode="json"),
        "status": payload.status.value,
        "created_by": str(payload.created_by),
        "created_at": now.isoformat(),
        "updated_at": now.isoformat(),
    }
    reminder_id = await storage.insert(CollectionName.REMINDERS, doc)
    return Reminder(
        id=reminder_id,  # type: ignore[arg-type]
        elder_id=payload.elder_id,
        type=payload.type,
        channel_priority=payload.channel_priority,
        payload=payload.payload,
        status=payload.status,
        created_by=payload.created_by,
        created_at=now,
        updated_at=now,
    )


async def get_by_id(reminder_id: UUID, principal: Principal) -> Reminder | None:
    storage = get_storage()
    doc = await storage.find_one(CollectionName.REMINDERS, {"id": str(reminder_id)})
    if doc is None:
        return None
    reminder = Reminder.model_validate(doc)
    await _ensure_elder_access(principal, reminder.elder_id)
    return reminder


async def list_for_elder(elder_id: UUID, principal: Principal) -> list[Reminder]:
    """§6.1 GET /v1/reminders —— 列出可访问的提醒。"""
    await _ensure_elder_access(principal, elder_id)
    storage = get_storage()
    docs = await storage.find_many(CollectionName.REMINDERS, {"elder_id": str(elder_id)})
    return [Reminder.model_validate(d) for d in docs]


async def list_for_family(
    family_id: UUID, *, types: Sequence[ReminderType] | None = None
) -> list[Reminder]:
    """家属列出自己创建的提醒（与 list_for_elder 不同路径 —— 走 created_by）。"""
    storage = get_storage()
    query: dict[str, object] = {"created_by": str(family_id)}
    if types:
        query["type"] = {"$in": [t.value for t in types]}
    docs = await storage.find_many(CollectionName.REMINDERS, query)
    return [Reminder.model_validate(d) for d in docs]


async def update(reminder_id: UUID, patch: ReminderPatch, principal: Principal) -> Reminder | None:
    """§6.1 PATCH /v1/reminders/:id —— 仅 family 可改。"""
    if principal.role != "family":
        raise AppError(
            code=ErrorCode.FORBIDDEN,
            message="only family can update reminder",
            http_status=403,
        )
    storage = get_storage()
    existing_doc = await storage.find_one(CollectionName.REMINDERS, {"id": str(reminder_id)})
    if existing_doc is None:
        return None
    existing = Reminder.model_validate(existing_doc)
    await _ensure_elder_access(principal, existing.elder_id)

    set_part: dict[str, object] = {"updated_at": now_utc().isoformat()}
    if patch.payload is not None:
        set_part["payload"] = patch.payload.model_dump(mode="json")
    if patch.status is not None:
        set_part["status"] = patch.status.value

    await storage.update_one(
        CollectionName.REMINDERS,
        {"id": str(reminder_id)},
        {"$set": set_part},
    )
    updated_doc = await storage.find_one(CollectionName.REMINDERS, {"id": str(reminder_id)})
    if updated_doc is None:
        return None
    return Reminder.model_validate(updated_doc)


async def delete(reminder_id: UUID, principal: Principal) -> bool:
    """§6.1 DELETE /v1/reminders/:id —— 仅 family primary 可删。"""
    if principal.role != "family":
        raise AppError(
            code=ErrorCode.FORBIDDEN,
            message="only family can delete reminder",
            http_status=403,
        )
    storage = get_storage()
    existing_doc = await storage.find_one(CollectionName.REMINDERS, {"id": str(reminder_id)})
    if existing_doc is None:
        return False
    existing = Reminder.model_validate(existing_doc)
    await _ensure_elder_access(principal, existing.elder_id)
    return await storage.delete_one(CollectionName.REMINDERS, {"id": str(reminder_id)})


async def list_active_by_elder(elder_id: UUID) -> list[Reminder]:
    """§6.1 GET /v1/reminders elder 视角 —— 仅 status=active。"""
    storage = get_storage()
    docs = await storage.find_many(
        CollectionName.REMINDERS,
        {
            "elder_id": str(elder_id),
            "status": ReminderStatus.ACTIVE.value,
        },
        sort=[("created_at", 1)],
    )
    return [Reminder.model_validate(d) for d in docs]


async def find_device_token(elder_id: UUID) -> str | None:
    """§11.18 推送调度：取 elder 的 device_token。"""
    from elder_common.repos.elder_profile import get_by_id

    elder = await get_by_id(str(elder_id))
    return elder.device_token if elder else None
