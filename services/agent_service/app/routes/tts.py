# mypy: disable-error-code="explicit-any"
# 原因：Pydantic BaseModel 父类签名含 Any
"""TTS 路由 —— /v1/agent/tts（PRD §6.1 / §3.1.2 / §H.15）。"""
from __future__ import annotations

from elder_common.auth import Principal, require_role
from elder_common.upstream import tts as tts_upstream
from fastapi import APIRouter, Depends
from pydantic import BaseModel, ConfigDict, Field

router = APIRouter(prefix="/v1/agent", tags=["agent-tts"])


class TTSRequest(BaseModel):
    """§6.3 入参。"""

    model_config = ConfigDict(extra="forbid")

    text: str = Field(min_length=1, max_length=200)


@router.post("/tts")
async def synthesize(
    body: TTSRequest,
    _principal: Principal = Depends(require_role("elder")),
) -> dict[str, object]:
    """§6.1：文字 → 音频 URL（千问 TTS，粤语男声，§H.15）。"""
    audio = tts_upstream.synthesize(body.text)
    return {"audio_url": audio.url, "expires_in": audio.expires_in}
