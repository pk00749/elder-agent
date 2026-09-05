# mypy: disable-error-code="explicit-any"
# 原因：Pydantic BaseModel 父类签名含 Any，与 §5 禁 Any 冲突
"""绑定路由 —— /v1/bind/*（PRD §3.2.7-§3.2.8 / §A.5 / §11.25）。

elder 预绑定阶段无 JWT（§11.20 不支持重装恢复），/v1/bind/pending 和
/v1/bind/confirm 用 X-Elder-Device-Token header 识别老人端（PRD §6.1 标
auth=elder 实际指已绑定后的会话；PR 2 按真实流走 device_token header，PRD
差异在 PR 描述里说明）。
"""

from __future__ import annotations

from uuid import UUID

from elder_common.auth import Principal, issue_token, rate_limit, require_role
from elder_common.constants import ErrorCode
from elder_common.errors import AppError
from elder_common.events import BIND_ATTEMPT_CREATED, BIND_CONFIRMED, BIND_REJECTED
from elder_common.logging import emit_event
from elder_common.redact import phone_tail
from elder_common.repos import bind_attempt, binding, elder_profile, family_user
from elder_common.repos.bind_attempt import (
    BIND_CODE_TTL_SECONDS,
    assert_exists,
    check_not_expired,
    fresh_bind_code,
)
from elder_common.schemas.bind_attempt import BindAttemptCreate, BindAttemptStatus
from elder_common.schemas.binding import BindingCreate, BindingRole
from elder_common.schemas.elder_profile import ElderProfileCreate
from elder_common.storage.base import CollectionName, get_storage
from fastapi import APIRouter, Depends, Header
from pydantic import BaseModel, ConfigDict, Field

router = APIRouter(prefix="/v1/bind", tags=["bind"])


# ---- 入参 / 响应 ----
class BindCodeRequest(BaseModel):
    """POST /v1/bind/code（elder 端 settings）。"""

    model_config = ConfigDict(extra="forbid")

    elder_device_token: str = Field(min_length=1, max_length=128)


class BindCodeResponse(BaseModel):
    """返回给老人端用于显示二维码（§3.2.7）。"""

    model_config = ConfigDict(extra="forbid")

    bind_code: str
    expires_in: int  # 秒，固定 5 分钟（§3.2.7 / §F.3 决议 22）


class FamilyBindRequest(BaseModel):
    """POST /v1/bind/elder（family 端扫码后）。"""

    model_config = ConfigDict(extra="forbid")

    bind_code: str = Field(min_length=6, max_length=64)


class FamilyBindResponse(BaseModel):
    """§6.3：返回新生成的 bind_attempt_id + bind_code 给 family 端。"""

    model_config = ConfigDict(extra="forbid")

    bind_attempt_id: str
    bind_code: str
    elder_device_token: str
    created_at: str


class PendingItem(BaseModel):
    """§6.3 bind/pending 单条。"""

    model_config = ConfigDict(extra="forbid")

    bind_attempt_id: str
    family_user_id: str
    family_user_name: str
    family_user_phone_tail: str
    bind_code: str
    created_at: str


class PendingResponse(BaseModel):
    """§6.3 bind/pending 整体。"""

    model_config = ConfigDict(extra="forbid")

    pending: list[PendingItem]


class ConfirmRequest(BaseModel):
    """POST /v1/bind/confirm（§6.3 / §3.2.8 §F.4 决议 25-28）。"""

    model_config = ConfigDict(extra="forbid")

    bind_code: str = Field(min_length=6, max_length=64)
    elder_name: str = Field(min_length=1, max_length=20)
    decision: str  # "accept" | "reject"


class ConfirmAcceptResponse(BaseModel):
    """§6.3 accept 响应。"""

    model_config = ConfigDict(extra="forbid")

    elder_id: str
    binding_id: str
    role: str
    token: str  # elder JWT with elder_id


