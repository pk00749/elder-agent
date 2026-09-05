"""bind_attempt 仓储（§5.9，v2.1.1 新增）。"""

from __future__ import annotations

from datetime import timedelta
from uuid import UUID

from elder_common.constants import ErrorCode
from elder_common.errors import AppError
from elder_common.schemas.bind_attempt import (
    BindAttempt,
    BindAttemptCreate,
    BindAttemptStatus,
)
from elder_common.storage.base import CollectionName, get_storage
from elder_common.time import now_utc

# §3.2.7 §F.3 决议 22：bind_code 5 分钟有效
BIND_CODE_TTL_SECONDS = 5 * 60


def _new_bind_code() -> str:
    """生成 6 位字母数字 bind_code（足够唯一性 + 易扫码）。"""
    import secrets
    import string

    alphabet = string.ascii_uppercase + string.digits
    # 去掉视觉易混淆字符：0/O/1/I/L
    alphabet = alphabet.translate(str.maketrans("", "", "0O1IL"))
    return "".join(secrets.choice(alphabet) for _ in range(8))


async def create(payload: BindAttemptCreate) -> BindAttempt:
    """§A.5 / §3.2.7：家属扫码后服务端签发 bind_code + 5 分钟有效期。"""
    storage = get_storage()
    now = now_utc()
    doc: dict[str, object] = {
        "bind_code": payload.bind_code,
        "family_user_id": str(payload.family_user_id),
        "elder_device_token": payload.elder_device_token,
        "status": BindAttemptStatus.PENDING.value,
        "created_at": now.isoformat(),
    }
    attempt_id = await storage.insert(CollectionName.BIND_ATTEMPTS, doc)
    return BindAttempt(
        id=attempt_id,  # type: ignore[arg-type]
        bind_code=payload.bind_code,
        family_user_id=payload.family_user_id,
        elder_device_token=payload.elder_device_token,
        status=BindAttemptStatus.PENDING,
        elder_id=None,
        binding_id=None,
        created_at=now,
        responded_at=None,
    )


async def find_by_bind_code(bind_code: str) -> BindAttempt | None:
    """§3.2.8 老人同意/拒绝时按 bind_code 定位 attempt。"""
    storage = get_storage()
    doc = await storage.find_one(CollectionName.BIND_ATTEMPTS, {"bind_code": bind_code})
    if doc is None:
        return None
    return BindAttempt.model_validate(doc)


async def list_pending_by_device(
    device_token: str, *, now: object | None = None
) -> list[BindAttempt]:
    """§3.2.7 老人端 10s/次轮询 —— 队列模式（§F.3 决议 23）。"""
    storage = get_storage()
    docs = await storage.find_many(
        CollectionName.BIND_ATTEMPTS,
        {
            "elder_device_token": device_token,
            "family_user_id": {"$ne": "00000000-0000-0000-0000-000000000000"},
            "status": BindAttemptStatus.PENDING.value,
        },
        sort=[("created_at", -1)],
    )
    cutoff = (now_utc() - timedelta(seconds=BIND_CODE_TTL_SECONDS)).isoformat()
    out: list[BindAttempt] = []
    for d in docs:
        created = d.get("created_at")
        if isinstance(created, str) and created < cutoff:
            continue
        out.append(BindAttempt.model_validate(d))
    return out


async def mark_confirmed(
    attempt_id: UUID,
    *,
    elder_id: UUID,
    binding_id: UUID,
) -> bool:
    """§A.5 bind/confirm 事务最后一步。"""
    storage = get_storage()
    now = now_utc()
    return await storage.update_one(
        CollectionName.BIND_ATTEMPTS,
        {"id": str(attempt_id)},
        {
            "$set": {
                "status": BindAttemptStatus.CONFIRMED.value,
                "elder_id": str(elder_id),
                "binding_id": str(binding_id),
                "responded_at": now.isoformat(),
            }
        },
    )


async def mark_rejected(attempt_id: UUID) -> bool:
    """§A.5 bind/reject —— 拒绝后 bind_code 立即失效。"""
    storage = get_storage()
    now = now_utc()
    return await storage.update_one(
        CollectionName.BIND_ATTEMPTS,
        {"id": str(attempt_id)},
        {
            "$set": {
                "status": BindAttemptStatus.REJECTED.value,
                "responded_at": now.isoformat(),
            }
        },
    )


def check_not_expired(attempt: BindAttempt) -> None:
    """§3.2.7 §F.3 决议 22/26 —— 5 分钟过期或被拒绝后失效。"""
    if attempt.status in (BindAttemptStatus.REJECTED, BindAttemptStatus.EXPIRED):
        raise AppError(
            code=ErrorCode.BIND_CODE_EXPIRED,
            message="bind_code already responded or expired",
            http_status=422,
        )
    age = now_utc() - attempt.created_at
    if age > timedelta(seconds=BIND_CODE_TTL_SECONDS):
        raise AppError(
            code=ErrorCode.BIND_CODE_EXPIRED,
            message="bind_code expired (>5 min)",
            http_status=422,
        )


def assert_exists(attempt: BindAttempt | None) -> BindAttempt:
    """§6.2 BIND_ATTEMPT_NOT_FOUND —— 找不到或已响应都按 not-found 处理。"""
    if attempt is None:
        raise AppError(
            code=ErrorCode.BIND_ATTEMPT_NOT_FOUND,
            message="bind_attempt not found",
            http_status=422,
        )
    return attempt


def fresh_bind_code() -> str:
    """生成新 bind_code（供路由层从 create() 之前调）。"""
    return _new_bind_code()
