"""binding 仓储（§5.3）。"""

from __future__ import annotations

from uuid import UUID

from elder_common.schemas.binding import Binding, BindingCreate, BindingRole
from elder_common.storage.base import CollectionName, get_storage
from elder_common.time import now_utc


async def create(payload: BindingCreate) -> Binding:
    """新建 binding；§3.2.8 §H.25 主/次由调用方决定（§A.5 bind/confirm 事务）。"""
    storage = get_storage()
    now = now_utc()
    doc: dict[str, object] = {
        "family_id": str(payload.family_id),
        "elder_id": str(payload.elder_id),
        "role": payload.role.value,
        "created_at": now.isoformat(),
    }
    binding_id = await storage.insert(CollectionName.BINDINGS, doc)
    return Binding(
        id=binding_id,  # type: ignore[arg-type]
        family_id=payload.family_id,
        elder_id=payload.elder_id,
        role=payload.role,
        created_at=now,
    )


async def find_primary_by_elder(elder_id: UUID) -> Binding | None:
    """§H.25：查 elder 当前 primary 是否已存在；用于 bind/confirm 决定角色降级。"""
    storage = get_storage()
    doc = await storage.find_one(
        CollectionName.BINDINGS,
        {"elder_id": str(elder_id), "role": BindingRole.PRIMARY.value},
    )
    if doc is None:
        return None
    return Binding.model_validate(doc)


async def family_has_binding(family_id: UUID, elder_id: UUID) -> bool:
    """§10 / §18：family 角色调 elder 端点前的归属校验（仓储层做）。"""
    storage = get_storage()
    doc = await storage.find_one(
        CollectionName.BINDINGS,
        {"family_id": str(family_id), "elder_id": str(elder_id)},
    )
    return doc is not None


async def list_by_elder(elder_id: UUID) -> list[Binding]:
    storage = get_storage()
    docs = await storage.find_many(CollectionName.BINDINGS, {"elder_id": str(elder_id)})
    return [Binding.model_validate(d) for d in docs]


async def delete(binding_id: UUID) -> bool:
    """§3.2.8 §11.6 解绑 —— 仅 primary 角色可调（路由层 + 这里都校验）。"""
    storage = get_storage()
    return await storage.delete_one(CollectionName.BINDINGS, {"id": str(binding_id)})
