"""存储抽象：Protocol + 单例（AGENTS.md §10）。"""

from __future__ import annotations

from collections.abc import Mapping
from enum import Enum
from typing import Protocol, runtime_checkable


class CollectionName(str, Enum):
    """集合名 —— 严格按 PRD §5 模型名 → 复数 snake_case。"""

    FAMILY_USERS = "family_users"
    ELDER_PROFILES = "elder_profiles"
    BINDINGS = "bindings"
    BIND_ATTEMPTS = "bind_attempts"
    REMINDERS = "reminders"
    REMINDER_ACKS = "reminder_acks"
    DIARY_SESSIONS = "diary_sessions"
    DIARY_ENTRIES = "diary_entries"


class IndexSpec:
    """索引描述 —— 实际实现交给存储后端；这里只透传给 ensure_indexes()."""

    __slots__ = ("fields", "unique")

    def __init__(self, fields: tuple[str, ...], *, unique: bool = False) -> None:
        self.fields = fields
        self.unique = unique

    def __repr__(self) -> str:
        kind = "UNIQUE" if self.unique else "INDEX"
        return f"{kind}({', '.join(self.fields)})"


class StorageError(Exception):
    """存储层错误基类。"""


@runtime_checkable
class Storage(Protocol):
    """存储抽象 —— 仓储函数与上游 SDK 唯一入口（§10）。"""

    async def insert(self, collection: CollectionName, doc: Mapping[str, object]) -> str:
        """插入文档；返回生成的 id（已写入 doc['id']）。"""
        ...

    async def find_one(
        self,
        collection: CollectionName,
        query: Mapping[str, object],
    ) -> dict[str, object] | None:
        """按 query 查一条；None 表示未命中。"""
        ...

    async def find_many(
        self,
        collection: CollectionName,
        query: Mapping[str, object],
        *,
        limit: int = 100,
        sort: list[tuple[str, int]] | None = None,
    ) -> list[dict[str, object]]:
        """按 query 查多条。sort 项为 (field, direction)，direction 1=升/-1=降。"""
        ...

    async def update_one(
        self,
        collection: CollectionName,
        query: Mapping[str, object],
        update: Mapping[str, object],
    ) -> bool:
        """原子更新一条；返回是否命中。"""
        ...

    async def delete_one(
        self,
        collection: CollectionName,
        query: Mapping[str, object],
    ) -> bool:
        """删除一条；返回是否命中。"""
        ...

    async def ensure_indexes(
        self,
        collection: CollectionName,
        indexes: list[IndexSpec],
    ) -> None:
        """确保集合索引存在（部署时调用；运行时幂等）。"""
        ...


_storage: Storage | None = None


def set_storage(storage: Storage) -> None:
    """注入存储实现。dev / 测试用 InMemoryStorage；prod 用 CloudBaseStorage。"""
    global _storage
    _storage = storage


def get_storage() -> Storage:
    """取单例存储；未注入时回退到 InMemoryStorage（便于开发 / 测试零配置）。"""
    global _storage
    if _storage is None:
        from elder_common.storage.memory import InMemoryStorage

        _storage = InMemoryStorage()
    return _storage
