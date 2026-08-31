# mypy: disable-error-code="explicit-any"
# 原因：FastAPI 装饰器签名含 Any，与 §5 禁 Any 冲突
# reminder-service FastAPI 入口（PRD §8.1：挂在 /v1/reminders 前缀）
from __future__ import annotations

from elder_common.errors import register_exception_handlers
from elder_common.logging import configure_logging, get_logger
from fastapi import FastAPI
from services.reminder_service.app.routes.reminders import router as reminders_router

logger = get_logger("reminder-service")
configure_logging()

app = FastAPI(
    title="elder-reminder-service",
    version="0.0.2",
    description="老友 Reminder 服务 —— 提醒 CRUD + 应答 + 推送（PRD §8.1）",
)
register_exception_handlers(app)


@app.get("/internal/health")
async def health() -> dict[str, str]:
    return {"status": "ok", "service": "reminder-service"}


# 业务路由（PR 2）
app.include_router(reminders_router)
