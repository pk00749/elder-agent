# mypy: disable-error-code="explicit-any"
# 原因：Pydantic BaseModel 父类签名含 Any
"""diary_entry —— 日志（PRD §5.7 / §3.1.6 / §A.6）。"""
from __future__ import annotations

from datetime import datetime
from uuid import UUID, uuid4

from pydantic import BaseModel, ConfigDict, Field


class AudioSegment(BaseModel):
    """§5.7 AudioSegment —— 每轮语音。"""

    model_config = ConfigDict(extra="forbid")

    cos_key: str = Field(min_length=1, max_length=256)
    asr_text: str = Field(min_length=1, max_length=500)
    duration_ms: int = Field(ge=0)


class DiaryEntryCreate(BaseModel):
    """§3.1.6 finalize 入参 —— 服务端补 id + 时间戳。"""

    model_config = ConfigDict(extra="forbid")

    elder_id: UUID
    session_id: UUID | None = None
    date: str = Field(min_length=10, max_length=10, pattern=r"^\d{4}-\d{2}-\d{2}$")
    text: str = Field(min_length=1, max_length=100)  # §3.1.4.D1
    summary: str = Field(min_length=1, max_length=60)  # §3.1.4.D2
    audio_segments: list[AudioSegment] = Field(min_length=1)  # §3.1.4.D3
    pending_id: UUID | None = None  # §A.6 flush-pending 幂等去重


class DiaryEntry(BaseModel):
    """持久化形态。"""

    model_config = ConfigDict(extra="ignore")

    id: UUID = Field(default_factory=uuid4)
    elder_id: UUID
    session_id: UUID | None = None
    date: str
    text: str
    summary: str
    audio_segments: list[AudioSegment]
    created_at: datetime
    pending_id: UUID | None = None  # §A.6：flush-pending 幂等去重（持久化字段）
