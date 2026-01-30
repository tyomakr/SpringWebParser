from __future__ import annotations

import asyncio
from typing import Iterable

import numpy as np


class _BKNode:
    __slots__ = ("phash", "meta", "children")

    def __init__(self, phash: int, meta: dict) -> None:
        self.phash = phash
        self.meta = meta
        self.children: dict[int, _BKNode] = {}


def _hamming_distance(a: int, b: int) -> int:
    return (a ^ b).bit_count()


class BKIndex:
    def __init__(self) -> None:
        self._root: _BKNode | None = None
        self._size = 0

    def add(self, phash: int, meta: dict) -> None:
        if self._root is None:
            self._root = _BKNode(phash, meta)
            self._size = 1
            return

        node = self._root
        while True:
            dist = _hamming_distance(phash, node.phash)
            if dist == 0:
                node.meta = meta
                return
            child = node.children.get(dist)
            if child is None:
                node.children[dist] = _BKNode(phash, meta)
                self._size += 1
                return
            node = child

    def bulk_add(self, items: list[tuple[int, dict]]) -> None:
        for phash, meta in items:
            self.add(phash, meta)

    def nearest(self, phash: int, max_dist: int) -> tuple[int, dict] | None:
        if self._root is None:
            return None

        best: tuple[int, dict] | None = None
        stack = [self._root]
        while stack:
            node = stack.pop()
            dist = _hamming_distance(phash, node.phash)
            if dist <= max_dist and (best is None or dist < best[0]):
                best = (dist, node.meta)
            lower = dist - max_dist
            upper = dist + max_dist
            for child_dist, child in node.children.items():
                if lower <= child_dist <= upper:
                    stack.append(child)
        return best

    def size(self) -> int:
        return self._size


class IndexService:
    def __init__(self, lock: asyncio.Lock | None = None) -> None:
        self._index = BKIndex()
        self._semantic_index = SemanticIndex()
        self._lock = lock or asyncio.Lock()
        self._semantic_keys: set[str] = set()

    async def add(self, phash: int, meta: dict) -> None:
        async with self._lock:
            self._index.add(phash, meta)

    async def bulk_add(self, items: list[tuple[int, dict]]) -> None:
        async with self._lock:
            self._index.bulk_add(items)

    def nearest(self, phash: int, max_dist: int) -> tuple[int, dict] | None:
        return self._index.nearest(phash, max_dist)

    def size(self) -> int:
        return self._index.size()

    async def add_semantic(self, vector: np.ndarray, meta: dict) -> None:
        async with self._lock:
            key = self._semantic_key(meta)
            if key and key in self._semantic_keys:
                return
            self._semantic_index.add(vector, meta)
            if key:
                self._semantic_keys.add(key)

    async def bulk_add_semantic(self, items: list[tuple[np.ndarray, dict]]) -> None:
        async with self._lock:
            filtered: list[tuple[np.ndarray, dict]] = []
            for vector, meta in items:
                key = self._semantic_key(meta)
                if key and key in self._semantic_keys:
                    continue
                if key:
                    self._semantic_keys.add(key)
                filtered.append((vector, meta))
            if filtered:
                self._semantic_index.bulk_add(filtered)

    def nearest_semantic(self, vector: np.ndarray) -> tuple[float, dict] | None:
        return self._semantic_index.top_one(vector)

    def semantic_size(self) -> int:
        return self._semantic_index.size()

    async def warmup(self, iterator: Iterable[tuple[int, dict]], limit: int | None = None) -> None:
        batch: list[tuple[int, dict]] = []
        for idx, item in enumerate(iterator):
            batch.append(item)
            if limit and idx + 1 >= limit:
                break
        if batch:
            await self.bulk_add(batch)

    @staticmethod
    def _semantic_key(meta: dict) -> str | None:
        for key in ("hash", "url", "id"):
            value = meta.get(key)
            if value is not None:
                return str(value)
        return None


class SemanticIndex:
    def __init__(self) -> None:
        self._vectors: list[np.ndarray] = []
        self._meta: list[dict] = []
        self._matrix: np.ndarray | None = None

    @staticmethod
    def _normalize(vector: np.ndarray) -> np.ndarray:
        norm = float(np.linalg.norm(vector))
        if norm == 0:
            return vector
        return vector / norm

    def add(self, vector: np.ndarray, meta: dict) -> None:
        vec = self._normalize(vector).astype(np.float32, copy=False)
        self._vectors.append(vec)
        self._meta.append(meta)
        self._matrix = None

    def bulk_add(self, items: list[tuple[np.ndarray, dict]]) -> None:
        for vector, meta in items:
            self.add(vector, meta)

    def top_one(self, vector: np.ndarray) -> tuple[float, dict] | None:
        if not self._vectors:
            return None
        vec = self._normalize(vector).astype(np.float32, copy=False)
        if self._matrix is None:
            self._matrix = np.stack(self._vectors)
        sims = self._matrix @ vec
        best_idx = int(np.argmax(sims))
        return float(sims[best_idx]), self._meta[best_idx]

    def size(self) -> int:
        return len(self._meta)


__all__ = ["BKIndex", "IndexService", "SemanticIndex"]
