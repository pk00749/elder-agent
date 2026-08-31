# agent-service FastAPI 入口（PRD §8.1：挂在 /v1/agent 前缀）
from __future__ import annotations

from elder_common.errors import register_exception_handlers
from elder_common.logging import configure_logging, get_logger
from fastapi import FastAPI

logger = get_logger("agent-service")
configure_logging()

app = FastAPI(
    title="elder-agent-service",
    version="0.0.1",
    description="老友 Agent 服务 —— ASR / TTS / LLM / Agent session（PRD §8.1）",
)

# 全局异常处理器（§4）
register_exception_handlers(app)


@app.get("/internal/health")
async def health() -> dict[str, str]:
    """健康检查（§9：internal/* 不出网关）。"""
    return {"status": "ok", "service": "agent-service"}


# 业务路由挂载（PR 2/4 起补 ASR / TTS / session / turn / finalize）
# 路径前缀严格按 PRD §6.1
