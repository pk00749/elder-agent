# 腾讯 TPush 推送封装（§A.1 / §11.18 双通道）。
from __future__ import annotations

from enum import Enum

import httpx

from elder_common.config import get_settings
from elder_common.constants import ErrorCode
from elder_common.errors import AppError
from elder_common.events import TPUSH_SENT, UPSTREAM_ERROR
from elder_common.logging import emit_event


class ChannelPriority(str, Enum):
    """TPush 通道优先级（§11.18）。

    MID 走 reminder channel（服药提醒）；HIGH 走 appointment channel（就医，绕过勿扰）。
    """

    MID = "mid"
    HIGH = "high"


def send(
    device_token: str,
    title: str,
    body: str,
    *,
    channel_priority: ChannelPriority,
    extra: dict[str, str] | None = None,
) -> None:
    """发送 TPush 推送，按 channel_priority 自动选通道（§11.18）。"""
    settings = get_settings()
    channel_id = (
        settings.tpush_reminder_channel_id
        if channel_priority == ChannelPriority.MID
        else settings.tpush_appointment_channel_id
    )
    try:
        resp = httpx.post(
            f"{settings.tpush_base_url}/send",
            json={
                "device_token": device_token,
                "title": title,
                "body": body,
                "channel_id": channel_id,
                "extra": extra or {},
            },
            timeout=httpx.Timeout(10.0),
        )
        resp.raise_for_status()
    except httpx.HTTPError as exc:
        emit_event(UPSTREAM_ERROR, upstream="tpush", error=str(exc))
        raise AppError(
            code=ErrorCode.INTERNAL_ERROR,
            message="TPush send failed",
            http_status=503,
        ) from exc
    emit_event(TPUSH_SENT, channel_priority=channel_priority.value)