# ---- 路由 ----
@router.post("/code", response_model=BindCodeResponse)
async def create_bind_code(body: BindCodeRequest) -> BindCodeResponse:
    """§3.2.7 第 1 步：elder 端签发 bind_code（5 分钟有效）。"""
    code = fresh_bind_code()
    # family_user_id 占位（§5.9 必填）；用零 UUID 表示未指定
    placeholder = UUID("00000000-0000-0000-0000-000000000000")
    attempt = await bind_attempt.create(
        BindAttemptCreate(
            bind_code=code,
            family_user_id=placeholder,
            elder_device_token=body.elder_device_token,
        )
    )
    emit_event(BIND_ATTEMPT_CREATED, bind_attempt_id=str(attempt.id))
    return BindCodeResponse(bind_code=code, expires_in=BIND_CODE_TTL_SECONDS)


@router.post(
    "/elder",
    response_model=FamilyBindResponse,
    dependencies=[Depends(require_role("family")), Depends(rate_limit("bind.elder", 10, 3600))],
)
async def family_bind_attempt(
    body: FamilyBindRequest,
    principal: Principal = Depends(require_role("family")),
) -> FamilyBindResponse:
    """§3.2.7 第 2 步：family 扫码后向服务端登记此次邀请。

    模型说明（与 PRD 差异在 PR 描述里说明）：
    - QR 内容 = (elder_device_token + elder 的 bind_code X)
    - family 调用本端点带 X；服务端按 X 找 elder 的 session bind_attempt
    - 然后新建一条 bind_attempt（bind_code=Y，family_user_id=本家属，device_token=A）
    - elder 端 /v1/bind/pending 拉所有 family_user_id 已被填的 bind_attempts
    """
    elder_attempt = assert_exists(await bind_attempt.find_by_bind_code(body.bind_code))
    check_not_expired(elder_attempt)
    if elder_attempt.family_user_id != UUID("00000000-0000-0000-0000-000000000000"):
        raise AppError(
            code=ErrorCode.CONFLICT,
            message="bind_code already claimed",
            http_status=409,
        )
    if elder_attempt.status is not BindAttemptStatus.PENDING:
        raise AppError(
            code=ErrorCode.CONFLICT,
            message="elder bind session not pending",
            http_status=409,
        )

    # 新建属于本 family 的 bind_attempt（新 bind_code，避免与 elder 的 session 冲突）
    new_code = fresh_bind_code()
    attempt = await bind_attempt.create(
        BindAttemptCreate(
            bind_code=new_code,
            family_user_id=UUID(principal.user_id),
            elder_device_token=elder_attempt.elder_device_token,
        )
    )
    emit_event(BIND_ATTEMPT_CREATED, bind_attempt_id=str(attempt.id))
    return FamilyBindResponse(
        bind_attempt_id=str(attempt.id),
        bind_code=new_code,
        elder_device_token=attempt.elder_device_token,
        created_at=attempt.created_at.isoformat(),
    )


@router.get("/pending", response_model=PendingResponse)
async def list_pending(
    x_elder_device_token: str | None = Header(default=None, alias="X-Elder-Device-Token"),
) -> PendingResponse:
    """§3.2.7 / §6.3：老人端 10s/次轮询。"""
    if not x_elder_device_token:
        raise AppError(
            code=ErrorCode.UNAUTHORIZED,
            message="missing X-Elder-Device-Token",
            http_status=401,
        )
    attempts = await bind_attempt.list_pending_by_device(x_elder_device_token)
    items: list[PendingItem] = []
    for a in attempts:
        fam = await family_user.find_by_id(str(a.family_user_id))
        items.append(
            PendingItem(
                bind_attempt_id=str(a.id),
                family_user_id=str(a.family_user_id),
                family_user_name=fam.phone if fam else "",  # §3.2.8：缺 name 用 phone tail
                family_user_phone_tail=phone_tail(fam.phone) if fam else "",
                bind_code=a.bind_code,
                created_at=a.created_at.isoformat(),
            )
        )
    return PendingResponse(pending=items)


