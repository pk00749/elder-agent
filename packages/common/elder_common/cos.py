# COS key 构造 + 24h 预签 URL（§12 / §11.13）。
from __future__ import annotations

from datetime import UTC, datetime

from elder_common.config import get_settings


class CosKeyBuilder:
    """按 PRD §5.8 构造 COS key —— 业务代码禁止字符串拼接（§12 红线）。"""

    @staticmethod
    def diary_elder_audio(elder_id: str, diary_id: str, segment_no: int) -> str:
        return f"elder/{elder_id}/diary/{diary_id}/seg-{segment_no}.m4a"

    @staticmethod
    def diary_agent_reply(elder_id: str, diary_id: str, segment_no: int) -> str:
        return f"elder/{elder_id}/diary/{diary_id}/reply-{segment_no}.m4a"

    @staticmethod
    def tmp_upload(elder_id: str, uuid: str) -> str:
        return f"tmp/{elder_id}/{uuid}.m4a"


def build_presigned_url(key: str, expires_in: int | None = None) -> str:
    """构造 24h 过期预签 URL（§12 / §11.13）。

    PR 1 mock 实现：返回带 expires 参数的占位 URL；
    PR 2 起接入真实 COS SDK 替换。
    """
    ttl = expires_in if expires_in is not None else get_settings().cos_upload_ttl_seconds
    ts = int(datetime.now(tz=UTC).timestamp())
    return f"https://mock.cos/{key}?expires={ttl}&ts={ts}"


def is_expired(cos_key: str) -> bool:
    """PR 1 占位：mock URL 不视为过期。PR 2 接 SDK 后真实判断。"""
    return False
