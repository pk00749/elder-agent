"""绑定流端到端测试（PRD §3.2.7 / §3.2.8 / §A.5）。"""
from __future__ import annotations

from uuid import UUID

from elder_common.auth import issue_token
from elder_common.repos import bind_attempt as ba_repo
from elder_common.repos import binding as b_repo
from elder_common.repos import elder_profile as ep_repo


async def _bind_setup(account_client: object) -> tuple[str, str]:
    """走完 elder 创建 bind_code + family 扫码。

    v2.1.2：family 端走 anonymous-device 拿 JWT，不再走 SMS。

    返回 (elder_device_token, family_token, family_user_id)。
    """
    device = "elder-token-A"
    # 1. elder 端签 bind_code
    r = await account_client.post(  # type: ignore[attr-defined]
        "/v1/bind/code", json={"elder_device_token": device}
    )
    assert r.status_code == 200, r.text
    bind_code = r.json()["bind_code"]

    # 2. family anonymous-device 拿 token
    r = await account_client.post(  # type: ignore[attr-defined]
        "/v1/auth/anonymous-device", json={"device_token": "fam-device-aaaa-1111"}
    )
    assert r.status_code == 200, r.text
    family_token = r.json()["token"]
    family_user_id = r.json()["user_id"]

    # 3. family 扫码 → 服务端生成新的 bind_attempt
    r = await account_client.post(  # type: ignore[attr-defined]
        "/v1/bind/elder",
        json={"bind_code": bind_code},
        headers={"Authorization": f"Bearer {family_token}"},
    )
    assert r.status_code == 200, r.text
    return device, family_token, family_user_id


def _sms_store() -> object:
    """MVP-DEFER（v2.1.2）：保留函数名但返回空 —— 历史调用方已切 anonymous-device。"""
    class _Empty:
        def dev_peek(self, _: str) -> str | None:
            return None
    return _Empty()


async def test_bind_full_flow_accept(account_client: object) -> None:
    """§3.2.8 accept：elder_profile + binding(PRIMARY) 创建；返回 elder JWT。"""
    device, _fam_token, _fam_uid = await _bind_setup(account_client)
    # elder 端 /v1/bind/pending 拿队列
    r = await account_client.get(  # type: ignore[attr-defined]
        "/v1/bind/pending", headers={"X-Elder-Device-Token": device}
    )
    assert r.status_code == 200
    pending = r.json()["pending"]
    assert len(pending) == 1
    family_bind_code = pending[0]["bind_code"]

    # elder 点同意
    r = await account_client.post(  # type: ignore[attr-defined]
        "/v1/bind/confirm",
        headers={"X-Elder-Device-Token": device},
        json={
            "bind_code": family_bind_code,
            "elder_name": "王秀英",
            "decision": "accept",
        },
    )
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["role"] == "primary"
    elder_id = UUID(body["elder_id"])
    binding_id = UUID(body["binding_id"])

    # 验证落库
    elder = await ep_repo.get_by_id(str(elder_id))
    assert elder is not None
    assert elder.name == "王秀英"
    bindings = await b_repo.list_by_elder(elder_id)
    assert len(bindings) == 1
    assert str(bindings[0].id) == body["binding_id"]


async def test_bind_full_flow_reject(account_client: object) -> None:
    """§3.2.8 reject：bind_attempt 标 rejected；不创建 elder_profile / binding。"""
    device, _fam_token, _fam_uid = await _bind_setup(account_client)
    r = await account_client.get(  # type: ignore[attr-defined]
        "/v1/bind/pending", headers={"X-Elder-Device-Token": device}
    )
    pending_code = r.json()["pending"][0]["bind_code"]

    r = await account_client.post(  # type: ignore[attr-defined]
        "/v1/bind/confirm",
        headers={"X-Elder-Device-Token": device},
        json={
            "bind_code": pending_code,
            "elder_name": "王秀英",
            "decision": "reject",
        },
    )
    assert r.status_code == 204
    # bind_attempt 标 rejected
    attempt = await ba_repo.find_by_bind_code(pending_code)
    assert attempt is not None
    assert attempt.status.value == "rejected"


async def test_bind_primary_then_secondary(account_client: object) -> None:
    """§H.25：第一个 accept → primary；后续 → secondary（仅不同家庭）。"""
    # Family 1 + Family 2 各扫一次，elder 依次确认
    device = "elder-token-B"
    bind_codes: list[str] = []
    for i in range(2):
        r = await account_client.post(  # type: ignore[attr-defined]
            "/v1/bind/code", json={"elder_device_token": device}
        )
        bind_codes.append(r.json()["bind_code"])

    family_tokens: list[str] = []
    family_device_tokens = [
        "fam-device-cccc-2222",
        "fam-device-dddd-3333",
    ]
    for dev_token in family_device_tokens:
        r = await account_client.post(  # type: ignore[attr-defined]
            "/v1/auth/anonymous-device", json={"device_token": dev_token}
        )
        family_tokens.append(r.json()["token"])

    family_bind_codes: list[str] = []
    for token, elder_code in zip(family_tokens, bind_codes, strict=False):
        r = await account_client.post(  # type: ignore[attr-defined]
            "/v1/bind/elder",
            json={"bind_code": elder_code},
            headers={"Authorization": f"Bearer {token}"},
        )
        family_bind_codes.append(r.json()["bind_code"])

    # elder 端逐个同意
    roles: list[str] = []
    for fbc in family_bind_codes:
        r = await account_client.post(  # type: ignore[attr-defined]
            "/v1/bind/confirm",
            headers={"X-Elder-Device-Token": device},
            json={
                "bind_code": fbc,
                "elder_name": "王秀英",
                "decision": "accept",
            },
        )
        assert r.status_code == 200
        roles.append(r.json()["role"])
    assert roles == ["primary", "secondary"]


async def test_bind_confirm_wrong_device(account_client: object) -> None:
    """§3.2.8：confirm 时 X-Elder-Device-Token 与 bind_attempt 不符 → 403。"""
    device, _, _ = await _bind_setup(account_client)
    r = await account_client.get(  # type: ignore[attr-defined]
        "/v1/bind/pending", headers={"X-Elder-Device-Token": device}
    )
    pending_code = r.json()["pending"][0]["bind_code"]

    r = await account_client.post(  # type: ignore[attr-defined]
        "/v1/bind/confirm",
        headers={"X-Elder-Device-Token": "WRONG-DEVICE"},
        json={
            "bind_code": pending_code,
            "elder_name": "王秀英",
            "decision": "accept",
        },
    )
    assert r.status_code == 403


async def test_auth_missing_jwt(account_client: object) -> None:
    """§7.4：缺 Authorization header → 401。"""
    r = await account_client.post(  # type: ignore[attr-defined]
        "/v1/bind/elder", json={"bind_code": "X"}
    )
    assert r.status_code == 401


async def test_auth_wrong_role(account_client: object) -> None:
    """§9：elder 角色调 family-only 端点 → 403。"""
    token = issue_token("elder-uuid-1", "elder", elder_id="elder-uuid-1")
    r = await account_client.post(  # type: ignore[attr-defined]
        "/v1/bind/elder",
        json={"bind_code": "X"},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert r.status_code == 403
