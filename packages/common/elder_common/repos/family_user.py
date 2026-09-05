"""family_user 仓储（§5.1）。"""

from __future__ import annotations

from elder_common.schemas.family_user import FamilyUser, FamilyUserCreate
from elder_common.storage.base import CollectionName, get_storage
from elder_common.time import now_utc


async def find_by_phone(phone: str) -> FamilyUser | None:
    """按手机号查；返回 None 表示未注册（§3.2.7 走 SMS 码 + 注册）。"""
    storage = get_storage()
    doc = await storage.find_one(CollectionName.FAMILY_USERS, {"phone": phone})
    if doc is None:
        return None
    return FamilyUser.model_validate(doc)


async def find_by_id(user_id: str) -> FamilyUser | None:
    storage = get_storage()
    doc = await storage.find_one(CollectionName.FAMILY_USERS, {"id": user_id})
    if doc is None:
        return None
    return FamilyUser.model_validate(doc)


async def create(payload: FamilyUserCreate) -> FamilyUser:
    """新建家属账号；服务端补 id + created_at + updated_at。"""
    storage = get_storage()
    now = now_utc()
    doc: dict[str, object] = {
        "phone": payload.phone,
        "created_at": now.isoformat(),
        "updated_at": now.isoformat(),
    }
    user_id = await storage.insert(CollectionName.FAMILY_USERS, doc)
    return FamilyUser(
        id=user_id,  # type: ignore[arg-type] # storage 返回 str uuid
        phone=payload.phone,
        created_at=now,
        updated_at=now,
    )
