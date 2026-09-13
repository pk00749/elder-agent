# elder_common —— 跨服务共享代码（AGENTS.md §1）。
# 模块清单：
# - errors：AppError + 全局异常处理器（§4）
# - config：Settings + validate()（§8）
# - logging：structlog 包装 + sanitize() + emit_event()（§7）
# - events：业务事件名常量（§7 末段）
# - time：now_utc() 等时区工具（§A.4）
# - redact：phone_tail() 等 PII 脱敏（§18 红线）
# - cos：cos_key_builder + 24h 预签（§12 / §11.13）
# - constants：错误码常量（§6.2）
# - migrate：CLI（§19）
# - upstream：DSH / ASR / TTS / COS / TPush / SMS 封装（§A.1）
from __future__ import annotations

from elder_common.config import Settings, get_settings
from elder_common.constants import ErrorCode
from elder_common.errors import AppError, register_exception_handlers
from elder_common.events import (
    AUTH_ANONYMOUS_INIT,
    AUTH_LOGIN,
    AUTH_SMS_SENT,
    BIND_ATTEMPT_CREATED,
    BIND_CONFIRMED,
    BIND_REJECTED,
    BIND_UNBOUND,
    DIARY_FLUSHED,
    REMINDER_ACKED,
    REMINDER_CREATED,
    REMINDER_DELETED,
    REMINDER_MISSED,
    REMINDER_PUSHED,
    REMINDER_UPDATED,
    SESSION_ABANDONED,
    SESSION_FINALIZED,
    SESSION_STARTED,
    SESSION_TURN,
    SMS_SENT,
    TPUSH_SENT,
    UPSTREAM_CALL,
    UPSTREAM_ERROR,
)
from elder_common.logging import emit_event, get_logger, sanitize

__all__ = [
    "AUTH_ANONYMOUS_INIT",
    "AUTH_LOGIN",
    "AUTH_SMS_SENT",
    "BIND_ATTEMPT_CREATED",
    "BIND_CONFIRMED",
    "BIND_REJECTED",
    "BIND_UNBOUND",
    "DIARY_FLUSHED",
    "REMINDER_ACKED",
    "REMINDER_CREATED",
    "REMINDER_DELETED",
    "REMINDER_MISSED",
    "REMINDER_PUSHED",
    "REMINDER_UPDATED",
    "SESSION_ABANDONED",
    "SESSION_FINALIZED",
    "SESSION_STARTED",
    "SESSION_TURN",
    "SMS_SENT",
    "TPUSH_SENT",
    "UPSTREAM_CALL",
    "UPSTREAM_ERROR",
    "AppError",
    "ErrorCode",
    "Settings",
    "emit_event",
    "get_logger",
    "get_settings",
    "register_exception_handlers",
    "sanitize",
]
