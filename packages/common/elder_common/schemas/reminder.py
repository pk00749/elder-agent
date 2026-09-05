# mypy: disable-error-code="explicit-any"
# 原因：Pydantic BaseModel 父类签名含 Any，与 §5 禁 Any 冲突（BaseSettings 同款）
"""reminder —— 提醒（PRD §5.4 + §A.4 v2.1 收紧）。"""

from __future__ import annotations

from datetime import datetime, timedelta
from enum import Enum
from typing import Literal
from uuid import UUID, uuid4

from pydantic import BaseModel, ConfigDict, Field, model_validator

from elder_common.constants import ErrorCode
from elder_common.errors import AppError
from elder_common.time import now_utc


class ReminderType(str, Enum):
    """§5.4 / §A.4：medication / appointment；v2.1 删 custom。"""

    MEDICATION = "medication"
    APPOINTMENT = "appointment"


class ReminderStatus(str, Enum):
    """§5.4。"""

    ACTIVE = "active"
    PAUSED = "paused"


class ChannelPriority(str, Enum):
    """§11.18 双通道 —— mid=服药，high=就医。"""

    MID = "mid"
    HIGH = "high"


class MedicationDosage(BaseModel):
    """§3.2.3 §I-4.C 决议：3 选 1 + 备注；删 custom 档。"""

    model_config = ConfigDict(extra="forbid")

    type: Literal["pill", "half", "spoon"]
    note: str | None = Field(default=None, max_length=100)


class ScheduleRepeat(str, Enum):
    """§3.2.3 §E.3 决议 9 —— 3 档。"""

    DAILY = "daily"
    WEEKDAYS = "weekdays"  # 周一三五
    CUSTOM = "custom"  # §3.2.3 §E.3：周二四六（手动指定 weekdays）


Weekdays = Literal[0, 1, 2, 3, 4, 5, 6]  # 周一=0，周日=6


class MedicationSchedule(BaseModel):
    """§3.2.3 §E.3 决议 9 —— 每天/周一三五/周二四六 3 档。"""

    model_config = ConfigDict(extra="forbid")

    time: str = Field(min_length=5, max_length=5, pattern=r"^([01]\d|2[0-3]):[0-5]\d$")
    repeat: ScheduleRepeat
    weekdays: list[Weekdays] | None = None

    @model_validator(mode="after")
    def check_custom_weekdays(self) -> MedicationSchedule:
        if self.repeat is ScheduleRepeat.CUSTOM and not self.weekdays:
            raise ValueError("custom repeat requires weekdays")
        if self.repeat is not ScheduleRepeat.CUSTOM and self.weekdays is not None:
            raise ValueError("weekdays only allowed for repeat=custom")
        return self


class MedicationPayload(BaseModel):
    """§5.4 medication payload。"""

    model_config = ConfigDict(extra="forbid")

    med_name: str = Field(min_length=1, max_length=20)
    dosage: MedicationDosage
    schedule: list[MedicationSchedule] = Field(min_length=1)
    note: str | None = Field(default=None, max_length=100)


class AppointmentPayload(BaseModel):
    """§5.4 + §3.2.4 + §A.4 + §E.4 决议 13/14。"""

    model_config = ConfigDict(extra="forbid")

    hospital: str = Field(min_length=1, max_length=20)
    department: str = Field(min_length=1, max_length=20)
    datetime: datetime
    advance_remind_min: Literal[30, 60, 120] = 60
    repeat: Literal["none"] | None = None  # §K.3d UI 不录入
    note: str | None = Field(default=None, max_length=100)

    @model_validator(mode="after")
    def check_advance(self) -> AppointmentPayload:
        # §3.2.4 §E.4 决议 14：日期 + 提前 > 现在
        if self.datetime - timedelta(minutes=self.advance_remind_min) <= now_utc():
            raise AppError(
                code=ErrorCode.REMINDER_TIME_PAST,
                message="reminder_time_past",
                http_status=422,
            )
        return self


ReminderPayload = MedicationPayload | AppointmentPayload


class ReminderBase(BaseModel):
    """§5.4 公共字段。"""

    model_config = ConfigDict(extra="forbid")

    elder_id: UUID
    type: ReminderType
    payload: ReminderPayload
    status: ReminderStatus = ReminderStatus.ACTIVE
    created_by: UUID


class ReminderCreate(ReminderBase):
    """创建入参 —— 服务端补 id + channel_priority + 时间戳。"""

    channel_priority: ChannelPriority | None = None

    @model_validator(mode="after")
    def auto_channel_priority(self) -> ReminderCreate:
        # §A.4：channel_priority 由服务端按 type 自动设
        if self.type is ReminderType.MEDICATION:
            self.channel_priority = ChannelPriority.MID
        else:
            self.channel_priority = ChannelPriority.HIGH
        # payload 类型必须匹配 type
        is_med = isinstance(self.payload, MedicationPayload)
        if self.type is ReminderType.MEDICATION and not is_med:
            raise ValueError("medication type requires MedicationPayload")
        if self.type is ReminderType.APPOINTMENT and is_med:
            raise ValueError("appointment type requires AppointmentPayload")
        return self


class Reminder(ReminderBase):
    """持久化形态。"""

    model_config = ConfigDict(extra="forbid")

    id: UUID = Field(default_factory=uuid4)
    channel_priority: ChannelPriority
    created_at: datetime
    updated_at: datetime


class ReminderPatch(BaseModel):
    """PATCH 入参 —— 全部可选。"""

    model_config = ConfigDict(extra="forbid")

    payload: ReminderPayload | None = None
    status: ReminderStatus | None = None
