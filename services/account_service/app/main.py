# account-service FastAPI 入口（PRD §8.1：挂在 /v1/auth /v1/bind /v1/diary 前缀）
from __future__ import annotations

from elder_common.errors import register_exception_handlers
from elder_common.logging import configure_logging, get_logger
from fastapi import FastAPI

logger = get_logger("account-service")
configure_logging()

app = FastAPI(
    title="elder-account-service",
    version="0.0.1",
    description="老友 Account 服务 —— 鉴权 / 绑定 / 日志查询（PRD §8.1）",
)
register_exception_handlers(app)


@app.get("/internal/health")
async def health() -> dict[str, str]:
    return {"status": "ok", "service": "account-service"}


# 业务路由挂载（PR 2 起补 auth / bind；PR 4 补 diary）
