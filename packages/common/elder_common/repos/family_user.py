"""family_user 仓储（§5.1；v2.1.2 anonymous-device 流程）。"""

from __future__ import annotations

from elder_common.schemas.family_user import (
    FamilyUser,
    FamilyUserCreate,
    FamilyUserCreateMvp,
)
from elder_common.storage.base import CollectionName, get_storage
from elder_common.time import now_utc


async def find_by_device_token(device_token: str) -> FamilyUser | None:
    """按 device_token 查（v2.1.2 anonymous-device 流程唯一标识）。"""
    storage = get_storage()
    doc = await storage.find_one(
        CollectionName.FAMILY_USERS, {"device_token": device_token}
    )
    if doc is None:
        return None
    return FamilyUser.model_validate(doc)


async def find_by_id(user_id: str) -> FamilyUser | None:
    storage = get_storage()
    doc = await storage.find_one(CollectionName.FAMILY_USERS, {"id": user_id})
    if doc is None:
        return None
    return FamilyUser.model_validate(doc)


async def create_mvp(payload: FamilyUserCreateMvp) -> FamilyUser:
    """MVP 阶段新建匿名家属账号（v2.1.2）—— device_token 必填、phone 留空。"""
    storage = get_storage()
    now = now_utc()
    doc: dict[str, object] = {
        "phone": payload.phone,
        "device_token": payload.device_token,
        "created_at": now.isoformat(),
        "updated_at": now.isoformat(),
    }
    user_id = await storage.insert(CollectionName.FAMILY_USERS, doc)
    return FamilyUser(
        id=user_id,  # type: ignore[arg-type] # storage 返回 str uuid
        phone=payload.phone,
        device_token=payload.device_token,
        created_at=now,
        updated_at=now,
    )


async def create(payload: FamilyUserCreate) -> FamilyUser:
    """MVP-DEFER：v2.x 接 SMS 后复用；现保留签名 + 注释，不被路由调用。"""
    storage = get_storage()
    now = now_utc()
    doc: dict[str, object] = {
        "phone": payload.phone,
        "device_token": payload.device_token,
        "created_at": now.isoformat(),
        "updated_at": now.isoformat(),
    }
    user_id = await storage.insert(CollectionName.FAMILY_USERS, doc)
    return FamilyUser(
        id=user_id,  # type: ignore[arg-type]
        phone=payload.phone,
        device_token=payload.device_token,
        created_at=now,
        updated_at=now,
    )
