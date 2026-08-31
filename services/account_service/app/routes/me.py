"""当前用户信息（PRD §6.1 implicit — 用于客户端缓存 / 校验）。"""

from __future__ import annotations

from elder_common.auth import Principal, require_role
from fastapi import APIRouter, Depends

router = APIRouter(prefix="/v1/me", tags=["me"])


@router.get("")
async def me(
    principal: Principal = Depends(require_role("family", "elder")),
) -> dict[str, object]:
    return {
        "user_id": principal.user_id,
        "role": principal.role,
        "elder_id": principal.elder_id,
    }
