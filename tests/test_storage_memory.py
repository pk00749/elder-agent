"""InMemoryStorage 单测（§14：上游 mock 必备）。"""
from __future__ import annotations

from elder_common.storage.base import CollectionName, IndexSpec
from elder_common.storage.memory import InMemoryStorage


async def test_insert_generates_id() -> None:
    s = InMemoryStorage()
    new_id = await s.insert(CollectionName.FAMILY_USERS, {"phone": "13800138000"})
    assert new_id
    doc = await s.find_one(CollectionName.FAMILY_USERS, {"id": new_id})
    assert doc is not None
    assert doc["phone"] == "13800138000"


async def test_find_many_with_in_operator() -> None:
    s = InMemoryStorage()
    await s.insert(CollectionName.REMINDERS, {"type": "medication", "status": "active"})
    await s.insert(CollectionName.REMINDERS, {"type": "medication", "status": "paused"})
    await s.insert(CollectionName.REMINDERS, {"type": "appointment", "status": "active"})
    docs = await s.find_many(
        CollectionName.REMINDERS,
        {"type": "medication", "status": {"$in": ["active", "paused"]}},
    )
    assert len(docs) == 2


async def test_update_one_set() -> None:
    s = InMemoryStorage()
    new_id = await s.insert(CollectionName.REMINDERS, {"status": "active"})
    ok = await s.update_one(
        CollectionName.REMINDERS,
        {"id": new_id},
        {"$set": {"status": "paused"}},
    )
    assert ok
    doc = await s.find_one(CollectionName.REMINDERS, {"id": new_id})
    assert doc is not None
    assert doc["status"] == "paused"


async def test_delete_one() -> None:
    s = InMemoryStorage()
    new_id = await s.insert(CollectionName.REMINDERS, {})
    ok = await s.delete_one(CollectionName.REMINDERS, {"id": new_id})
    assert ok
    doc = await s.find_one(CollectionName.REMINDERS, {"id": new_id})
    assert doc is None


async def test_ensure_indexes_idempotent() -> None:
    s = InMemoryStorage()
    idx = [IndexSpec(("phone",), unique=True)]
    await s.ensure_indexes(CollectionName.FAMILY_USERS, idx)
    await s.ensure_indexes(CollectionName.FAMILY_USERS, idx)  # 再调一次幂等
    assert s._indexes[CollectionName.FAMILY_USERS] == idx  # type: ignore[attr-defined]
