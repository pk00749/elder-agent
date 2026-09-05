# mypy: disable-error-code="explicit-any"
# 原因：Pydantic BaseModel 父类签名含 Any
"""ASR 路由 —— /v1/agent/asr（PRD §6.1 / §3.1.2）。"""

from __future__ import annotations

from elder_common.auth import Principal, require_role
from elder_common.upstream import asr as asr_upstream
from fastapi import APIRouter, Depends
from pydantic import BaseModel, ConfigDict, Field

router = APIRouter(prefix="/v1/agent", tags=["agent-asr"])


class ASRRequest(BaseModel):
    """§6.3 入参。"""

    model_config = ConfigDict(extra="forbid")

    audio_url: str = Field(min_length=1, max_length=512)
    format: str = Field(default="m4a", min_length=1, max_length=16)


@router.post("/asr")
async def recognize(
    body: ASRRequest,
    _principal: Principal = Depends(require_role("elder")),
) -> dict[str, object]:
    """§6.1：上行音频 URL → 文字（千问 ASR，粤语主识别 + 普通话兜底，§K.2e）。"""
    result = asr_upstream.recognize(body.audio_url)
    return {"text": result.text, "confidence": result.confidence}
