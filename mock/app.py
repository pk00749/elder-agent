# mock-upstream —— 模拟 DSH/ASR/TTS/COS/TPUSH 全部上游（§A.1 / PR 1 验证）。
# v2.1.2 MVP-DEFER：SMS 端点保留但不再被调用，v2.x 接 SMS 时直接复用。
#
# 所有端点返回固定 mock 响应，供 smoke test 验证 SDK 封装可达。
# PR 2 起会按需扩 mock 行为（如 ASR 置信度触发重试）。
from __future__ import annotations

import time

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

app = FastAPI(title="mock-upstream", version="0.0.1")


class ASRRequest(BaseModel):  # type: ignore[explicit-any] # Pydantic BaseModel 父类签名含 Any
    audio_url: str
    format: str = "m4a"


class TTSRequest(BaseModel):  # type: ignore[explicit-any] # Pydantic BaseModel 父类签名含 Any
    text: str
    voice: str = "zh_male_cantonese"


class CosPresignRequest(BaseModel):  # type: ignore[explicit-any] # Pydantic BaseModel 父类签名含 Any
    key: str
    bucket: str
    ttl: int


class TPushRequest(BaseModel):  # type: ignore[explicit-any] # Pydantic BaseModel 父类签名含 Any
    device_token: str
    title: str
    body: str
    channel_id: str
    extra: dict[str, object] = {}


class SMSRequest(BaseModel):  # type: ignore[explicit-any] # Pydantic BaseModel 父类签名含 Any
    phone: str
    code: str
    app_id: str
    template_id: str
    sign_name: str


class DSHMessage(BaseModel):  # type: ignore[explicit-any] # Pydantic BaseModel 父类签名含 Any
    role: str
    content: str


class DSHRequest(BaseModel):  # type: ignore[explicit-any] # Pydantic BaseModel 父类签名含 Any
    messages: list[DSHMessage]


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok", "service": "mock-upstream"}


# ---- DSH ----
@app.post("/dsh/chat")
async def dsh_chat(req: DSHRequest) -> dict[str, object]:
    last_user = next((m.content for m in reversed(req.messages) if m.role == "user"), "")
    reply = "今天过得怎么样？" if not last_user else f"（mock 回复）你说：{last_user[:30]}"
    return {"choices": [{"message": {"role": "assistant", "content": reply}}]}


# ---- ASR ----
@app.post("/asr/recognize")
async def asr_recognize(req: ASRRequest) -> dict[str, object]:
    return {"text": "今天和老张下棋，赢了", "confidence": 0.95}


# ---- TTS ----
@app.post("/tts/synthesize")
async def tts_synthesize(req: TTSRequest) -> dict[str, object]:
    return {
        "audio_url": f"https://mock.cos/tts-{int(time.time())}.mp3",
        "expires_in": 600,
    }


# ---- COS ----
@app.post("/cos/presign-put")
async def cos_presign_put(req: CosPresignRequest) -> dict[str, object]:
    return {
        "url": f"https://mock.cos/upload/{req.key}?X-Amz-Expires={req.ttl}",
        "key": req.key,
        "expires_in": req.ttl,
    }


@app.post("/cos/presign-get")
async def cos_presign_get(req: CosPresignRequest) -> dict[str, object]:
    return {
        "url": f"https://mock.cos/get/{req.key}?X-Amz-Expires={req.ttl}",
        "expires_in": req.ttl,
    }


# ---- TPush ----
@app.post("/tpush/send")
async def tpush_send(req: TPushRequest) -> dict[str, str]:
    return {"status": "ok", "message_id": f"mock-{int(time.time())}"}


# ---- SMS（v2.1.2 MVP-DEFER：保留路由便于 v2.x 接 SMS 时直接复用）----
@app.post("/sms/send")
async def sms_send(req: SMSRequest) -> dict[str, str]:
    return {"status": "ok"}


# ---- 故意失败端点（smoke test 验 AppError 抛 UPSTREAM_*）----
@app.post("/test/upstream-asr-fail")
async def upstream_asr_fail() -> dict[str, str]:
    raise HTTPException(status_code=500, detail="simulated upstream failure")
