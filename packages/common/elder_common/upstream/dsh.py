# DeepSeek Harness 封装（§A.1 / PRD §8.2）。
from __future__ import annotations

from collections.abc import Mapping
from typing import cast

import httpx

from elder_common.config import get_settings
from elder_common.constants import ErrorCode
from elder_common.errors import AppError
from elder_common.events import UPSTREAM_ERROR
from elder_common.logging import emit_event


def chat(messages: list[dict[str, str]], **kwargs: object) -> str:
    """调 DSH chat completion；返回 assistant message content。

    失败抛 AppError(UPSTREAM_LLM, 502)。
    """
    settings = get_settings()
    headers: dict[str, str] = {}
    if settings.dsh_api_key:
        headers["Authorization"] = f"Bearer {settings.dsh_api_key}"
    payload = cast(Mapping[str, object], {"messages": messages, **kwargs})
    try:
        resp = httpx.post(
            f"{settings.dsh_base_url}/chat",
            json=payload,
            headers=headers,
            timeout=httpx.Timeout(30.0),
        )
        resp.raise_for_status()
    except httpx.HTTPError as exc:
        emit_event(UPSTREAM_ERROR, upstream="dsh", error=str(exc))
        raise AppError(
            code=ErrorCode.UPSTREAM_LLM,
            message="LLM upstream unavailable",
            http_status=502,
        ) from exc
    body = resp.json()
    choices = body.get("choices", [])
    if not choices:
        raise AppError(
            code=ErrorCode.UPSTREAM_LLM,
            message="LLM returned empty choices",
            http_status=502,
        )
    return str(choices[0]["message"]["content"])
