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

            try:
                phash_value = self.compute_phash(image)
            except Exception as exc:  # pylint: disable=broad-except
                logger.warning("Failed to compute pHash for %s: %s", candidate_id, exc)
                return self._finalize(start, "SKIP", 0.0, "phash-error", None)

            try:
                phash_int = int(phash_value, 16)
            except ValueError:
                logger.warning("Invalid pHash from image %s", candidate_id)
                return self._finalize(start, "SKIP", 0.0, "phash-error", None)

            if self.index_service.size() == 0:
                return self._finalize(start, "SKIP", 0.0, "index-empty", None)

            search_limit = self.settings.phash_max_dist + self.settings.gray_band
            nearest = self.index_service.nearest(phash_int, search_limit)
            if nearest is None:
                return self._finalize(start, "SKIP", 0.0, "no-match", "miss")

            dist, meta = nearest
            score = max(0.0, 1 - dist / 64)
            label = meta.get("id") or meta.get("hash") or meta.get("url") or "unknown"
            reason = f"sim={score:.2f};dist={dist};nearest={label}"
            if dist <= self.settings.phash_max_dist:
                return self._finalize(start, "PUBLISH", score, reason, "hit")
            if dist <= self.settings.phash_max_dist + self.settings.gray_band:
                return self._finalize(start, "SKIP", score, reason, "gray")
            return self._finalize(start, "SKIP", score, reason, "miss")

    def _finalize(self, start: float, decision: str, score: float, reason: str,
                  zone: str | None) -> tuple[str, float, str, str | None]:
        duration_ms = (perf_counter() - start) * 1000
        if zone in {"hit", "gray", "miss"}:
            self.metrics.record_candidate_result(zone)
        self.metrics.record_recommend_request(duration_ms)
        return decision, score, reason, zone
