"""flush-pending 端到端测试（PRD §A.6 / §3.2.9 / §6.3）。"""
from __future__ import annotations

from uuid import UUID, uuid4

from elder_common.auth import Principal, issue_token
from elder_common.repos import elder_profile as ep_repo
from elder_common.schemas.elder_profile import ElderProfileCreate


async def _bootstrap_elder() -> tuple[Principal, UUID]:
    """建 elder + 返 elder principal（flush-pending 要求 elder 角色）。"""
    elder = await ep_repo.create(
        ElderProfileCreate(name="X", device_token="d-x", timezone="Asia/Shanghai")
    )
    principal = Principal(
        user_id=str(elder.id), role="elder", elder_id=str(elder.id)
    )
    return principal, elder.id


def _token(principal: Principal) -> str:
    return issue_token(principal.user_id, principal.role, elder_id=principal.elder_id)


async def test_flush_pending_sync(account_client: object) -> None:
    """§A.6：正常路径 —— pending_id → diary_entry 创建。"""
    principal, _elder_id = await _bootstrap_elder()
    pid = uuid4()
    body = {
        "pending_diaries": [
            {
                "pending_id": str(pid),
                "audio_cos_key": "elder/x/tmp/abc.m4a",
                "turns": [
                    {
                        "elder_text": "今天和老张下棋",
                        "elder_audio_cos_key": "elder/x/tmp/a.m4a",
                    },
                ],
                "text": "今天和老张下了棋，赢了",
                "summary": "和老张下棋，赢了",
            }
        ]
    }
    r = await account_client.post(  # type: ignore[attr-defined]
        "/v1/diary/flush-pending",
        json=body,
        headers={"Authorization": f"Bearer {_token(principal)}"},
    )
    assert r.status_code == 200, r.text
    out = r.json()
    assert len(out["synced"]) == 1
    assert out["synced"][0]["pending_id"] == str(pid)
    assert out["dropped"] == []


async def test_flush_pending_idempotent(account_client: object) -> None:
    """§A.6：同 pending_id 二次 flush → 返原 diary_id，不重写。"""
    principal, _elder_id = await _bootstrap_elder()
    pid = uuid4()
    body = {
        "pending_diaries": [
            {
                "pending_id": str(pid),
                "audio_cos_key": "elder/x/tmp/abc.m4a",
                "turns": [],
                "text": "和老张下棋",
                "summary": "下棋",
            }
        ]
    }
    headers = {"Authorization": f"Bearer {_token(principal)}"}
    r1 = await account_client.post(  # type: ignore[attr-defined]
        "/v1/diary/flush-pending", json=body, headers=headers
    )
    r2 = await account_client.post(  # type: ignore[attr-defined]
        "/v1/diary/flush-pending", json=body, headers=headers
    )
    assert r1.status_code == 200
    assert r2.status_code == 200
    d1 = r1.json()["synced"][0]["diary_id"]
    d2 = r2.json()["synced"][0]["diary_id"]
    assert d1 == d2


async def test_flush_pending_drop_expired_cos(account_client: object) -> None:
    """§11.13 / §A.6：audio_cos_key 24h 过期 → dropped（保留 text+summary）。"""
    principal, _elder_id = await _bootstrap_elder()
    pid = uuid4()
    body = {
        "pending_diaries": [
            {
                "pending_id": str(pid),
                "audio_cos_key": "elder/x/tmp/expired_abc.m4a",
                "turns": [],
                "text": "和老张下棋",
                "summary": "下棋",
            }
        ]
    }
    r = await account_client.post(  # type: ignore[attr-defined]
        "/v1/diary/flush-pending",
        json=body,
        headers={"Authorization": f"Bearer {_token(principal)}"},
    )
    assert r.status_code == 200
    out = r.json()
    assert len(out["dropped"]) == 1
    assert out["dropped"][0]["reason"] == "audio_cos_key expired (>24h)"
    assert len(out["synced"]) == 1
