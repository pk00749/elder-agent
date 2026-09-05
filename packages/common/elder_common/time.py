# 时区工具（§A.4 appointment_datetime 校验用）。
from __future__ import annotations

from datetime import UTC, datetime


def now_utc() -> datetime:
    """返回当前 UTC 时间（aware）。"""
    return datetime.now(tz=UTC)
