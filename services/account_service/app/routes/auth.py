# mypy: disable-error-code="explicit-any"
# 原因：Pydantic BaseModel 父类签名含 Any，与 §5 禁 Any 冲突
"""鉴权路由 —— /v1/auth/*（PRD §6.1）。"""

from __future__ import annotations

from contextlib import suppress

from elder_common.auth import issue_token
from elder_common.constants import ErrorCode
from elder_common.errors import AppError
from elder_common.events import AUTH_LOGIN, AUTH_SMS_SENT
from elder_common.logging import emit_event
from elder_common.repos import family_user
from elder_common.upstream import sms as sms_upstream
from fastapi import APIRouter
from pydantic import BaseModel, ConfigDict, Field
from services.account_service.app.deps import get_sms_store

router = APIRouter(prefix="/v1/auth", tags=["auth"])


class SmsCodeRequest(BaseModel):
    """POST /v1/auth/sms-code 入参（§6.1）。"""

    model_config = ConfigDict(extra="forbid")

    phone: str = Field(min_length=11, max_length=11, pattern=r"^1[3-9]\d{9}$")


class LoginRequest(BaseModel):
    """POST /v1/auth/login 入参（§6.1）。"""

    model_config = ConfigDict(extra="forbid")

    phone: str = Field(min_length=11, max_length=11, pattern=r"^1[3-9]\d{9}$")
    code: str = Field(min_length=6, max_length=6, pattern=r"^\d{6}$")


@router.post("/sms-code", status_code=204)
async def send_sms_code(body: SmsCodeRequest) -> None:
    """发送验证码（§6.1：60s 1 次 / 手机号）。"""
    store = get_sms_store()
    code = store.request_code(body.phone)
    if code is None:
        raise AppError(
            code=ErrorCode.RATE_LIMITED,
            message="sms-code rate limited (60s cooldown)",
            http_status=429,
            retry_after_seconds=60,
        )
    # §A.1：上游不可达（mock / dev / 测试）时容错 —— code 已在 store，登录不受影响
    with suppress(AppError):
        sms_upstream.send_code(body.phone)
    emit_event(AUTH_SMS_SENT)


@router.post("/login")
async def login(body: LoginRequest) -> dict[str, object]:
    """验证码登录 —— 成功返回 JWT（§6.1 / §7.4）。"""
    store = get_sms_store()
    if not store.verify(body.phone, body.code):
        raise AppError(
            code=ErrorCode.UNAUTHORIZED,
            message="invalid sms code or locked",
            http_status=401,
        )

    # §3.2.7 / §11.20：未注册即自动新建 family_user
    user = await family_user.find_by_phone(body.phone)
    if user is None:
        from elder_common.schemas.family_user import FamilyUserCreate

        user = await family_user.create(FamilyUserCreate(phone=body.phone))

    token = issue_token(str(user.id), "family")
    emit_event(AUTH_LOGIN, user_id=str(user.id))
    return {
        "token": token,
        "user_id": str(user.id),
        "role": "family",
    }
