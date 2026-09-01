# mypy: disable-error-code="explicit-any"
# 原因：Pydantic BaseModel 父类签名含 Any
"""diary_session —— Agent 访谈会话（PRD §5.6 / §3.1.2）。"""
from __future__ import annotations

from datetime import datetime
from enum import Enum
from uuid import UUID, uuid4

from pydantic import BaseModel, ConfigDict, Field


class SessionStatus(str, Enum):
    """§5.6：active / finalized / abandoned。

    状态变更只通过 state.transition()（AGENTS.md §11 红线）。
    """

    ACTIVE = "active"
    FINALIZED = "finalized"
    ABANDONED = "abandoned"


class Turn(BaseModel):
    """§5.6 Turn 内嵌结构 —— 一轮对话。

    turn_no 是 1-based；elder_text 由 ASR 转写；assistant_text 由 DSH 返回。
    """

    model_config = ConfigDict(extra="forbid")

    turn_no: int = Field(ge=1)
    elder_text: str = Field(min_length=1, max_length=500)
    elder_audio_cos_key: str = Field(min_length=1, max_length=256)
    assistant_text: str = Field(min_length=1, max_length=100)
    assistant_audio_cos_key: str = Field(min_length=1, max_length=256)


class SessionCreate(BaseModel):
    """POST /v1/agent/diary-session 入参。"""

    model_config = ConfigDict(extra="forbid")

    elder_id: UUID


class Session(BaseModel):
    """持久化形态。"""

    model_config = ConfigDict(extra="forbid")

    id: UUID = Field(default_factory=uuid4)
    elder_id: UUID
    status: SessionStatus = SessionStatus.ACTIVE
    turns: list[Turn] = Field(default_factory=list)
    created_at: datetime
    updated_at: datetime
