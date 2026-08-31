# mypy: disable-error-code="explicit-any"
# 原因：Pydantic BaseModel 父类签名含 Any，与 §5 禁 Any 冲突（BaseSettings 同款）

"""reminder_ack —— 提醒应答（PRD §5.5）。"""

from __future__ import annotations

from datetime import datetime
from enum import Enum
from uuid import UUID, uuid4

from pydantic import BaseModel, ConfigDict, Field


class AckAction(str, Enum):
    """§5.5：taken / ack / missed。"""

    TAKEN = "taken"
    ACK = "ack"
    MISSED = "missed"


class ReminderAckCreate(BaseModel):
    """POST /v1/reminders/:id/ack 入参。"""

    model_config = ConfigDict(extra="forbid")

    action: AckAction
    at: datetime | None = None  # 不传则服务端补 now_utc()
    offline_buffered: bool = False  # §5.5


class ReminderAck(BaseModel):
    """持久化形态。"""

    model_config = ConfigDict(extra="forbid")

    id: UUID = Field(default_factory=uuid4)
    reminder_id: UUID
    elder_id: UUID
    action: AckAction
    at: datetime
    offline_buffered: bool = False
