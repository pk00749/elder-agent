"""CloudBaseStorage —— 腾讯云 CloudBase NoSQL 适配（PRD §8.2）。

PR 2 范围：留接口 + 明确部署实现位置；prod 部署时把方法体替换为 cloudbase
SDK 调用（或 CloudBase HTTP API 调 httpx），错误按 AppError(UPSTREAM_*) 抛。

调用前需要在 prod 启动钩子里 `set_storage(CloudBaseStorage(env_id, region))`。
"""

from __future__ import annotations

from collections.abc import Mapping

from elder_common.storage.base import CollectionName, IndexSpec


class CloudBaseStorage:
    """CloudBase NoSQL 客户端占位。

    当前所有方法抛 NotImplementedError —— PR 4 部署阶段补真实 SDK 调用；
    业务路由通过 `Storage` Protocol 调用本类，dev / 测试用 InMemoryStorage。
    """

    def __init__(self, env_id: str, region: str) -> None:
        self.env_id = env_id
        self.region = region

    async def insert(
        self,
        collection: CollectionName,
        doc: Mapping[str, object],
    ) -> str:
        raise NotImplementedError("CloudBaseStorage.insert lands in deployment PR")

    async def find_one(
        self,
        collection: CollectionName,
        query: Mapping[str, object],
    ) -> dict[str, object] | None:
        raise NotImplementedError("CloudBaseStorage.find_one lands in deployment PR")

    async def find_many(
        self,
        collection: CollectionName,
        query: Mapping[str, object],
        *,
        limit: int = 100,
        sort: list[tuple[str, int]] | None = None,
    ) -> list[dict[str, object]]:
        raise NotImplementedError("CloudBaseStorage.find_many lands in deployment PR")

    async def update_one(
        self,
        collection: CollectionName,
        query: Mapping[str, object],
        update: Mapping[str, object],
    ) -> bool:
        raise NotImplementedError("CloudBaseStorage.update_one lands in deployment PR")

    async def delete_one(
        self,
        collection: CollectionName,
        query: Mapping[str, object],
    ) -> bool:
        raise NotImplementedError("CloudBaseStorage.delete_one lands in deployment PR")

    async def ensure_indexes(
        self,
        collection: CollectionName,
        indexes: list[IndexSpec],
    ) -> None:
        raise NotImplementedError("CloudBaseStorage.ensure_indexes lands in deployment PR")
