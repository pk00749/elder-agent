# 业务事件名常量（AGENTS.md §7 末段）。
# 所有业务事件必须从 emit_event(name, ...) 调用，name 在此集中。
from __future__ import annotations

# ---- 鉴权 ----
AUTH_SMS_SENT = "auth.sms_sent"
AUTH_LOGIN = "auth.login"
AUTH_ANONYMOUS_INIT = "auth.anonymous_init"

# ---- 绑定 ----
BIND_ATTEMPT_CREATED = "bind.attempt_created"
BIND_CONFIRMED = "bind.confirmed"
BIND_REJECTED = "bind.rejected"
BIND_UNBOUND = "bind.unbound"

# ---- 提醒 ----
REMINDER_CREATED = "reminder.created"
REMINDER_UPDATED = "reminder.updated"
REMINDER_DELETED = "reminder.deleted"
REMINDER_ACKED = "reminder.acked"
REMINDER_MISSED = "reminder.missed"
REMINDER_PUSHED = "reminder.pushed"

# ---- Agent / 日记 ----
SESSION_STARTED = "diary.session_started"
SESSION_TURN = "diary.session_turn"
SESSION_FINALIZED = "diary.session_finalized"
SESSION_ABANDONED = "diary.session_abandoned"
DIARY_FLUSHED = "diary.flushed"

# ---- 上游 ----
UPSTREAM_CALL = "upstream.call"
UPSTREAM_ERROR = "upstream.error"

# ---- 推送 ----
TPUSH_SENT = "tpush.sent"
SMS_SENT = "sms.sent"
