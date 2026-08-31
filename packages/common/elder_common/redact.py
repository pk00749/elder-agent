# PII 脱敏（§18 红线 —— 客户端禁止明文手机号）。
from __future__ import annotations


def phone_tail(phone: str | None) -> str:
    """返回 `136****8888` 格式（缺则返回空串）。"""
    if not phone or len(phone) < 7:
        return ""
    return f"{phone[:3]}****{phone[-4:]}"
