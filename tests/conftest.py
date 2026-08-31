# 测试公共 fixture（AGENTS.md §14）。
from __future__ import annotations

import os

import pytest


@pytest.fixture
def mock_infra_enabled() -> bool:
    """mock infra 是否可达。CI 跑 docker compose up 后为 enabled。"""
    return os.environ.get("MOCK_INFRA", "").lower() == "enabled"

pytest_plugins: list[str] = []
