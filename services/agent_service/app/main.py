# mypy: disable-error-code="explicit-any"
# 原因：FastAPI 装饰器签名含 Any
# agent-service FastAPI 入口（PRD §8.1：挂在 /v1/agent/* 前缀）
from __future__ import annotations

from elder_common.errors import register_exception_handlers
from elder_common.logging import configure_logging, get_logger
from fastapi import FastAPI
from services.agent_service.app.routes.asr import router as asr_router
from services.agent_service.app.routes.diary import router as diary_router
from services.agent_service.app.routes.tts import router as tts_router

logger = get_logger("agent-service")
configure_logging()

app = FastAPI(
    title="elder-agent-service",
    version="0.0.2",
    description="老友 Agent 服务 —— AI 访谈写日志（PRD §8.1）",
)
register_exception_handlers(app)


@app.get("/internal/health")
async def health() -> dict[str, str]:
    return {"status": "ok", "service": "agent-service"}


# 业务路由（PR 3）
app.include_router(diary_router)
app.include_router(asr_router)
app.include_router(tts_router)
