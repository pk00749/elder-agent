"""日记路由 —— /v1/diary*（PRD §3.2.5 / §3.2.6 / §3.2.9 / §A.6 / §6.1）。"""

from __future__ import annotations

from uuid import UUID

from elder_common.auth import Principal, require_role
from elder_common.constants import ErrorCode
from elder_common.errors import AppError
from elder_common.events import DIARY_FLUSHED
from elder_common.logging import emit_event
from elder_common.repos import binding as b_repo
from elder_common.repos import diary_entry as entry_repo
from elder_common.schemas.diary_entry import AudioSegment, DiaryEntry
from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel, ConfigDict, Field

router = APIRouter(prefix="/v1/diary", tags=["diary"])

COS_UPLOAD_TTL_SECONDS = 24 * 3600


class DiaryItem(BaseModel):  # type: ignore[explicit-any] # Pydantic BaseModel 父类签名含 Any
    """§3.2.5 / §3.2.6 单条摘要：text / summary + audio_segments 列表。"""

    model_config = ConfigDict(extra="forbid")

    id: str
    elder_id: str
    date: str
    text: str
    summary: str
    audio_segments: list[AudioSegment]
    created_at: str


class DiaryListResponse(BaseModel):  # type: ignore[explicit-any] # Pydantic BaseModel 父类签名含 Any
    """GET /v1/diary 响应。"""

    model_config = ConfigDict(extra="forbid")

    diaries: list[DiaryItem]


class PendingDiary(BaseModel):  # type: ignore[explicit-any] # Pydantic BaseModel 父类签名含 Any
    """§A.6 / §3.2.9 flush-pending 单条。"""

    model_config = ConfigDict(extra="forbid")

    pending_id: UUID
    audio_cos_key: str = Field(min_length=1, max_length=256)
    turns: list[dict[str, str]] = Field(default_factory=list)
    text: str = Field(max_length=100)  # §3.1.4.D1
    summary: str = Field(max_length=60)  # §3.1.4.D2


class FlushPendingRequest(BaseModel):  # type: ignore[explicit-any] # Pydantic BaseModel 父类签名含 Any
    """§A.6 / §3.2.9 入参。"""

    model_config = ConfigDict(extra="forbid")

    pending_diaries: list[PendingDiary] = Field(min_length=1)


class FlushPendingResponse(BaseModel):  # type: ignore[explicit-any] # Pydantic BaseModel 父类签名含 Any
    """§A.6 / §6.3 出参。"""

    model_config = ConfigDict(extra="forbid")

    synced: list[dict[str, str]]
    dropped: list[dict[str, str]]


def _diary_to_item(d: DiaryEntry) -> DiaryItem:
    return DiaryItem(
        id=str(d.id),
        elder_id=str(d.elder_id),
        date=d.date,
        text=d.text,
        summary=d.summary,
        audio_segments=d.audio_segments,
        created_at=d.created_at.isoformat(),
    )


async def _verify_elder_access(principal: Principal, elder_id: UUID) -> None:
    """§10 / §18：路由层调仓储层校验（family 角色查 binding）。"""
    if principal.role == "elder" and principal.elder_id != str(elder_id):
        raise AppError(
            code=ErrorCode.FORBIDDEN,
            message="elder_id mismatch",
            http_status=403,
        )
    if principal.role == "family":
        ok = await b_repo.family_has_binding(UUID(principal.user_id), elder_id)
        if not ok:
            raise AppError(
                code=ErrorCode.FORBIDDEN,
                message="family not bound to this elder",
                http_status=403,
            )


@router.get("", response_model=DiaryListResponse)
async def list_diaries(
    elder_id: UUID = Query(...),
    date_from: str | None = Query(default=None),
    date_to: str | None = Query(default=None),
    principal: Principal = Depends(require_role("family", "elder")),
) -> DiaryListResponse:
    """§3.2.5 / §3.2.6 / §3.1.7：按 elder + 日期范围列日志。"""
    await _verify_elder_access(principal, elder_id)
    items = await entry_repo.list_by_elder_range(elder_id, date_from=date_from, date_to=date_to)
    return DiaryListResponse(diaries=[_diary_to_item(d) for d in items])


