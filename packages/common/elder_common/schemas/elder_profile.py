# mypy: disable-error-code="explicit-any"
# 原因：Pydantic BaseModel 父类签名含 Any，与 §5 禁 Any 冲突（BaseSettings 同款）

"""elder_profile —— 老人档案（PRD §5.2）。"""

from __future__ import annotations

from datetime import datetime
from uuid import UUID, uuid4

from pydantic import BaseModel, ConfigDict, Field


class ElderProfileBase(BaseModel):
    """共享字段（§5.2）。"""

    model_config = ConfigDict(extra="forbid")

    name: str = Field(min_length=1, max_length=20)
    device_token: str | None = Field(default=None, min_length=1, max_length=128)
    timezone: str = Field(default="Asia/Shanghai", min_length=1, max_length=64)


class ElderProfileCreate(ElderProfileBase):
    """创建入参 —— 服务端补 id + 时间戳。"""


class ElderProfile(ElderProfileBase):
    """持久化形态。"""

    id: UUID = Field(default_factory=uuid4)
    created_at: datetime
    updated_at: datetime
