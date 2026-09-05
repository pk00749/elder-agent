"""v2.1 schema 回归（AGENTS.md §19 / §A.7）—— 字段约束已被 unit tests 覆盖。

本文件保留为占位（防止 §A.4 / §A.7 回归时无 test 报警）；具体字段约束见
tests/test_schemas_v21.py。
"""
from __future__ import annotations


def test_schema_v21_covered_by_unit_tests() -> None:
    """占位：实际字段约束断言在 tests/test_schemas_v21.py。"""
    from tests.test_schemas_v21 import (
        test_medication_dosage_type_enum,
        test_medication_schedule_time_format,
        test_appointment_advance_remind_min_enum,
        test_appointment_time_past_rejected,
        test_reminder_channel_priority_auto,
    )

    # 调用一遍保证不被删除
    test_medication_dosage_type_enum()
    test_medication_schedule_time_format()
    test_appointment_advance_remind_min_enum()
    test_appointment_time_past_rejected()
    test_reminder_channel_priority_auto()
