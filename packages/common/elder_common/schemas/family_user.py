# mypy: disable-error-code="explicit-any"
# 原因：Pydantic BaseModel 父类签名含 Any，与 §5 禁 Any 冲突（BaseSettings 同款）

"""family_user —— 家属账号（PRD §5.1）。"""

from __future__ import annotations

from datetime import datetime
from uuid import UUID, uuid4

from pydantic import BaseModel, ConfigDict, Field


class FamilyUserBase(BaseModel):
    """共享字段（§5.1）。"""

    model_config = ConfigDict(extra="forbid")

    phone: str = Field(min_length=11, max_length=11, pattern=r"^1[3-9]\d{9}$")


class FamilyUserCreate(FamilyUserBase):
    """创建入参 —— 服务端补 id + 时间戳。"""


class FamilyUser(FamilyUserBase):
    """持久化形态。"""

    id: UUID = Field(default_factory=uuid4)
    created_at: datetime
    updated_at: datetime
