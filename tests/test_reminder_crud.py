"""reminder_service 端到端测试（PRD §3.2.3 / §3.2.4 / §6.1 / §A.4 / §11.18）。"""
from __future__ import annotations

from datetime import UTC, datetime, timedelta

from elder_common.auth import issue_token
from elder_common.repos import binding as b_repo
from elder_common.repos import elder_profile as ep_repo
from elder_common.repos import family_user as fu_repo
from elder_common.schemas.binding import BindingCreate, BindingRole
from elder_common.schemas.elder_profile import ElderProfileCreate
from elder_common.schemas.family_user import FamilyUserCreateMvp


async def _new_family_with_elder() -> tuple[str, str, str]:
    """创建 family + elder + binding —— 返回 (family_token, elder_token, elder_id)。"""
    fam = await fu_repo.create_mvp(
        FamilyUserCreateMvp(phone="", device_token="fam-device-X-test-only-1234")
    )
    fam_token = issue_token(str(fam.id), "family")

    elder = await ep_repo.create(
        ElderProfileCreate(
            name="测试老人", device_token="elder-device-X", timezone="Asia/Shanghai"
        )
    )
    await b_repo.create(
        BindingCreate(family_id=fam.id, elder_id=elder.id, role=BindingRole.PRIMARY)
    )
    elder_token = issue_token(str(elder.id), "elder", elder_id=str(elder.id))

    return fam_token, elder_token, str(elder.id)


async def test_create_medication_reminder(reminder_client: object) -> None:
    """§A.4 + §11.18：medication → channel_priority 自动设为 mid。"""
    fam_token, _elder_token, elder_id = await _new_family_with_elder()
    body = {
        "elder_id": elder_id,
        "type": "medication",
        "payload": {
            "med_name": "降压药",
            "dosage": {"type": "pill"},
            "schedule": [{"time": "09:00", "repeat": "daily"}],
        },
    }
    r = await reminder_client.post(  # type: ignore[attr-defined]
        "/v1/reminders",
        json=body,
        headers={"Authorization": f"Bearer {fam_token}"},
    )
    assert r.status_code == 200, r.text
    out = r.json()
    assert out["channel_priority"] == "mid"
    assert out["type"] == "medication"
    assert out["id"]


async def test_create_appointment_reminder(reminder_client: object) -> None:
    """§A.4 + §11.18：appointment → channel_priority 自动设为 high。"""
    fam_token, _elder_token, elder_id = await _new_family_with_elder()
    future_dt = (datetime.now(tz=UTC) + timedelta(days=2)).isoformat()
    body = {
        "elder_id": elder_id,
        "type": "appointment",
        "payload": {
            "hospital": "华西医院",
            "department": "心内科",
            "datetime": future_dt,
            "advance_remind_min": 60,
            "repeat": "none",
        },
    }
    r = await reminder_client.post(  # type: ignore[attr-defined]
        "/v1/reminders",
        json=body,
        headers={"Authorization": f"Bearer {fam_token}"},
    )
    assert r.status_code == 200, r.text
    out = r.json()
    assert out["channel_priority"] == "high"
    assert out["type"] == "appointment"


async def test_create_appointment_time_past(reminder_client: object) -> None:
    """§A.4 + §6.2：appointment datetime + advance ≤ 现在 → 422 REMINDER_TIME_PAST。"""
    fam_token, _elder_token, elder_id = await _new_family_with_elder()
    past_dt = (datetime.now(tz=UTC) - timedelta(days=2)).isoformat()
    body = {
        "elder_id": elder_id,
        "type": "appointment",
        "payload": {
            "hospital": "华西医院",
            "department": "心内科",
            "datetime": past_dt,
            "advance_remind_min": 60,
            "repeat": "none",
        },
    }
    r = await reminder_client.post(  # type: ignore[attr-defined]
        "/v1/reminders",
        json=body,
        headers={"Authorization": f"Bearer {fam_token}"},
    )
    assert r.status_code == 422
    assert r.json()["code"] == "REMINDER_TIME_PAST"


async def test_list_reminders_for_family(reminder_client: object) -> None:
    """§6.1 GET /v1/reminders —— family 视角返回 created_by=自己。"""
    fam_token, _elder_token, elder_id = await _new_family_with_elder()
    body = {
        "elder_id": elder_id,
        "type": "medication",
        "payload": {
            "med_name": "维生素",
            "dosage": {"type": "spoon"},
            "schedule": [{"time": "08:00", "repeat": "daily"}],
        },
    }
    await reminder_client.post(  # type: ignore[attr-defined]
        "/v1/reminders",
        json=body,
        headers={"Authorization": f"Bearer {fam_token}"},
    )
    r = await reminder_client.get(  # type: ignore[attr-defined]
        "/v1/reminders", headers={"Authorization": f"Bearer {fam_token}"}
    )
    assert r.status_code == 200
    items = r.json()["reminders"]
    assert len(items) == 1


async def test_ack_reminder(reminder_client: object) -> None:
    """§6.1 POST /v1/reminders/{id}/ack —— elder 应答。"""
    fam_token, elder_token, elder_id = await _new_family_with_elder()
    body = {
        "elder_id": elder_id,
        "type": "medication",
        "payload": {
            "med_name": "钙片",
            "dosage": {"type": "half"},
            "schedule": [{"time": "12:00", "repeat": "daily"}],
        },
    }
    r = await reminder_client.post(  # type: ignore[attr-defined]
        "/v1/reminders",
        json=body,
        headers={"Authorization": f"Bearer {fam_token}"},
    )
    assert r.status_code == 200, r.text
    rid = r.json()["id"]
    r = await reminder_client.post(  # type: ignore[attr-defined]
        f"/v1/reminders/{rid}/ack",
        json={"action": "taken"},
        headers={"Authorization": f"Bearer {elder_token}"},
    )
    assert r.status_code == 200, r.text
    assert r.json()["action"] == "taken"


async def test_family_only_create(reminder_client: object) -> None:
    """§6.1 / §9：elder 角色调 create → 403。"""
    _fam_token, elder_token, _ = await _new_family_with_elder()
    r = await reminder_client.post(  # type: ignore[attr-defined]
        "/v1/reminders",
        json={
            "elder_id": "00000000-0000-0000-0000-000000000000",
            "type": "medication",
            "payload": {
                "med_name": "X",
                "dosage": {"type": "pill"},
                "schedule": [{"time": "09:00", "repeat": "daily"}],
            },
        },
        headers={"Authorization": f"Bearer {elder_token}"},
    )
    assert r.status_code == 403


async def test_missing_jwt(reminder_client: object) -> None:
    """§7.4：缺 Authorization → 401。"""
    r = await reminder_client.get("/v1/reminders")  # type: ignore[attr-defined]
    assert r.status_code == 401
