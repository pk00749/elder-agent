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
def _reset_sms_store() -> None:
    """每个测试前清空 SMS 验证码池。"""
    from services.account_service.app.deps import _sms_store

    with _sms_store._lock:
        _sms_store._codes.clear()
        _sms_store._last_sent.clear()
        _sms_store._fail_counts.clear()
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
