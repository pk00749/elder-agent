# COS 预签 URL（§A.1 / §12）。
from __future__ import annotations

from typing import cast

import httpx

from elder_common.config import get_settings
from elder_common.constants import ErrorCode
from elder_common.errors import AppError
from elder_common.events import UPSTREAM_ERROR
from elder_common.logging import emit_event


def presign_put(key: str) -> str:
    """PUT 上传预签 URL（24h 过期，§11.13）。"""
    settings = get_settings()
    try:
        resp = httpx.post(
            f"{settings.cos_base_url}/presign-put",
            json={
                "key": key,
                "bucket": settings.cos_bucket,
                "ttl": settings.cos_upload_ttl_seconds,
            },
            timeout=httpx.Timeout(10.0),
        )
        resp.raise_for_status()
    except httpx.HTTPError as exc:
        emit_event(UPSTREAM_ERROR, upstream="cos", error=str(exc))
        raise AppError(
            code=ErrorCode.INTERNAL_ERROR,
            message="COS presign failed",
            http_status=503,
        ) from exc
    return cast(str, resp.json()["url"])


def presign_get(key: str) -> str:
    """GET 下载预签 URL（24h 过期，§11.13）。"""
    settings = get_settings()
    try:
        resp = httpx.post(
            f"{settings.cos_base_url}/presign-get",
            json={
                "key": key,
                "bucket": settings.cos_bucket,
                "ttl": settings.cos_upload_ttl_seconds,
            },
            timeout=httpx.Timeout(10.0),
        )
        resp.raise_for_status()
    except httpx.HTTPError as exc:
        emit_event(UPSTREAM_ERROR, upstream="cos", error=str(exc))
        raise AppError(
            code=ErrorCode.INTERNAL_ERROR,
            message="COS presign failed",
            http_status=503,
        ) from exc
    return cast(str, resp.json()["url"])
