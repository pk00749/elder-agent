"""InMemoryStorage —— dev / 测试用（AGENTS.md §14 mock 必备）。"""

from __future__ import annotations

import copy
import re
import uuid
from collections.abc import Mapping
from typing import cast

from elder_common.storage.base import CollectionName, IndexSpec


def _new_id() -> str:
    return str(uuid.uuid4())


def _matches(doc: Mapping[str, object], query: Mapping[str, object]) -> bool:
    """支持 `{ "id": "x", "status": {"$in": [...]} }` 形式的简单 query。"""
    for key, expected in query.items():
        actual = doc.get(key)
        if isinstance(expected, Mapping):
            for op, operand in cast(Mapping[str, object], expected).items():
                if op == "$in":
                    items = cast("list[object] | tuple[object, ...]", operand)
                    if actual not in items:
                        return False
                elif op == "$ne":
                    if actual == operand:
                        return False
                elif op == "$exists":
                    if (operand and key not in doc) or (not operand and key in doc):
                        return False
                else:
                    raise NotImplementedError(f"unsupported op: {op}")
        elif isinstance(expected, re.Pattern):
            if not isinstance(actual, str) or not expected.search(actual):
                return False
        else:
            if actual != expected:
                return False
    return True


class InMemoryStorage:
    """进程内字典式存储。线程不安全 —— 仅供 dev / 测试。"""

    def __init__(self) -> None:
        self._docs: dict[CollectionName, list[dict[str, object]]] = {}
        self._indexes: dict[CollectionName, list[IndexSpec]] = {}

    async def insert(
        self,
        collection: CollectionName,
        doc: Mapping[str, object],
    ) -> str:
        bucket = self._docs.setdefault(collection, [])
        stored: dict[str, object] = dict(doc)
        if "id" not in stored:
            stored["id"] = _new_id()
        for existing in bucket:
            if existing.get("id") == stored["id"]:
                raise ValueError(f"duplicate id in {collection}: {stored['id']}")
        bucket.append(copy.deepcopy(stored))
        return str(stored["id"])

    async def find_one(
        self,
        collection: CollectionName,
        query: Mapping[str, object],
    ) -> dict[str, object] | None:
        for doc in self._docs.get(collection, []):
            if _matches(doc, query):
                return copy.deepcopy(doc)
        return None

    async def find_many(
        self,
        collection: CollectionName,
        query: Mapping[str, object],
        *,
        limit: int = 100,
        sort: list[tuple[str, int]] | None = None,
    ) -> list[dict[str, object]]:
        results: list[dict[str, object]] = []
        for d in self._docs.get(collection, []):
            if _matches(d, query):
                results.append(copy.deepcopy(d))
        if sort:
            for field, direction in reversed(sort):
                # 类型注解 lambda —— mypy --strict 无法推导带默认参数的 lambda 类型
                def getter(d: dict[str, object], f: str = field) -> tuple[bool, object]:
                    return (d.get(f) is None, d.get(f))

                results.sort(key=getter)
                if direction < 0:
                    results.reverse()
        return results[:limit]

    async def update_one(
        self,
        collection: CollectionName,
        query: Mapping[str, object],
        update: Mapping[str, object],
    ) -> bool:
        for doc in self._docs.get(collection, []):
            if _matches(doc, query):
                set_part = update.get("$set")
                if isinstance(set_part, Mapping):
                    for k, v in cast(Mapping[str, object], set_part).items():
                        doc[k] = v
                inc_part = update.get("$inc")
                if isinstance(inc_part, Mapping):
                    for k, v in cast(Mapping[str, object], inc_part).items():
                        current = doc.get(k)
                        doc[k] = (cast(int, current) if current is not None else 0) + cast(int, v)
                return True
        return False

    async def delete_one(
        self,
        collection: CollectionName,
        query: Mapping[str, object],
    ) -> bool:
        bucket = self._docs.get(collection, [])
        for i, doc in enumerate(bucket):
            if _matches(doc, query):
                del bucket[i]
                return True
        return False

    async def ensure_indexes(
        self,
        collection: CollectionName,
        indexes: list[IndexSpec],
    ) -> None:
        existing = self._indexes.setdefault(collection, [])
        for spec in indexes:
            if spec not in existing:
                existing.append(spec)
