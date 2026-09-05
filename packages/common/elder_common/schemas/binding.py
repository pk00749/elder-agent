# mypy: disable-error-code="explicit-any"
# 原因：Pydantic BaseModel 父类签名含 Any，与 §5 禁 Any 冲突（BaseSettings 同款）

"""binding —— 家属-老人绑定关系（PRD §5.3 / §3.2.8 §H.25）。"""

from __future__ import annotations

from datetime import datetime
from enum import Enum
from uuid import UUID, uuid4

from pydantic import BaseModel, ConfigDict, Field


class BindingRole(str, Enum):
    """primary 拥有全部配置权 + 解绑权；secondary 只读 + 配置权（§5.3 / §11.6）。"""

    PRIMARY = "primary"
    SECONDARY = "secondary"


class BindingCreate(BaseModel):
    """创建入参。"""

    model_config = ConfigDict(extra="forbid")

    family_id: UUID
    elder_id: UUID
    role: BindingRole


class Binding(BaseModel):
    """持久化形态。"""

    model_config = ConfigDict(extra="forbid")

    id: UUID = Field(default_factory=uuid4)
    family_id: UUID
    elder_id: UUID
    role: BindingRole
    created_at: datetime
