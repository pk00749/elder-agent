# CloudBase NoSQL 索引创建脚本（AGENTS.md §10 / §19）。
# PR 1 占位：仅打印 manifest；PR 2 起接真实 CloudBase SDK 调用。
#
# 用法：uv run python scripts/create_indexes.py [--apply]
from __future__ import annotations

import argparse
import sys

# 对应 PRD §5 数据模型集合名（复数 snake_case）
INDEXES = [
    {
        "collection": "family_users",
        "indexes": [
            {"name": "phone_unique", "keys": [("phone", 1)], "unique": True},
        ],
    },
    {
        "collection": "elder_profiles",
        "indexes": [
            {"name": "device_token_unique", "keys": [("device_token", 1)], "unique": True, "sparse": True},
        ],
    },
    {
        "collection": "bindings",
        "indexes": [
            {"name": "family_elder_unique", "keys": [("family_id", 1), ("elder_id", 1)], "unique": True},
            {"name": "elder_role", "keys": [("elder_id", 1), ("role", 1)]},
        ],
    },
    {
        "collection": "bind_attempts",
        "indexes": [
            {"name": "bind_code_unique", "keys": [("bind_code", 1)], "unique": True},
            {"name": "elder_status", "keys": [("elder_device_token", 1), ("status", 1), ("created_at", -1)]},
        ],
    },
    {
        "collection": "reminders",
        "indexes": [
            {"name": "elder_type_status_priority", "keys": [("elder_id", 1), ("type", 1), ("status", 1), ("channel_priority", 1)]},
        ],
    },
    {
        "collection": "reminder_acks",
        "indexes": [
            {"name": "reminder_at", "keys": [("reminder_id", 1), ("at", -1)]},
        ],
    },
    {
        "collection": "diary_sessions",
        "indexes": [
            {"name": "elder_status_created", "keys": [("elder_id", 1), ("status", 1), ("created_at", -1)]},
        ],
    },
    {
        "collection": "diary_entries",
        "indexes": [
            {"name": "elder_date_created", "keys": [("elder_id", 1), ("date", -1), ("created_at", -1)]},
        ],
    },
]


def main() -> int:
    parser = argparse.ArgumentParser(prog="create_indexes.py")
    parser.add_argument("--apply", action="store_true", help="实际下发（PR 2 起接 SDK）")
    args = parser.parse_args()

    if args.apply:
        print("[create_indexes] --apply: PR 1 stub，不实际下发（PR 2 起接 CloudBase SDK）")
        return 0

    print("[create_indexes] manifest:")
    for entry in INDEXES:
        print(f"  {entry['collection']}:")
        for idx in entry["indexes"]:
            keys = ", ".join(f"{k}: {v}" for k, v in idx["keys"])
            flags = []
            if idx.get("unique"):
                flags.append("UNIQUE")
            if idx.get("sparse"):
                flags.append("SPARSE")
            flag_str = f" [{', '.join(flags)}]" if flags else ""
            print(f"    - {idx['name']}: {keys}{flag_str}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