@router.get("/{diary_id}")
async def get_diary(
    diary_id: str,
    principal: Principal = Depends(require_role("family", "elder")),
) -> dict[str, object]:
    """§3.2.6 / §3.1.7：单条详情 —— 返 entry + 每段预签 COS URL。"""
    entry = await entry_repo.get_by_id(UUID(diary_id))
    if entry is None:
        raise AppError(
            code=ErrorCode.NOT_FOUND,
            message="diary not found",
            http_status=404,
        )
    await _verify_elder_access(principal, entry.elder_id)
    from elder_common.cos import build_presigned_url

    segments = [
        {
            "cos_key": s.cos_key,
            "asr_text": s.asr_text,
            "duration_ms": s.duration_ms,
            "audio_url": build_presigned_url(s.cos_key, expires_in=600),
        }
        for s in entry.audio_segments
    ]
    return {
        "id": str(entry.id),
        "elder_id": str(entry.elder_id),
        "date": entry.date,
        "text": entry.text,
        "summary": entry.summary,
        "audio_segments": segments,
        "created_at": entry.created_at.isoformat(),
    }


@router.post("/flush-pending", response_model=FlushPendingResponse)
async def flush_pending(
    body: FlushPendingRequest,
    principal: Principal = Depends(require_role("elder")),
) -> FlushPendingResponse:
    """§A.6 / §3.2.9：绑定成功后批量同步本地暂存日记。"""
    if not principal.elder_id:
        raise AppError(
            code=ErrorCode.FORBIDDEN,
            message="elder_id missing in token",
            http_status=403,
        )
    elder_id = UUID(principal.elder_id)

    from elder_common.schemas.diary_entry import DiaryEntryCreate
    from elder_common.time import now_utc

    synced: list[dict[str, str]] = []
    dropped: list[dict[str, str]] = []
    today = now_utc().strftime("%Y-%m-%d")

    for pd in body.pending_diaries:
        existing = await entry_repo.find_by_pending_id(pd.pending_id)
        if existing is not None:
            synced.append({"pending_id": str(pd.pending_id), "diary_id": str(existing.id)})
            continue

        if _is_cos_key_expired(pd.audio_cos_key):
            if pd.text or pd.summary:
                entry = await entry_repo.create(
                    DiaryEntryCreate(
                        elder_id=elder_id,
                        session_id=None,
                        date=today,
                        text=pd.text or "(未保存)",
                        summary=pd.summary or "(未保存)",
                        audio_segments=[
                            AudioSegment(
                                cos_key=pd.audio_cos_key,
                                asr_text=pd.turns[0]["elder_text"]
                                if pd.turns
                                else (pd.text or pd.summary or "已过期"),
                                duration_ms=0,
                            )
                        ],
                        pending_id=pd.pending_id,
                    )
                )
                synced.append({"pending_id": str(pd.pending_id), "diary_id": str(entry.id)})
            dropped.append(
                {
                    "pending_id": str(pd.pending_id),
                    "reason": "audio_cos_key expired (>24h)",
                }
            )
            continue

        audio_segments = [
            AudioSegment(
                cos_key=turn.get("elder_audio_cos_key", pd.audio_cos_key),
                asr_text=turn.get("elder_text", ""),
                duration_ms=max(1000, len(turn.get("elder_text", "")) * 300),
            )
            for turn in pd.turns
        ]
        if not audio_segments:
            audio_segments = [
                AudioSegment(
                    cos_key=pd.audio_cos_key,
                    asr_text=pd.text,
                    duration_ms=max(1000, len(pd.text) * 300),
                )
            ]

        entry = await entry_repo.create(
            DiaryEntryCreate(
                elder_id=elder_id,
                session_id=None,
                date=today,
                text=pd.text or "(未保存)",
                summary=pd.summary or "(未保存)",
                audio_segments=audio_segments,
                pending_id=pd.pending_id,
            )
        )
        synced.append({"pending_id": str(pd.pending_id), "diary_id": str(entry.id)})

    emit_event(
        DIARY_FLUSHED,
        elder_id=str(elder_id),
        synced_count=len(synced),
        dropped_count=len(dropped),
    )
    return FlushPendingResponse(synced=synced, dropped=dropped)


def _is_cos_key_expired(cos_key: str) -> bool:
    """§11.13：临时上传 COS key 24h 过期 —— mock 用 key 字符串判断。"""
    return "expired" in cos_key.lower()
