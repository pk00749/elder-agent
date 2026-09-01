"""Prompt 版本注册表（AGENTS.md §11）。"""

from __future__ import annotations

import os
from importlib import resources
from typing import Final

from elder_common.constants import ErrorCode
from elder_common.errors import AppError

CURRENT_VERSION: Final[str] = "v1"
_PROMPTS_PACKAGE: Final[str] = "services.agent_service.app.agent.prompts"


def _load(name: str) -> str:
    """从嵌入资源读 prompt 文本（确保打包后也能用）。"""
    try:
        return resources.files(_PROMPTS_PACKAGE).joinpath(name).read_text(encoding="utf-8")
    except (FileNotFoundError, OSError) as exc:
        raise AppError(
            code=ErrorCode.INTERNAL_ERROR,
            message=f"prompt not found: {name}",
            http_status=500,
        ) from exc


def system_prompt(version: str | None = None) -> str:
    """§11：按 PROMPT_VERSION env（默认 v1）选 prompt。"""
    v = version or os.environ.get("PROMPT_VERSION", CURRENT_VERSION)
    return _load(f"system_{v}.txt")


def greeting_prompt(version: str | None = None) -> str:
    v = version or os.environ.get("PROMPT_VERSION", CURRENT_VERSION)
    return _load(f"greeting_{v}.txt")


def save_prompt(version: str | None = None) -> str:
    v = version or os.environ.get("PROMPT_VERSION", CURRENT_VERSION)
    return _load(f"save_{v}.txt")
