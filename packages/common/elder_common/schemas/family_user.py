# mypy: disable-error-code="explicit-any"
# 原因：Pydantic BaseModel 父类签名含 Any，与 §5 禁 Any 冲突（BaseSettings 同款）

"""family_user —— 家属账号（PRD §5.1）。"""

from __future__ import annotations

from datetime import datetime
from uuid import UUID, uuid4

from pydantic import BaseModel, ConfigDict, Field, model_validator


class FamilyUserBase(BaseModel):
    """共享字段（§5.1；v2.1.2 新增 device_token 用于 anonymous-device 流程）。"""

    model_config = ConfigDict(extra="forbid")

    # v2.1.2：MVP 阶段 phone 与 device_token 二选一非空；v2.x 接 SMS 后收紧到 phone 必填。
    phone: str = Field(
        default="",
        min_length=0,
        max_length=11,
        pattern=r"^(?:1[3-9]\d{9})?$",
    )
    device_token: str | None = Field(
        default=None,
        min_length=8,
        max_length=128,
        pattern=r"^[A-Za-z0-9\-_]{8,128}$",
    )


class FamilyUserCreateMvp(FamilyUserBase):
    """MVP 创建入参（v2.1.2）—— phone 留空，device_token 必填。"""

    @model_validator(mode="after")
    def _ensure_device_token(self) -> FamilyUserCreateMvp:
        if not self.device_token:
            raise ValueError("device_token required for MVP family_user")
        return self


class FamilyUserCreate(FamilyUserCreateMvp):
    """v2.x 创建入参（标 MVP-DEFER）—— phone 必填 + device_token 可选。

    MVP 阶段未调用；保留供 v2.x 接 SMS 后复用。
    """

    @model_validator(mode="after")
    def _ensure_phone(self) -> FamilyUserCreate:
        if not self.phone:
            raise ValueError("phone required for v2.x family_user")
        return self


class FamilyUser(FamilyUserBase):
    """持久化形态。"""

    id: UUID = Field(default_factory=uuid4)
    created_at: datetime
    updated_at: datetime
