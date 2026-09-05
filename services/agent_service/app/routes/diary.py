# mypy: disable-error-code="explicit-any"
# 原因：Pydantic BaseModel 父类签名含 Any
"""Agent 访谈路由 —— /v1/agent/diary-session*（PRD §3.1.2 / §6.1 / §6.3）。"""

from __future__ import annotations

from uuid import UUID

from elder_common.auth import Principal, require_role
from fastapi import APIRouter, Depends
from pydantic import BaseModel, ConfigDict, Field
from services.agent_service.app.agent.services import diary

router = APIRouter(prefix="/v1/agent/diary-session", tags=["agent-diary"])


class StartRequest(BaseModel):
    """POST /v1/agent/diary-session 入参（§6.3）。"""

    model_config = ConfigDict(extra="forbid")

    elder_id: UUID


class TurnRequest(BaseModel):
    """POST /v1/agent/diary-session/:id/turn 入参（§6.3）。"""

    model_config = ConfigDict(extra="forbid")

    turn_no: int = Field(ge=1)
    elder_text: str = Field(min_length=1, max_length=500)
    elder_audio_cos_key: str = Field(min_length=1, max_length=256)


class FinalizeRequest(BaseModel):
    """POST /v1/agent/diary-session/:id/finalize 入参（§6.3）。"""

    model_config = ConfigDict(extra="forbid")

    turn_no: int = Field(ge=1)
    elder_text: str = Field(min_length=1, max_length=500)
    elder_audio_cos_key: str = Field(min_length=1, max_length=256)


@router.post("")
async def start_session(
    body: StartRequest,
    principal: Principal = Depends(require_role("elder")),
) -> dict[str, object]:
    """§3.1.2 / §6.3：建访谈会话 + 返 greeting。"""
    # body.elder_id 必须匹配 principal.elder_id（PRD §10 仓储层二次校验走 §11）
    if str(body.elder_id) != principal.elder_id:
        from elder_common.constants import ErrorCode
        from elder_common.errors import AppError

        raise AppError(
            code=ErrorCode.FORBIDDEN,
            message="elder_id mismatch",
            http_status=403,
        )
    return await diary.start_session(principal)


@router.post("/{session_id}/turn")
async def handle_turn(
    session_id: str,
    body: TurnRequest,
    principal: Principal = Depends(require_role("elder")),
) -> dict[str, object]:
    """§3.1.2 / §6.3：处理一轮 —— 返 reply + audio_url + should_finalize + turns_left。"""
    return await diary.handle_turn(
        UUID(session_id),
        body.turn_no,
        body.elder_text,
        body.elder_audio_cos_key,
        principal,
    )


@router.post("/{session_id}/finalize")
async def finalize_session(
    session_id: str,
    body: FinalizeRequest,
    principal: Principal = Depends(require_role("elder")),
) -> dict[str, object]:
    """§3.1.6 / §6.3：finalize —— 整理日记 + 落库。"""
    return await diary.finalize_session(
        UUID(session_id),
        body.turn_no,
        body.elder_text,
        body.elder_audio_cos_key,
        principal,
    )
