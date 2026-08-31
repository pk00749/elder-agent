"""索引清单 —— 显式定义，部署时由 scripts/create_indexes.py 调 ensure_indexes() 落库。

参考 PRD §5 各集合索引建议。
"""

from __future__ import annotations

from elder_common.storage.base import CollectionName, IndexSpec

# ---- family_user (§5.1) ----
# phone 唯一
FAMILY_USER_INDEXES: list[IndexSpec] = [
    IndexSpec(("phone",), unique=True),
]

# ---- elder_profile (§5.2) ----
# device_token 唯一
ELDER_PROFILE_INDEXES: list[IndexSpec] = [
    IndexSpec(("device_token",), unique=True),
]

# ---- binding (§5.3) ----
# (family_id, elder_id) 唯一
BINDING_INDEXES: list[IndexSpec] = [
    IndexSpec(("family_id", "elder_id"), unique=True),
    IndexSpec(("elder_id", "role")),
]

# ---- bind_attempt (§5.9) ----
# bind_code 唯一；支持老人端轮询
BIND_ATTEMPT_INDEXES: list[IndexSpec] = [
    IndexSpec(("bind_code",), unique=True),
    IndexSpec(("elder_device_token", "status", "created_at")),
]

# ---- reminder (§5.4) ----
REMINDER_INDEXES: list[IndexSpec] = [
    IndexSpec(("elder_id", "type", "status", "channel_priority")),
    IndexSpec(("created_by",)),
]

# ---- reminder_ack (§5.5) ----
REMINDER_ACK_INDEXES: list[IndexSpec] = [
    IndexSpec(("reminder_id", "at")),
    IndexSpec(("elder_id", "at")),
]

# ---- diary_session (§5.6) ----（PR 3 起补）
DIARY_SESSION_INDEXES: list[IndexSpec] = [
    IndexSpec(("elder_id", "status", "created_at")),
]

# ---- diary_entry (§5.7) ----（PR 3 起补）
DIARY_ENTRY_INDEXES: list[IndexSpec] = [
    IndexSpec(("elder_id", "date", "created_at")),
]


ALL_INDEXES: dict[CollectionName, list[IndexSpec]] = {
    CollectionName.FAMILY_USERS: FAMILY_USER_INDEXES,
    CollectionName.ELDER_PROFILES: ELDER_PROFILE_INDEXES,
    CollectionName.BINDINGS: BINDING_INDEXES,
    CollectionName.BIND_ATTEMPTS: BIND_ATTEMPT_INDEXES,
    CollectionName.REMINDERS: REMINDER_INDEXES,
    CollectionName.REMINDER_ACKS: REMINDER_ACK_INDEXES,
    CollectionName.DIARY_SESSIONS: DIARY_SESSION_INDEXES,
    CollectionName.DIARY_ENTRIES: DIARY_ENTRY_INDEXES,
}
