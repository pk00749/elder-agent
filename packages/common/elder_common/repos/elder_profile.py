"""elder_profile 仓储（§5.2）。"""

from __future__ import annotations

from elder_common.schemas.elder_profile import ElderProfile, ElderProfileCreate
from elder_common.storage.base import CollectionName, get_storage
from elder_common.time import now_utc


async def get_by_id(elder_id: str) -> ElderProfile | None:
    storage = get_storage()
    doc = await storage.find_one(CollectionName.ELDER_PROFILES, {"id": elder_id})
    if doc is None:
        return None
    return ElderProfile.model_validate(doc)


async def get_by_device_token(device_token: str) -> ElderProfile | None:
    storage = get_storage()
    doc = await storage.find_one(CollectionName.ELDER_PROFILES, {"device_token": device_token})
    if doc is None:
        return None
    return ElderProfile.model_validate(doc)


async def create(payload: ElderProfileCreate) -> ElderProfile:
    """创建 elder_profile；§3.2.8 / §A.5 在 bind/confirm 事务内调。"""
    storage = get_storage()
    now = now_utc()
    doc: dict[str, object] = {
        "name": payload.name,
        "device_token": payload.device_token,
        "timezone": payload.timezone,
        "created_at": now.isoformat(),
        "updated_at": now.isoformat(),
    }
    elder_id = await storage.insert(CollectionName.ELDER_PROFILES, doc)
    return ElderProfile(
        id=elder_id,  # type: ignore[arg-type]
        name=payload.name,
        device_token=payload.device_token,
        timezone=payload.timezone,
        created_at=now,
        updated_at=now,
    )
