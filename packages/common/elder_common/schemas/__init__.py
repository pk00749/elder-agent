"""Pydantic 模型 —— 严格按 PRD §5 字段定义（AGENTS.md §2 / §3 / §A.4 / §A.7）。"""

from __future__ import annotations

from elder_common.schemas.bind_attempt import (
    BindAttempt,
    BindAttemptCreate,
    BindAttemptStatus,
)
from elder_common.schemas.binding import Binding, BindingCreate, BindingRole
from elder_common.schemas.diary_entry import (
    AudioSegment,
    DiaryEntry,
    DiaryEntryCreate,
)
from elder_common.schemas.diary_session import (
    Session,
    SessionCreate,
    SessionStatus,
    Turn,
)
from elder_common.schemas.elder_profile import ElderProfile, ElderProfileCreate
from elder_common.schemas.family_user import FamilyUser, FamilyUserCreate
from elder_common.schemas.reminder import (
    AppointmentPayload,
    MedicationDosage,
    MedicationSchedule,
    Reminder,
    ReminderCreate,
    ReminderPayload,
    ReminderStatus,
    ReminderType,
    ScheduleRepeat,
    Weekdays,
)
from elder_common.schemas.reminder_ack import (
    AckAction,
    ReminderAck,
    ReminderAckCreate,
)

__all__ = [
    "AckAction",
    "AppointmentPayload",
    "AudioSegment",
    "BindAttempt",
    "BindAttemptCreate",
    "BindAttemptStatus",
    "Binding",
    "BindingCreate",
    "BindingRole",
    "DiaryEntry",
    "DiaryEntryCreate",
    "ElderProfile",
    "ElderProfileCreate",
    "FamilyUser",
    "FamilyUserCreate",
    "MedicationDosage",
    "MedicationSchedule",
    "Reminder",
    "ReminderAck",
    "ReminderAckCreate",
    "ReminderCreate",
    "ReminderPayload",
    "ReminderStatus",
    "ReminderType",
    "ScheduleRepeat",
    "Session",
    "SessionCreate",
    "SessionStatus",
    "Turn",
    "Weekdays",
]
