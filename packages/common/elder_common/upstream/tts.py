# 千问 TTS 封装（§A.1 / PRD §10.4）。
from __future__ import annotations

from dataclasses import dataclass

import httpx

from elder_common.config import get_settings
from elder_common.constants import ErrorCode
from elder_common.errors import AppError
from elder_common.events import UPSTREAM_CALL, UPSTREAM_ERROR
from elder_common.logging import emit_event


@dataclass(frozen=True)
class AudioUrl:
    """TTS 返回音频 URL + 过期秒数（§6.3）。"""

    url: str
    expires_in: int


def synthesize(text: str) -> AudioUrl:
    """调千问 TTS（粤语男声，§H.15）。失败抛 UPSTREAM_TTS。

    失败兜底走系统铃声（§H.17）；服务端不降级第三方铃声 SDK。
    """
    settings = get_settings()
    try:
        resp = httpx.post(
            f"{settings.tts_base_url}/synthesize",
            json={"text": text, "voice": settings.tts_voice},
            timeout=httpx.Timeout(30.0),
        )
        resp.raise_for_status()
    except httpx.HTTPError as exc:
        emit_event(UPSTREAM_ERROR, upstream="tts", error=str(exc))
        raise AppError(
            code=ErrorCode.UPSTREAM_TTS,
            message="TTS upstream unavailable",
            http_status=503,
        ) from exc

    body = resp.json()
    emit_event(UPSTREAM_CALL, upstream="tts", voice=settings.tts_voice)
    return AudioUrl(url=body["audio_url"], expires_in=int(body["expires_in"]))
