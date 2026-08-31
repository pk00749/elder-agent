# mypy: disable-error-code="explicit-any"
# 原因：Pydantic BaseModel 父类签名含 Any，与 §5 禁 Any 冲突
"""提醒路由 —— /v1/reminders*（PRD §3.2.3 / §3.2.4 / §6.1 / §A.4）。"""

from __future__ import annotations

from uuid import UUID

from elder_common.auth import Principal, rate_limit, require_role
from elder_common.constants import ErrorCode
from elder_common.errors import AppError
from elder_common.events import REMINDER_CREATED, REMINDER_DELETED, REMINDER_UPDATED
from elder_common.logging import emit_event
from elder_common.repos import reminder as reminder_repo
from elder_common.repos import reminder_ack as ack_repo
from elder_common.schemas.reminder import (
    ChannelPriority,
    ReminderCreate,
    ReminderPatch,
    ReminderPayload,
    ReminderStatus,
    ReminderType,
)
from elder_common.schemas.reminder_ack import (
    ReminderAckCreate,
)
from fastapi import APIRouter, Depends
from pydantic import BaseModel, ConfigDict, model_validator
from services.reminder_service.app.scheduling.push import enqueue_pushes

router = APIRouter(prefix="/v1/reminders", tags=["reminders"])


# ---- 入参 ----
class ReminderCreateRequest(BaseModel):
    """POST /v1/reminders 入参 —— created_by 由服务端按 JWT 填（§A.4 §10）。"""

    model_config = ConfigDict(extra="forbid")

    elder_id: UUID
    type: ReminderType
    payload: ReminderPayload
    status: ReminderStatus = ReminderStatus.ACTIVE

    @model_validator(mode="after")
    def auto_channel_priority(self) -> ReminderCreateRequest:
        # §A.4：服务端按 type 自动设 channel_priority
        if self.type is ReminderType.MEDICATION:
            self.__dict__["_channel_priority"] = ChannelPriority.MID
        else:
            self.__dict__["_channel_priority"] = ChannelPriority.HIGH
        return self


class ReminderPatchRequest(BaseModel):
    """PATCH /v1/reminders/{id} 入参 —— 与内部 ReminderPatch 同形。"""

    model_config = ConfigDict(extra="forbid")

    payload: ReminderPayload | None = None
    status: ReminderStatus | None = None


class AckRequest(BaseModel):
    """POST /v1/reminders/{id}/ack 入参（§5.5）。"""

    model_config = ConfigDict(extra="forbid")

    action: str  # AckAction value
    at: str | None = None  # ISO datetime
    offline_buffered: bool = False


# ---- 路由 ----
@router.get("")
async def list_reminders(
    principal: Principal = Depends(require_role("family", "elder")),
) -> dict[str, object]:
    """§6.1 GET /v1/reminders —— 列出可访问的提醒。

    - family：返回 created_by = 自己 的全部提醒
    - elder：返回 elder_id = 自己 的全部提醒
    """
    if principal.role == "family":
        items = await reminder_repo.list_for_family(UUID(principal.user_id))
    else:
        items = await reminder_repo.list_for_elder(UUID(principal.elder_id or ""), principal)
    return {"reminders": [r.model_dump(mode="json") for r in items]}


@router.post(
    "",
    dependencies=[Depends(rate_limit("reminder.create", 30, 3600))],
)
async def create_reminder(
    body: ReminderCreateRequest,
    principal: Principal = Depends(require_role("family")),
) -> dict[str, object]:
    """§6.1 POST /v1/reminders —— family 创建提醒；推送通过 TPush 调度（§11.18）。"""
    cp = ChannelPriority.MID if body.type is ReminderType.MEDICATION else ChannelPriority.HIGH
    create = ReminderCreate(
        elder_id=body.elder_id,
        type=body.type,
        payload=body.payload,
        status=body.status,
        created_by=UUID(principal.user_id),
        channel_priority=cp,
    )
    reminder = await reminder_repo.create(create, principal)
    # §11.18：异步调度 TPush 推送
    pushed = await enqueue_pushes(reminder)
    emit_event(
        REMINDER_CREATED,
        reminder_id=str(reminder.id),
        elder_id=str(reminder.elder_id),
        channel=reminder.channel_priority.value,
        pushed=pushed,
    )
    return reminder.model_dump(mode="json")


@router.get("/{reminder_id}")
async def get_reminder(
    reminder_id: str,
    principal: Principal = Depends(require_role("family", "elder")),
) -> dict[str, object]:
    """§6.1 GET /v1/reminders/{id} —— 单条详情。"""
    reminder = await reminder_repo.get_by_id(UUID(reminder_id), principal)
    if reminder is None:
        raise AppError(
            code=ErrorCode.NOT_FOUND,
            message="reminder not found",
            http_status=404,
        )
    return reminder.model_dump(mode="json")


@router.patch(
    "/{reminder_id}",
    dependencies=[Depends(rate_limit("reminder.update", 30, 3600))],
)
async def update_reminder(
    reminder_id: str,
    body: ReminderPatchRequest,
    principal: Principal = Depends(require_role("family")),
) -> dict[str, object]:
    """§6.1 PATCH /v1/reminders/{id} —— family 编辑；重新调度 TPush。"""
    patch = ReminderPatch(payload=body.payload, status=body.status)
    updated = await reminder_repo.update(UUID(reminder_id), patch, principal)
    if updated is None:
        raise AppError(
            code=ErrorCode.NOT_FOUND,
            message="reminder not found",
            http_status=404,
        )
    await enqueue_pushes(updated)  # §11.18：重新调度
    emit_event(
        REMINDER_UPDATED,
        reminder_id=str(updated.id),
        elder_id=str(updated.elder_id),
    )
    return updated.model_dump(mode="json")


@router.delete(
    "/{reminder_id}",
    status_code=204,
    dependencies=[Depends(rate_limit("reminder.delete", 30, 3600))],
)
async def delete_reminder(
    reminder_id: str,
    principal: Principal = Depends(require_role("family")),
) -> None:
    """§6.1 DELETE /v1/reminders/{id} —— family 删除（推送取消 v2 扩展）。"""
    ok = await reminder_repo.delete(UUID(reminder_id), principal)
    if not ok:
        raise AppError(
            code=ErrorCode.NOT_FOUND,
            message="reminder not found",
            http_status=404,
        )
    emit_event(REMINDER_DELETED, reminder_id=reminder_id)


@router.post("/{reminder_id}/ack")
async def ack_reminder(
    reminder_id: str,
    body: AckRequest,
    principal: Principal = Depends(require_role("elder")),
) -> dict[str, object]:
    """§6.1 POST /v1/reminders/{id}/ack —— elder 应答。"""
    from datetime import datetime

    at = datetime.fromisoformat(body.at) if body.at else None
    from elder_common.schemas.reminder_ack import AckAction

    payload = ReminderAckCreate(
        action=AckAction(body.action),
        at=at,
        offline_buffered=body.offline_buffered,
    )
    ack = await ack_repo.create(UUID(reminder_id), payload, principal)
    return ack.model_dump(mode="json")
