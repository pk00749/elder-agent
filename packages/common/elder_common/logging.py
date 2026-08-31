# structlog 配置 + sanitize() + emit_event()（AGENTS.md §7）。
from __future__ import annotations

import logging
import re
from collections.abc import Mapping
from typing import TYPE_CHECKING, cast

import structlog
from structlog.types import EventDict, Processor

if TYPE_CHECKING:
    from structlog.stdlib import BoundLogger

# 禁止写入原始音频 URL / ASR 全文 / 用户密码 / 密钥（§7 / §18 红线）
_SENSITIVE_KEYS = frozenset(
    {
        "password",
        "passwd",
        "secret",
        "token",
        "api_key",
        "access_key",
        "audio_url",
        "elder_audio_url",
        "asr_text",
        "elder_text",
        "raw_audio",
        "raw_text",
    }
)

_PHONE_PATTERN = re.compile(r"\b1[3-9]\d{9}\b")


def sanitize(data: Mapping[str, object] | str) -> dict[str, object] | str:
    """清洗敏感字段（§7 / §18 红线）。

    - 屏蔽 _SENSITIVE_KEYS 字段值
    - 替换文本中的手机号尾四位
    """
    if isinstance(data, str):
        return _PHONE_PATTERN.sub(lambda m: m.group()[:3] + "****" + m.group()[-4:], data)
    out: dict[str, object] = {}
    for k, v in data.items():
        lk = k.lower()
        if any(s in lk for s in _SENSITIVE_KEYS):
            out[k] = "***"
        elif isinstance(v, Mapping):
            out[k] = sanitize(cast(Mapping[str, object], v))
        elif isinstance(v, str):
            out[k] = sanitize(v)
        else:
            out[k] = v
    return out


def get_logger(name: str | None = None) -> BoundLogger:
    """取 logger。structlog.get_logger 在 stubs 里返回 Any，cast 提类型。"""
    return cast("BoundLogger", structlog.get_logger(name))


def emit_event(name: str, **fields: object) -> None:
    """业务事件统一入口（§7）。所有事件名集中在 events 常量。"""
    logger = get_logger("event")
    fields_map = cast(Mapping[str, object], fields)
    cleaned = sanitize(fields_map)
    # sanitize 返回 dict | str；只有 dict 路径能 spread 进 logger.info
    if isinstance(cleaned, dict):
        logger.info(name, **cleaned)


def configure_logging(level: str = "INFO", fmt: str = "json") -> None:
    """初始化 structlog。dev 环境走 pretty，prod 走 JSON。"""
    level_int = getattr(logging, level.upper(), logging.INFO)
    processors: list[Processor] = [
        structlog.contextvars.merge_contextvars,
        structlog.processors.add_log_level,
        structlog.processors.TimeStamper(fmt="iso"),
        _drop_color_message,
    ]
    if fmt == "json":
        processors.append(structlog.processors.JSONRenderer())
    else:
        processors.append(structlog.dev.ConsoleRenderer(colors=False))
    structlog.configure(
        processors=processors,
        wrapper_class=structlog.make_filtering_bound_logger(level_int),
        logger_factory=structlog.PrintLoggerFactory(),
        cache_logger_on_first_use=True,
    )


def _drop_color_message(_: object, __: str, event_dict: EventDict) -> EventDict:
    """uvicorn 默认带的 colorize 字段清理。"""
    event_dict.pop("color_message", None)
    return event_dict
