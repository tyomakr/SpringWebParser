from __future__ import annotations

import asyncio
import hashlib
import logging
from dataclasses import dataclass
from io import BytesIO
from time import perf_counter

import httpx
import imagehash
from PIL import Image

from .config import Settings
from .embedding import EmbeddingService
from .index import IndexService
from .metrics import MetricsCollector
from .storage import Storage
from .text_detector import TextDetector

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class PositiveRecord:
    id: int
    url: str
    hash: str
    phash: str


class ImageAnalyzer:
    def __init__(self, settings: Settings, http_client: httpx.AsyncClient, storage: Storage,
                 index_service: IndexService, metrics: MetricsCollector | None = None) -> None:
        self.settings = settings
        self.http_client = http_client
        self.storage = storage
        self.index_service = index_service
        self.metrics = metrics or MetricsCollector()
        self.text_detector = TextDetector(settings.ocr_enabled, settings.ocr_min_chars)
        self._semaphore = asyncio.Semaphore(settings.max_concurrency)
        self.embedding_service = EmbeddingService()

    async def fetch_image(self, url: str) -> Image.Image:
        timeout = httpx.Timeout(self.settings.request_timeout, connect=self.settings.request_timeout)
        resp = await self.http_client.get(url, timeout=timeout)
        resp.raise_for_status()
        return Image.open(BytesIO(resp.content)).convert("RGB")

    @staticmethod
    def compute_phash(image: Image.Image) -> str:
        hash_value = imagehash.phash(image, hash_size=8)
        return hash_value.__str__()

    @staticmethod
    def compute_hash(url: str) -> str:
        digest = hashlib.md5(url.encode("utf-8")).hexdigest()
        return digest

    def load_positives(self) -> list[PositiveRecord]:
        rows = self.storage.list_active_positives()
        return [PositiveRecord(id=row["id"], url=row["url"], hash=row["hash"], phash=row["phash"]) for row in rows]

    async def analyze_candidate(self, candidate_id: str, url: str) -> tuple[str, float, str, str | None]:
        start = perf_counter()
        async with self._semaphore:
            try:
                image = await self.fetch_image(url)
            except Exception as exc:  # pylint: disable=broad-except
                logger.warning("Failed to fetch image %s: %s", url, exc)
                return self._finalize(start, "SKIP", 0.0, "fetch-failed", None)

            if self.text_detector.is_text_dominant(image):
                return self._finalize(start, "SKIP", 0.0, "text-only", None)

            mode = (self.settings.similarity_mode or "phash").lower()
            use_phash = mode in {"phash", "hybrid"}
            use_semantic = mode in {"semantic", "hybrid"}

            phash_value: str | None = None
            phash_int: int | None = None
            embedding_vec = None

            if use_phash:
                try:
                    phash_value = self.compute_phash(image)
                    phash_int = int(phash_value, 16)
                except Exception as exc:  # pylint: disable=broad-except
                    logger.warning("Failed to compute pHash for %s: %s", candidate_id, exc)
                    return self._finalize(start, "SKIP", 0.0, "phash-error", None)

            if use_semantic:
                try:
                    embedding_vec = self.embedding_service.compute(image)
                except Exception as exc:  # pylint: disable=broad-except
                    logger.warning("Failed to compute embedding for %s: %s", candidate_id, exc)
                    embedding_vec = None

            phash_index_size = self.index_service.size()
            semantic_index_size = self.index_service.semantic_size()

            if mode == "semantic":
                if semantic_index_size == 0:
                    return self._finalize(start, "SKIP", 0.0, "semantic-index-empty", None, semantic=True,
                                          max_similarity=None)
            elif phash_index_size == 0:
                # Hybrid can still use semantic even if pHash index is empty
                if use_semantic and semantic_index_size > 0:
                    pass
                else:
                    return self._finalize(start, "SKIP", 0.0, "index-empty", None)

            # Fast pHash path
            if use_phash and phash_int is not None and phash_index_size > 0:
                search_limit = self.settings.phash_max_dist + self.settings.gray_band
                nearest = self.index_service.nearest(phash_int, search_limit)
                if nearest is not None:
                    dist, meta = nearest
                    score = max(0.0, 1 - dist / 64)
                    label = meta.get("id") or meta.get("hash") or meta.get("url") or "unknown"
                    reason = f"sim={score:.2f};dist={dist};nearest={label}"
                    if dist <= self.settings.phash_max_dist:
                        return self._finalize(start, "PUBLISH", score, reason, "hit")
                    if dist <= self.settings.phash_max_dist + self.settings.gray_band:
                        return self._finalize(start, "SKIP", score, reason, "gray")
                    # If hybrid, fall through to semantic when miss
                    if mode == "phash":
                        return self._finalize(start, "SKIP", score, reason, "miss")

            if not use_semantic:
                return self._finalize(start, "SKIP", 0.0, "no-match", "miss")

            if semantic_index_size == 0:
                return self._finalize(start, "SKIP", 0.0, "semantic-index-empty", None, semantic=True,
                                      max_similarity=None)

            if embedding_vec is None:
                fallback_key = phash_value or url
                embedding_vec = self.embedding_service.fallback_from_hash(fallback_key)

            nearest_sem = self.index_service.nearest_semantic(embedding_vec)
            if nearest_sem is None:
                return self._finalize(start, "SKIP", 0.0, "semantic-index-empty", None, semantic=True,
                                      max_similarity=None)

            sim, meta = nearest_sem
            label = meta.get("id") or meta.get("hash") or meta.get("url") or "unknown"
            reason = f"semantic maxSim={sim:.3f};nearest={label}"
            if sim >= self.settings.semantic_publish_threshold:
                return self._finalize(start, "PUBLISH", sim, reason, "hit", semantic=True, max_similarity=sim)
            if sim >= self.settings.semantic_gray_threshold:
                return self._finalize(start, "SKIP", sim, reason, "gray", semantic=True, max_similarity=sim)
            reason = f"{reason};below={self.settings.semantic_gray_threshold:.3f}"
            return self._finalize(start, "SKIP", sim, reason, "miss", semantic=True, max_similarity=sim)

    def _finalize(self, start: float, decision: str, score: float, reason: str,
                  zone: str | None, semantic: bool = False, max_similarity: float | None = None) -> tuple[str, float, str, str | None]:
        duration_ms = (perf_counter() - start) * 1000
        if zone in {"hit", "gray", "miss"}:
            self.metrics.record_candidate_result(zone)
        self.metrics.record_recommend_request(duration_ms)
        if semantic:
            self.metrics.record_semantic_request(duration_ms, zone, max_similarity)
        return decision, score, reason, zone
