# 腾讯 SMS 验证码封装（§A.1）。

# MVP-DEFER（v2.1.2）：MVP 阶段 SMS 端点下线，模块代码保留供 v2.x 接回。
# 删除 /v1/auth/sms-code 与 /v1/auth/login 路由（见 services/account_service/app/routes/auth.py）。
# v2.x 接 SMS 时仅需取消路由注释 + 启用 AUTH_SMS_SENT / AUTH_LOGIN 事件即可。

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
