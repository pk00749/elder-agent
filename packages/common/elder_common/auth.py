"""鉴权：JWT 签发 / 校验 + FastAPI 依赖（AGENTS.md §7.4 / §9 / §13）。

主流程：
- `/v1/auth/login` 拿到 sms_code 验证通过后调 `issue_token(user_id, role, elder_id)`
- 每个保护路由加 `Depends(require_role("family"))`
- family 角色调仓储层做 elder 归属校验（§10 / §18 红线 —— 路由层不做 in-code 越权）
"""

from __future__ import annotations

import threading
import time
from collections import defaultdict, deque
from collections.abc import Callable
from dataclasses import dataclass
from typing import Literal, cast

from fastapi import Header
from jose import JWTError, jwt

from elder_common.config import get_settings
from elder_common.constants import ErrorCode
from elder_common.errors import AppError

Role = Literal["elder", "family"]


@dataclass(frozen=True)
class Principal:
    """JWT 解码后的当前用户。"""

    user_id: str
    role: Role
    elder_id: str | None = None


def issue_token(
    user_id: str,
    role: Role,
    *,
    elder_id: str | None = None,
) -> str:
    """签发 JWT（§7.4 单 JWT 7 天有效，§11.11 无 refresh token）。"""
    settings = get_settings()
    now = int(time.time())
    payload: dict[str, object] = {
        "user_id": user_id,
        "role": role,
        "iat": now,
        "exp": now + settings.jwt_expires_seconds,
    }
    if elder_id is not None:
        payload["elder_id"] = elder_id
    token = jwt.encode(payload, settings.jwt_secret, algorithm=settings.jwt_algorithm)
    return token


def decode_token(token: str) -> Principal:
    """解码 + 校验 JWT；失败抛 AppError(UNAUTHORIZED)。"""
    settings = get_settings()
    try:
        payload_obj: object = jwt.decode(
            token,
            settings.jwt_secret,
            algorithms=[settings.jwt_algorithm],
        )
    except JWTError as exc:
        raise AppError(
            code=ErrorCode.UNAUTHORIZED,
            message="invalid or expired token",
            http_status=401,
        ) from exc

    if not isinstance(payload_obj, dict):
        raise AppError(
            code=ErrorCode.UNAUTHORIZED,
            message="malformed token payload",
            http_status=401,
        )
    payload = cast(dict[str, object], payload_obj)

    user_id_raw = payload.get("user_id")
    role_raw = payload.get("role")
    if not isinstance(user_id_raw, str) or role_raw not in ("elder", "family"):
        raise AppError(
            code=ErrorCode.UNAUTHORIZED,
            message="malformed token claims",
            http_status=401,
        )
    elder_id_raw = payload.get("elder_id")
    elder_id = elder_id_raw if isinstance(elder_id_raw, str) else None
    role: Role = "elder" if role_raw == "elder" else "family"
    return Principal(user_id=user_id_raw, role=role, elder_id=elder_id)


def _extract_bearer(authorization: str | None) -> str:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise AppError(
            code=ErrorCode.UNAUTHORIZED,
            message="missing Authorization header",
            http_status=401,
        )
    return authorization.split(" ", 1)[1].strip()


def require_role(*allowed: Role) -> Callable[..., Principal]:  # type: ignore[explicit-any] # FastAPI Depends 依赖可接受任意注入参数
    """FastAPI 依赖工厂：限制角色（§9 / §13）。

    用法：`dependencies=[Depends(require_role("family"))]`
    """

    def _dep(authorization: str | None = Header(default=None)) -> Principal:
        principal = decode_token(_extract_bearer(authorization))
        if allowed and principal.role not in allowed:
            raise AppError(
                code=ErrorCode.FORBIDDEN,
                message=f"role {principal.role} not allowed",
                http_status=403,
            )
        return principal

    return _dep


def assert_elder_match(principal: Principal, elder_id: str) -> None:
    """校验 principal 是否有权访问 elder_id（§10 / §18 红线）。

    - elder 角色：principal.elder_id 必须 == elder_id
    - family 角色：由调用方在仓储层二次校验 binding 归属
    """
    if principal.role == "elder":
        if principal.elder_id != elder_id:
            raise AppError(
                code=ErrorCode.FORBIDDEN,
                message="elder_id mismatch",
                http_status=403,
            )
    elif principal.role != "family":
        raise AppError(
            code=ErrorCode.FORBIDDEN,
            message=f"unknown role: {principal.role}",
            http_status=403,
        )


# ---- 限流（§6.1 / §10.2）----
class _SlidingWindowLimiter:
    """进程内滑动窗口 —— 单实例够用；多实例部署换 Redis（PR 4 部署 PR）。"""

    def __init__(self, max_calls: int, window_seconds: float) -> None:
        self.max_calls = max_calls
        self.window = window_seconds
        self._hits: dict[str, deque[float]] = defaultdict(deque)
        self._lock = threading.Lock()

    def hit(self, key: str) -> bool:
        """返回 True 表示允许；False 表示触发限流。"""
        now = time.monotonic()
        with self._lock:
            bucket = self._hits[key]
            cutoff = now - self.window
            while bucket and bucket[0] < cutoff:
                bucket.popleft()
            if len(bucket) >= self.max_calls:
                return False
            bucket.append(now)
            return True


_limiters: dict[str, _SlidingWindowLimiter] = {}


def rate_limit(name: str, max_calls: int, window_seconds: float) -> Callable[..., None]:  # type: ignore[explicit-any] # FastAPI Depends 依赖可接受任意注入参数
    """FastAPI 依赖工厂：按 `name` 限流（§6.1 / §10.2）。

    命中后抛 AppError(RATE_LIMITED, retry_after_seconds)。
    """
    limiter = _limiters.setdefault(name, _SlidingWindowLimiter(max_calls, window_seconds))

    def _dep(authorization: str | None = Header(default=None)) -> None:
        key = _extract_bearer(authorization) if authorization else "anonymous"
        if not limiter.hit(key):
            raise AppError(
                code=ErrorCode.RATE_LIMITED,
                message=f"rate limit exceeded for {name}",
                http_status=429,
                retry_after_seconds=int(window_seconds),
            )

    return _dep
