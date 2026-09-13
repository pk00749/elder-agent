"""anonymous-device 鉴权端点测试（v2.1.2 / §6.1 / §C）。"""

from __future__ import annotations


async def _init(account_client: object, device_token: str) -> dict[str, str]:
    r = await account_client.post(  # type: ignore[attr-defined]
        "/v1/auth/anonymous-device", json={"device_token": device_token}
    )
    assert r.status_code == 200, r.text
    return r.json()


async def test_first_init_returns_jwt_and_user_id(account_client: object) -> None:
    """首调 anonymous-device → 返回 JWT + family role。"""
    data = await _init(account_client, "device-uuid-aaaa-1111")
    assert data["role"] == "family"
    assert data["token"]
    assert data["user_id"]


async def test_same_device_token_reuses_user_id(account_client: object) -> None:
    """幂等：同 device_token 重调复用 user_id，仅签发新 JWT。"""
    first = await _init(account_client, "device-uuid-bbbb-2222")
    second = await _init(account_client, "device-uuid-bbbb-2222")
    assert first["user_id"] == second["user_id"]


async def test_missing_device_token_returns_422(account_client: object) -> None:
    """缺 device_token → 422 VALIDATION_ERROR。"""
    r = await account_client.post(  # type: ignore[attr-defined]
        "/v1/auth/anonymous-device", json={}
    )
    assert r.status_code == 422


async def test_short_device_token_returns_422(account_client: object) -> None:
    """device_token 长度 < 8 → 422。"""
    r = await account_client.post(  # type: ignore[attr-defined]
        "/v1/auth/anonymous-device", json={"device_token": "short"}
    )
    assert r.status_code == 422


async def test_invalid_chars_returns_422(account_client: object) -> None:
    r"""device_token 含非法字符 → 422（pattern 限制 [A-Za-z0-9\-_]）。"""
    r = await account_client.post(  # type: ignore[attr-defined]
        "/v1/auth/anonymous-device", json={"device_token": "device uuid with spaces"}
    )
    assert r.status_code == 422


async def test_sms_endpoints_removed(account_client: object) -> None:
    """MVP 阶段 sms-code 与 login 路由不应存在（404 / 405）。"""
    r1 = await account_client.post(  # type: ignore[attr-defined]
        "/v1/auth/sms-code", json={"phone": "13800138000"}
    )
    assert r1.status_code in (404, 405)
    r2 = await account_client.post(  # type: ignore[attr-defined]
        "/v1/auth/login", json={"phone": "13800138000", "code": "123456"}
    )
    assert r2.status_code in (404, 405)
