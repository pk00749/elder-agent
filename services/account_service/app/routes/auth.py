# mypy: disable-error-code="explicit-any"
# 原因：Pydantic BaseModel 父类签名含 Any，与 §5 禁 Any 冲突
"""鉴权路由 —— /v1/auth/*（PRD §6.1，v2.1.2 仅保留 anonymous-device）。"""

from __future__ import annotations

from elder_common.auth import issue_token
from elder_common.events import AUTH_ANONYMOUS_INIT
from elder_common.logging import emit_event
from elder_common.repos import family_user
from fastapi import APIRouter
from pydantic import BaseModel, ConfigDict, Field

router = APIRouter(prefix="/v1/auth", tags=["auth"])


# ---- MVP v2.1.2 ----
class AnonymousDeviceRequest(BaseModel):
    """POST /v1/auth/anonymous-device 入参（§6.1 / §C）。

    client 首启调一次拿到 JWT；后续请求用 JWT，设备身份信息保存在 DataStore。
    """

    model_config = ConfigDict(extra="forbid")

    device_token: str = Field(
        min_length=8,
        max_length=128,
        # 允许 UUID v4 / 带连字符的设备 ID；限制 ASCII 可打印字符
        pattern=r"^[A-Za-z0-9\-_]{8,128}$",
    )


class AnonymousDeviceResponse(BaseModel):
    """POST /v1/auth/anonymous-device 出参 —— 同 §7.4 JWT 签发格式。"""

    model_config = ConfigDict(extra="forbid")

    token: str
    user_id: str
    role: str  # 固定 "family"


@router.post("/anonymous-device", response_model=AnonymousDeviceResponse)
async def anonymous_device(body: AnonymousDeviceRequest) -> AnonymousDeviceResponse:
    """§6.1 / §C：device_token 静默注册或登录，返回 JWT。

    幂等：同 device_token 重调复用已有 family_user.id，仅签新 JWT 返回。
    MVP 阶段不限流 —— 鉴权路径无上游依赖，滥用风险低。
    v2.x 接 SMS 后由网关层做 per-IP 限流（见 services/account_service/app/deps.py 占位）。
    """
    user = await family_user.find_by_device_token(body.device_token)
    if user is None:
        from elder_common.schemas.family_user import FamilyUserCreateMvp

        user = await family_user.create_mvp(
            FamilyUserCreateMvp(phone="", device_token=body.device_token)
        )

    token = issue_token(str(user.id), "family")
    emit_event(
        AUTH_ANONYMOUS_INIT,
        user_id=str(user.id),
        device_token_prefix=body.device_token[:8],
    )
    return AnonymousDeviceResponse(
        token=token,
        user_id=str(user.id),
        role="family",
    )


# ---- MVP-DEFER：v2.x 接 SMS 时取消注释 ----
# @router.post("/sms-code", status_code=204)
# async def send_sms_code(body: SmsCodeRequest) -> None:
#     """发送验证码（§6.1：60s 1 次 / 手机号）。MVP-DEFER：v2.x 启用。"""
#     ...
#
# @router.post("/login")
# async def login(body: LoginRequest) -> dict[str, object]:
#     """验证码登录 —— 成功返回 JWT（§6.1 / §7.4）。MVP-DEFER：v2.x 启用。"""
#     ...
