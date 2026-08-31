"""v2.1 schema 约束单测（PRD §A.4 / §A.7）。"""
from __future__ import annotations

from datetime import timedelta

import pytest
from pydantic import ValidationError

from elder_common.schemas.reminder import (
    AppointmentPayload,
    ChannelPriority,
    MedicationDosage,
    MedicationPayload,
    MedicationSchedule,
    ReminderCreate,
    ScheduleRepeat,
)


def test_medication_dosage_type_enum() -> None:
    """§A.4：3 选 1；type='custom' 必须拒绝（v2.1 删除档）。"""
    MedicationDosage(type="pill")
    MedicationDosage(type="half")
    MedicationDosage(type="spoon")
    with pytest.raises(ValidationError):
        MedicationDosage(type="custom")  # type: ignore[arg-type]


def test_medication_schedule_time_format() -> None:
    """§5.4：HH:mm 格式。"""
    MedicationSchedule(time="09:30", repeat=ScheduleRepeat.DAILY)
    with pytest.raises(ValidationError):
        MedicationSchedule(time="9:30", repeat=ScheduleRepeat.DAILY)  # type: ignore[arg-type]
    with pytest.raises(ValidationError):
        MedicationSchedule(time="25:00", repeat=ScheduleRepeat.DAILY)  # type: ignore[arg-type]


def test_medication_schedule_custom_requires_weekdays() -> None:
    """§5.4 + §3.2.3：custom 档必须给 weekdays。"""
    with pytest.raises(ValidationError):
        MedicationSchedule(time="09:30", repeat=ScheduleRepeat.CUSTOM)
    MedicationSchedule(
        time="09:30", repeat=ScheduleRepeat.CUSTOM, weekdays=[0, 1, 2]
    )


def test_appointment_advance_remind_min_enum() -> None:
    """§A.4：30|60|120。"""
    from elder_common.time import now_utc

    future = now_utc() + timedelta(days=2)
    AppointmentPayload(
        hospital="华西医院", department="心内科", datetime=future, advance_remind_min=30
    )
    AppointmentPayload(
        hospital="华西医院", department="心内科", datetime=future, advance_remind_min=60
    )
    AppointmentPayload(
        hospital="华西医院", department="心内科", datetime=future, advance_remind_min=120
    )
    with pytest.raises(ValidationError):
        AppointmentPayload(
            hospital="华西医院",
            department="心内科",
            datetime=future,
            advance_remind_min=45,  # type: ignore[arg-type]
        )


def test_appointment_time_past_rejected() -> None:
    """§A.4 / §6.2 REMINDER_TIME_PAST：日期 + 提前 ≤ 现在 → 422。"""
    from elder_common.constants import ErrorCode
    from elder_common.errors import AppError
    from elder_common.time import now_utc

    past = now_utc() - timedelta(days=1)
    with pytest.raises(AppError) as exc_info:
        AppointmentPayload(
            hospital="华西医院", department="心内科", datetime=past, advance_remind_min=30
        )
    assert exc_info.value.code == ErrorCode.REMINDER_TIME_PAST
    assert exc_info.value.http_status == 422


def test_reminder_channel_priority_auto() -> None:
    """§A.4：channel_priority 由 type 自动设。"""

    from uuid import uuid4

    future = now_utc_safe()
    elder_id = uuid4()
    family_id = uuid4()
    med_create = ReminderCreate(
        elder_id=elder_id,
        type="medication",  # type: ignore[arg-type]
        payload=MedicationPayload(
            med_name="降压药",
            dosage=MedicationDosage(type="pill"),
            schedule=[MedicationSchedule(time="09:00", repeat=ScheduleRepeat.DAILY)],
        ),
        created_by=family_id,
    )
    assert med_create.channel_priority is ChannelPriority.MID

    appt_create = ReminderCreate(
        elder_id=elder_id,
        type="appointment",  # type: ignore[arg-type]
        payload=AppointmentPayload(
            hospital="华西医院", department="心内科", datetime=future, advance_remind_min=60
        ),
        created_by=family_id,
    )
    assert appt_create.channel_priority is ChannelPriority.HIGH


def test_reminder_type_payload_match() -> None:
    """§A.4：type 与 payload 类型必须配套。"""
    from uuid import uuid4

    from elder_common.time import now_utc

    future = now_utc() + timedelta(days=2)
    elder_id = uuid4()
    family_id = uuid4()
    # type=medication + appointment payload → 拒绝
    with pytest.raises(ValidationError):
        ReminderCreate(
            elder_id=elder_id,
            type="medication",  # type: ignore[arg-type]
            payload=AppointmentPayload(
                hospital="华西医院",
                department="心内科",
                datetime=future,
                advance_remind_min=60,
            ),
            created_by=family_id,
        )


def now_utc_safe():
    """兼容 Pydantic datetime 校验的未来时间。"""
    from elder_common.time import now_utc
    return now_utc() + timedelta(days=2)
