"""TPush 调度 —— 计算下次推送时刻 + 调用 TPush（§11.18 / §A.1）。

PR 2 实现：
- medication：未来 7 天每个匹配日 / HH:mm 各发一次（mock 立即触发）
- appointment：datetime - advance_remind_min 触发（mock 立即触发）
- prod 实装：调 TPush REST push_date_time 字段做定时发送；本 PR 用 mock
"""

from __future__ import annotations

from datetime import datetime, timedelta
from typing import cast

from elder_common.errors import AppError
from elder_common.events import REMINDER_PUSHED
from elder_common.logging import emit_event
from elder_common.repos import reminder as reminder_repo
from elder_common.schemas.reminder import (
    AppointmentPayload,
    MedicationPayload,
    MedicationSchedule,
    Reminder,
    ReminderPayload,
    ReminderType,
    ScheduleRepeat,
)
from elder_common.time import now_utc
from elder_common.upstream import tpush
from elder_common.upstream.tpush import ChannelPriority as UpstreamChannelPriority


def _parse_hh_mm(s: str) -> tuple[int, int]:
    """'HH:mm' → (hour, minute)。"""
    parts = s.split(":")
    return int(parts[0]), int(parts[1])


def compute_next_push_times(reminder: Reminder, from_dt: datetime) -> list[datetime]:
    """§11.18：根据 reminder 类型计算下次推送时刻（PR 2：未来 7 天窗口）。"""
    out: list[datetime] = []
    payload: ReminderPayload = reminder.payload

    if reminder.type is ReminderType.APPOINTMENT:
        ap = cast(AppointmentPayload, payload)
        fire_at = ap.datetime - timedelta(minutes=ap.advance_remind_min)
        if fire_at > from_dt:
            out.append(fire_at)
        return out

    if reminder.type is ReminderType.MEDICATION:
        mp = cast(MedicationPayload, payload)
        for sched in mp.schedule:
            h, m = _parse_hh_mm(sched.time)
            for day_offset in range(7):
                base = from_dt.replace(hour=h, minute=m, second=0, microsecond=0)
                candidate = base + timedelta(days=day_offset)
                if candidate <= from_dt:
                    continue
                if _matches_weekday(sched, candidate):
                    out.append(candidate)
        return out

    return out


def _matches_weekday(sched: MedicationSchedule, candidate: datetime) -> bool:
    """§3.2.3 §E.3 决议 9：daily / weekdays(周一三五) / custom(手动指定)。"""
    wd = candidate.weekday()  # 周一=0，周日=6
    if sched.repeat is ScheduleRepeat.DAILY:
        return True
    if sched.repeat is ScheduleRepeat.WEEKDAYS:
        return wd in (0, 2, 4)  # 周一三五
    if sched.repeat is ScheduleRepeat.CUSTOM and sched.weekdays is not None:
        return wd in sched.weekdays
    return False


def render_push_content(reminder: Reminder) -> tuple[str, str]:
    """推送文案 —— mock 用纯文本；prod 可接 TTS 模板。"""
    payload: ReminderPayload = reminder.payload
    if reminder.type is ReminderType.APPOINTMENT:
        ap = cast(AppointmentPayload, payload)
        title = "就医提醒"
        body = f"{ap.hospital} {ap.department} {ap.datetime.strftime('%m-%d %H:%M')}"
        return title, body
    mp = cast(MedicationPayload, payload)
    title = "服药提醒"
    times = "、".join(s.time for s in mp.schedule)
    body = f"{mp.med_name}（{times}）"
    return title, body


async def enqueue_pushes(reminder: Reminder) -> int:
    """§11.18 / §A.1：调度 TPush 推送；返回成功发送条数。"""
    device_token = await reminder_repo.find_device_token(reminder.elder_id)
    if not device_token:
        return 0  # elder 没 device_token（旧版本 / 卸载）→ 不发推送
    times = compute_next_push_times(reminder, now_utc())
    title, body = render_push_content(reminder)
    sent = 0
    upstream_cp = (
        UpstreamChannelPriority.MID
        if reminder.channel_priority.value == "mid"
        else UpstreamChannelPriority.HIGH
    )
    for _t in times:
        # prod 实装：调 TPush REST push_date_time 做定时发送；PR 2 mock 立即发送
        try:
            tpush.send(
                device_token=device_token,
                title=title,
                body=body,
                channel_priority=upstream_cp,
                extra={"reminder_id": str(reminder.id), "type": reminder.type.value},
            )
        except AppError:
            continue  # §7 容错：单条失败不阻塞其他推送
        sent += 1
        emit_event(
            REMINDER_PUSHED,
            reminder_id=str(reminder.id),
            elder_id=str(reminder.elder_id),
            channel=reminder.channel_priority.value,
        )
    return sent
