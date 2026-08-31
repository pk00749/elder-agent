# mypy: disable-error-code="explicit-any"
# 原因：FastAPI 装饰器签名含 Any，与 §5 禁 Any 冲突（mock/app.py 同款）
# account-service FastAPI 入口（PRD §8.1：挂在 /v1/auth /v1/bind /v1/diary 前缀）
from __future__ import annotations

from elder_common.errors import register_exception_handlers
from elder_common.logging import configure_logging, get_logger
from fastapi import FastAPI
from services.account_service.app.routes.auth import router as auth_router
from services.account_service.app.routes.bind import router as bind_router
from services.account_service.app.routes.me import router as me_router

logger = get_logger("account-service")
configure_logging()

app = FastAPI(
    title="elder-account-service",
    version="0.0.2",
    description="老友 Account 服务 —— 鉴权 / 绑定 / 日志查询（PRD §8.1）",
)
register_exception_handlers(app)


@app.get("/internal/health")
async def health() -> dict[str, str]:
    return {"status": "ok", "service": "account-service"}


# 业务路由（PR 2）
app.include_router(auth_router)
app.include_router(bind_router)
app.include_router(me_router)
# /v1/diary/* —— account_service per §8.1；PR 3 起补（依赖 agent_service 的 diary_session 数据）
