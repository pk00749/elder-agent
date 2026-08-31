# v2.1 schema 约束回归测试（AGENTS.md §A.4 / §A.7）。
# PR 1 占位：等 Pydantic schema 模型在 PR 2 落地后补充字段断言。
from __future__ import annotations
import pytest


def test_schema_v21_placeholder() -> None:
    """PR 1 占位。PR 2 起补字段约束断言：
    - MedicationDosage.type ∈ [pill, half, spoon]（§A.4）
    - Reminder.channel_priority ∈ [mid, high]（§A.4）
    - AppointmentPayload.advance_remind_min ∈ [30, 60, 120]（§A.4）
    - AppointmentPayload.repeat ∈ [none] | null（§A.4）
    - bind_attempt 数据模型字段约束（§5.9）
    """
    pytest.skip("PR 2 起补 schema 字段约束测试")