@router.post("/confirm")
async def confirm(
    body: ConfirmRequest,
    x_elder_device_token: str | None = Header(default=None, alias="X-Elder-Device-Token"),
) -> dict[str, object] | object:
    """§3.2.8 / §A.5：老人同意 / 拒绝 —— 服务端事务。"""
    if not x_elder_device_token:
        raise AppError(
            code=ErrorCode.UNAUTHORIZED,
            message="missing X-Elder-Device-Token",
            http_status=401,
        )
    attempt = assert_exists(await bind_attempt.find_by_bind_code(body.bind_code))
    check_not_expired(attempt)
    if attempt.elder_device_token != x_elder_device_token:
        raise AppError(
            code=ErrorCode.FORBIDDEN,
            message="bind_code belongs to a different device",
            http_status=403,
        )

    if body.decision == "reject":
        await bind_attempt.mark_rejected(attempt.id)
        emit_event(BIND_REJECTED, bind_attempt_id=str(attempt.id))
        # §6.3 reject：204 No Content
        from fastapi import Response

        return Response(status_code=204)

    if body.decision != "accept":
        raise AppError(
            code=ErrorCode.BAD_REQUEST,
            message="decision must be accept|reject",
            http_status=400,
        )

    # ---- accept：§A.5 事务 ----
    # 1. 创建 / 复用 elder_profile（device_token 唯一 → 同设备复用）
    existing = await elder_profile.get_by_device_token(x_elder_device_token)
    if existing is not None:
        elder = existing
    else:
        elder = await elder_profile.create(
            ElderProfileCreate(
                name=body.elder_name,
                device_token=x_elder_device_token,
                timezone="Asia/Shanghai",  # §5.2；客户端后续可改
            )
        )

    # 2. §H.25：时间最早成 primary；后续降级 secondary
    existing_primary = await binding.find_primary_by_elder(elder.id)
    role = BindingRole.SECONDARY if existing_primary is not None else BindingRole.PRIMARY

    # 3. 创建 binding
    new_binding = await binding.create(
        BindingCreate(
            family_id=attempt.family_user_id,
            elder_id=elder.id,
            role=role,
        )
    )

    # 4. 标记 bind_attempt confirmed
    await bind_attempt.mark_confirmed(attempt.id, elder_id=elder.id, binding_id=new_binding.id)

    emit_event(
        BIND_CONFIRMED,
        bind_attempt_id=str(attempt.id),
        elder_id=str(elder.id),
        binding_id=str(new_binding.id),
        role=role.value,
    )

    # 5. 签发 elder JWT（带 elder_id）—— 客户端拿到后替换之前的 device-token 凭据
    token = issue_token(str(elder.id), "elder", elder_id=str(elder.id))
    return ConfirmAcceptResponse(
        elder_id=str(elder.id),
        binding_id=str(new_binding.id),
        role=role.value,
        token=token,
    )


@router.delete(
    "/elder/{elder_id}",
    status_code=204,
    dependencies=[Depends(require_role("family"))],
)
async def unbind(
    elder_id: str,
    principal: Principal = Depends(require_role("family")),
) -> None:
    """§6.1 / §11.6：解绑 —— 仅 family primary 可调（仓储层再校验 role）。"""
    eid = UUID(elder_id)
    fam_id = UUID(principal.user_id)
    # 找 binding（family_id, elder_id）
    storage = get_storage()
    docs = await storage.find_many(
        CollectionName.BINDINGS,
        {"family_id": str(fam_id), "elder_id": str(eid)},
    )
    if not docs:
        raise AppError(
            code=ErrorCode.NOT_FOUND,
            message="binding not found",
            http_status=404,
        )
    from elder_common.schemas.binding import Binding

    b = Binding.model_validate(docs[0])
    if b.role is not BindingRole.PRIMARY:
        raise AppError(
            code=ErrorCode.FORBIDDEN,
            message="only primary family can unbind",
            http_status=403,
        )
    if principal.role != "family":
        raise AppError(
            code=ErrorCode.FORBIDDEN,
            message="only family can unbind",
            http_status=403,
        )
    await binding.delete(b.id)
    emit_event("bind.unbound", binding_id=str(b.id), elder_id=str(eid))
