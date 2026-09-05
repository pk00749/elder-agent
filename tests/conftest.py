# 测试公共 fixture（AGENTS.md §14）。
from __future__ import annotations

import os

import pytest
import pytest_asyncio
from elder_common.storage.base import set_storage
from elder_common.storage.memory import InMemoryStorage
from httpx import ASGITransport, AsyncClient


@pytest.fixture
def mock_infra_enabled() -> bool:
    """mock infra 是否可达。CI 跑 docker compose up 后为 enabled。"""
    return os.environ.get("MOCK_INFRA", "").lower() == "enabled"


@pytest.fixture(autouse=True)
def _reset_storage() -> None:
    """每个测试前重置 InMemoryStorage —— dev / 测试唯一数据后端。"""
    set_storage(InMemoryStorage())
    yield
    set_storage(InMemoryStorage())


@pytest.fixture(autouse=True)
def _v21x_limiter_placeholder() -> None:
    """v2.1.2：MVP 阶段 anonymous-device 不限流（services/account_service/app/deps.py）。

    保留 autouse 占位 —— v2.x 接 SMS 后回填 per-IP 限流器重置逻辑。
    """
    yield


@pytest_asyncio.fixture
async def account_client():
    """account-service httpx AsyncClient。"""
    from services.account_service.app.main import app

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        yield client


@pytest_asyncio.fixture
async def reminder_client():
    """reminder-service httpx AsyncClient。"""
    from services.reminder_service.app.main import app

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        yield client


@pytest_asyncio.fixture
async def agent_client():
    """agent-service httpx AsyncClient。"""
    from services.agent_service.app.main import app

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        yield client
