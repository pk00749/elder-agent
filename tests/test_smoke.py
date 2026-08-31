# Smoke test —— PR 1 验证（AGENTS.md §15）。
# 不依赖外部 mock infra（external marker 显式标注）。
from __future__ import annotations

import pytest


def test_imports() -> None:
    """所有公共模块可正常 import。"""
    import elder_common

    assert elder_common.AppError is not None
    assert elder_common.ErrorCode is not None
    assert elder_common.Settings is not None


def test_settings_validate() -> None:
    """Settings 在 dev 环境可实例化（JWT_SECRET 默认值 ≥32 字符）。"""
    from elder_common.config import Settings

    s = Settings()
    s.check_required()
    assert s.jwt_algorithm == "HS256"
    assert s.cos_bucket == "elder-agent-dev"


def test_error_code_values() -> None:
    """错误码与 PRD §6.2 一致。"""
    from elder_common.constants import ErrorCode

    assert ErrorCode.UNAUTHORIZED == "UNAUTHORIZED"
    assert ErrorCode.REMINDER_TIME_PAST == "REMINDER_TIME_PAST"
    assert ErrorCode.BIND_CODE_EXPIRED == "BIND_CODE_EXPIRED"
    assert ErrorCode.UPSTREAM_ASR == "UPSTREAM_ASR"


def test_app_error_field() -> None:
    """AppError 字段透传（§4）。"""
    from elder_common.errors import AppError

    err = AppError(
        code="VALIDATION_ERROR",
        message="elder_id required",
        field="body.elder_id",
        http_status=422,
    )
    assert err.code == "VALIDATION_ERROR"
    assert err.field == "body.elder_id"
    assert err.http_status == 422


def test_sanitize_password_key() -> None:
    """sanitize() 屏蔽 password / token 等敏感字段（§7 红线）。"""
    from elder_common.logging import sanitize

    out = sanitize({"username": "alice", "password": "secret123", "token": "abc"})
    assert out["username"] == "alice"
    assert out["password"] == "***"
    assert out["token"] == "***"


def test_sanitize_phone_in_string() -> None:
    """sanitize() 在字符串里替换手机号尾四位（§7 红线）。"""
    from elder_common.logging import sanitize

    assert sanitize("call me at 13800138000") == "call me at 138****8000"


def test_cos_key_builder() -> None:
    """COS key 构造走工厂方法，禁止字符串拼接（§12 红线）。"""
    from elder_common.cos import CosKeyBuilder

    assert CosKeyBuilder.diary_elder_audio("e1", "d1", 3) == "elder/e1/diary/d1/seg-3.m4a"
    assert CosKeyBuilder.tmp_upload("e1", "uuid-abc") == "tmp/e1/uuid-abc.m4a"


@pytest.mark.external
def test_upstream_reachable(mock_infra_enabled: bool) -> None:
    """mock infra 连通性：CI 起 docker compose 后跑（-m external）。"""
    if not mock_infra_enabled:
        pytest.skip("MOCK_INFRA not enabled")
    from elder_common.upstream import asr, cos, dsh, sms, tpush, tts

    asr_result = asr.recognize("https://mock.cos/audio.m4a")
    assert asr_result.text
    assert 0.0 <= asr_result.confidence <= 1.0
    tts_audio = tts.synthesize("hello")
    assert tts_audio.url
    assert tts_audio.expires_in > 0
    assert cos.presign_put("tmp/test.m4a")
    assert cos.presign_get("tmp/test.m4a")
    assert dsh.chat([{"role": "user", "content": "hi"}])
    tpush.send(
        device_token="mock-token",
        title="t",
        body="b",
        channel_priority=tpush.ChannelPriority.MID,
    )
    sms.send_code("13800138000")


@pytest.mark.external
def test_upstream_failure_raises_app_error(mock_infra_enabled: bool) -> None:
    """ASR 失败端 → AppError(UPSTREAM_ASR)。"""
    if not mock_infra_enabled:
        pytest.skip("MOCK_INFRA not enabled")
    from elder_common.config import get_settings
    from elder_common.constants import ErrorCode
    from elder_common.errors import AppError
    from elder_common.upstream import asr

    settings = get_settings()
    bad_url = f"{settings.asr_base_url}/test/upstream-asr-fail"
    original_url = settings.asr_base_url
    try:
        object.__setattr__(settings, "asr_base_url", bad_url)
        with pytest.raises(AppError) as exc_info:
            asr.recognize("x")
        assert exc_info.value.code == ErrorCode.UPSTREAM_ASR
    finally:
        object.__setattr__(settings, "asr_base_url", original_url)
