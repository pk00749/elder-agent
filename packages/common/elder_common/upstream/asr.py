# 千问 ASR 封装（§A.1 / PRD §10.4）。
from __future__ import annotations

from dataclasses import dataclass

import httpx

from elder_common.config import get_settings
from elder_common.constants import ErrorCode
from elder_common.errors import AppError
from elder_common.events import UPSTREAM_CALL, UPSTREAM_ERROR
from elder_common.logging import emit_event


@dataclass(frozen=True)
class ASRResult:
    """ASR 返回值（§10.4）。

    confidence < 0.6 由客户端走录音重试流程，不消耗轮数（§3.1.2）。
    """

    text: str
    confidence: float


def recognize(audio_url: str) -> ASRResult:
    """调千问 ASR（粤语主识别 + 普通话兜底，§K.2e）。

    失败抛 AppError(UPSTREAM_ASR)。
    """
    settings = get_settings()
    try:
        resp = httpx.post(
            f"{settings.asr_base_url}/recognize",
            json={"audio_url": audio_url, "format": "m4a"},
            timeout=httpx.Timeout(30.0),
        )
        resp.raise_for_status()
    except httpx.HTTPError as exc:
        emit_event(UPSTREAM_ERROR, upstream="asr", error=str(exc))
        raise AppError(
            code=ErrorCode.UPSTREAM_ASR,
            message="ASR upstream unavailable",
            http_status=503,
        ) from exc

    body = resp.json()
    emit_event(UPSTREAM_CALL, upstream="asr", confidence=body.get("confidence", 0.0))
    return ASRResult(text=body["text"], confidence=float(body["confidence"]))
