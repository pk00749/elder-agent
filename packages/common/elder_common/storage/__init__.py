"""存储抽象层（AGENTS.md §10）。

业务代码只通过 `get_storage()` 访问数据；具体实现（内存 / CloudBase）
由启动时通过 `set_storage()` 注入。
"""

from __future__ import annotations

from elder_common.storage.base import (
    CollectionName,
    IndexSpec,
    Storage,
    StorageError,
    get_storage,
    set_storage,
)

__all__ = [
    "CollectionName",
    "IndexSpec",
    "Storage",
    "StorageError",
    "get_storage",
    "set_storage",
]
