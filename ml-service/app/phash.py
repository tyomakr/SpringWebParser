from __future__ import annotations

import asyncio
import logging
from dataclasses import dataclass
from io import BytesIO
from typing import Iterable, Optional

import httpx
import imagehash
from PIL import Image

from .config import Settings
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
    def __init__(self, settings: Settings, http_client: httpx.AsyncClient, storage: Storage) -> None:
        self.settings = settings
        self.http_client = http_client
        self.storage = storage
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
    def hamming_distance(hex_a: str, hex_b: str) -> int:
        return imagehash.hex_to_hash(hex_a) - imagehash.hex_to_hash(hex_b)

    def load_positives(self) -> list[PositiveRecord]:
        rows = self.storage.list_active_positives()
        return [PositiveRecord(id=row["id"], url=row["url"], hash=row["hash"], phash=row["phash"]) for row in rows]

    async def analyze_candidate(self, candidate_id: str, url: str,
                                positives: list[PositiveRecord]) -> tuple[str, float, str]:
        async with self._semaphore:
            try:
                image = await self.fetch_image(url)
            except Exception as exc:  # pylint: disable=broad-except
                logger.warning("Failed to fetch image %s: %s", url, exc)
                return "SKIP", 0.0, "fetch-failed"

            if self.text_detector.is_text_dominant(image):
                return "SKIP", 0.0, "text-only"

            try:
                phash_value = self.compute_phash(image)
            except Exception as exc:  # pylint: disable=broad-except
                logger.warning("Failed to compute pHash for %s: %s", candidate_id, exc)
                return "SKIP", 0.0, "phash-error"

            if not positives:
                return "SKIP", 0.0, "no-positives"

            decision, score, reason = self._match_with_positives(phash_value, positives)
            return decision, score, reason

    def _match_with_positives(self, phash_value: str,
                              positives: Iterable[PositiveRecord]) -> tuple[str, float, str]:
        best: Optional[tuple[int, PositiveRecord]] = None
        for record in positives:
            try:
                dist = self.hamming_distance(phash_value, record.phash)
            except ValueError:  # corrupted stored hash
                logger.warning("Malformed pHash in DB for hash=%s", record.hash)
                continue
            if best is None or dist < best[0]:
                best = (dist, record)

        if best is None:
            return "SKIP", 0.0, "no-positives"

        dist, record = best
        score = max(0.0, 1 - dist / 64)
        reason = f"sim={score:.2f};nearest={record.hash};dist={dist}"
        if dist <= self.settings.phash_max_dist:
            return "PUBLISH", score, reason
        return "SKIP", score, reason
