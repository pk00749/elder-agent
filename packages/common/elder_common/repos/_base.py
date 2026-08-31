"""仓储基类 —— UUID / datetime 序列化 + 时间戳管理（§10）。"""

from __future__ import annotations

from datetime import datetime
from uuid import UUID

from pydantic import BaseModel


def to_storage_value(value: object) -> object:
    """UUID → str；datetime → isoformat；其余原样。"""
    if isinstance(value, UUID):
        return str(value)
    if isinstance(value, datetime):
        return value.isoformat()
    return value


def serialize(model: BaseModel) -> dict[str, object]:
    """pydantic model → 存进存储的 dict（UUID/时间序列化）。"""
    return {k: to_storage_value(v) for k, v in model.model_dump().items()}
