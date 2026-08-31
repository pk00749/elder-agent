# 腾讯 SMS 验证码封装（§A.1）。
from __future__ import annotations

import random

import httpx

from elder_common.config import get_settings
from elder_common.constants import ErrorCode
from elder_common.errors import AppError
from elder_common.events import SMS_SENT, UPSTREAM_ERROR
from elder_common.logging import emit_event


def send_code(phone: str) -> str:
    """发送短信验证码，返回 6 位 code。

    dev / mock：直接生成 + 调 mock URL；prod：腾讯 SMS 模板。
    """
    settings = get_settings()
    code = f"{random.randint(0, 999999):06d}"
    try:
        resp = httpx.post(
            f"{settings.sms_base_url}/send",
            json={
                "phone": phone,
                "code": code,
                "app_id": settings.sms_app_id,
                "template_id": settings.sms_template_id,
                "sign_name": settings.sms_sign_name,
            },
            timeout=httpx.Timeout(10.0),
        )
        resp.raise_for_status()
    except httpx.HTTPError as exc:
        emit_event(UPSTREAM_ERROR, upstream="sms", error=str(exc))
        raise AppError(
            code=ErrorCode.INTERNAL_ERROR,
            message="SMS send failed",
            http_status=503,
        ) from exc
    emit_event(SMS_SENT)  # 不记 phone（§7 sanitize）
    return code
