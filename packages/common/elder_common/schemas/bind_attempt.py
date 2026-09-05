# mypy: disable-error-code="explicit-any"
# 原因：Pydantic BaseModel 父类签名含 Any，与 §5 禁 Any 冲突（BaseSettings 同款）

"""bind_attempt —— 绑定尝试（PRD §5.9，v2.1.1 新增）。"""

from __future__ import annotations

from datetime import datetime
from enum import Enum
from uuid import UUID, uuid4

from pydantic import BaseModel, ConfigDict, Field


class BindAttemptStatus(str, Enum):
    """§5.9 状态。"""

    PENDING = "pending"
    CONFIRMED = "confirmed"
    REJECTED = "rejected"
    EXPIRED = "expired"


class BindAttemptCreate(BaseModel):
    """创建入参 —— 服务端补 id + 时间戳 + 状态。"""

    model_config = ConfigDict(extra="forbid")

    bind_code: str = Field(min_length=6, max_length=64)
    family_user_id: UUID
    elder_device_token: str = Field(min_length=1, max_length=128)


class BindAttempt(BaseModel):
    """持久化形态。"""

    model_config = ConfigDict(extra="forbid")

    id: UUID = Field(default_factory=uuid4)
    bind_code: str
    family_user_id: UUID
    elder_device_token: str
    status: BindAttemptStatus = BindAttemptStatus.PENDING
    elder_id: UUID | None = None
    binding_id: UUID | None = None
    created_at: datetime
    responded_at: datetime | None = None
