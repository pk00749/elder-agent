# reminder-service FastAPI 入口（PRD §8.1：挂在 /v1/reminders 前缀）
from __future__ import annotations

from elder_common.errors import register_exception_handlers
from elder_common.logging import configure_logging, get_logger
from fastapi import FastAPI

logger = get_logger("reminder-service")
configure_logging()

app = FastAPI(
    title="elder-reminder-service",
    version="0.0.1",
    description="老友 Reminder 服务 —— 提醒 CRUD + 应答 + 推送（PRD §8.1）",
)
register_exception_handlers(app)


@app.get("/internal/health")
async def health() -> dict[str, str]:
    return {"status": "ok", "service": "reminder-service"}


# 业务路由挂载（PR 3 起补 reminders CRUD + ack）
