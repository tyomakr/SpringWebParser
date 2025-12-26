from __future__ import annotations

import asyncio
import logging
from datetime import datetime, timezone
from time import perf_counter
from typing import List

import httpx

from .config import Settings, TrainingExportRecord
from .embedding import EmbeddingService
from .index import IndexService
from .metrics import MetricsCollector
from .phash import ImageAnalyzer
from .storage import Storage

logger = logging.getLogger(__name__)


class TrainingSyncer:
    def __init__(self, settings: Settings, http_client: httpx.AsyncClient,
                 storage: Storage, analyzer: ImageAnalyzer,
                 index_service: IndexService, metrics: MetricsCollector) -> None:
        self.settings = settings
        self.http_client = http_client
        self.storage = storage
        self.analyzer = analyzer
        self._lock = asyncio.Lock()
        self.index_service = index_service
        self.metrics = metrics
        self.embedding_service = EmbeddingService()

    async def run_periodic(self, stop_event: asyncio.Event) -> None:
        interval = max(1, self.settings.sync_interval_sec)
        while not stop_event.is_set():
            try:
                result = await self.run_once()
            except Exception as exc:  # pylint: disable=broad-except
                logger.error("Sync cycle failed: %r", exc, exc_info=True)
                result = {"error": True, "added": 0, "processed": 0, "last_sync": None}
            if result is None:
                result = {"error": True, "added": 0, "processed": 0, "last_sync": None}
            wait_timeout = interval
            if result.get("error") or (result.get("added", 0) == 0 and result.get("processed", 0) == 0):
                wait_timeout = min(interval, 60)
            try:
                await asyncio.wait_for(stop_event.wait(), timeout=wait_timeout)
            except asyncio.TimeoutError:
                continue

    async def run_once(self) -> dict:
        async with self._lock:
            logger.info("Starting training export sync")
            start = perf_counter()
            initial_index_size = self.index_service.size()
            last_sync = None if initial_index_size == 0 else self.storage.get_last_sync()
            processed = 0
            latest_created = last_sync
            offset = 0
            page_count = 0
            headers = {}
            if self.settings.training_export_api_key:
                headers["Authorization"] = f"Bearer {self.settings.training_export_api_key}"
            bulk_items: List[tuple[int, dict]] = []
            bulk_semantic: List[tuple] = []
            had_error = False
            max_attempts = 3

            if self.settings.sync_debug:
                logger.debug("Sync start | last_sync=%s index_before=%s", last_sync, initial_index_size)

            last_error: Exception | None = None
            try:
                while True:
                    params = {
                        "limit": self.settings.sync_page_limit,
                        "offset": offset,
                    }
                    if latest_created:
                        params["since"] = latest_created

                    resp = None
                    for attempt in range(1, max_attempts + 1):
                        try:
                            resp = await self.http_client.get(
                                str(self.settings.training_export_url),
                                params=params,
                                headers=headers,
                                timeout=self.settings.request_timeout,
                            )
                            resp.raise_for_status()
                            break
                        except httpx.HTTPStatusError as exc:
                            body_preview = ""
                            try:
                                body_preview = (await exc.response.aread())[:200].decode("utf-8", errors="replace")
                            except Exception:  # pylint: disable=broad-except
                                body_preview = "<unavailable>"
                            logger.error(
                                "Training export HTTP error %s for %s params=%s body=%s (attempt %s/%s)",
                                exc.response.status_code,
                                self.settings.training_export_url,
                                params,
                                body_preview,
                                attempt,
                                max_attempts,
                            )
                            had_error = True
                            resp = None
                            last_error = exc
                            break
                        except httpx.RequestError as exc:
                            logger.warning(
                                "Training export request failed url=%s params=%s error=%r attempt=%s/%s",
                                self.settings.training_export_url,
                                params,
                                exc,
                                attempt,
                                max_attempts,
                            )
                            had_error = True
                            resp = None
                            last_error = exc
                            if attempt < max_attempts:
                                if self.settings.sync_debug:
                                    logger.debug("Retrying export fetch attempt=%s after backoff", attempt)
                                await asyncio.sleep(2)
                            else:
                                break
                    if resp is None:
                        if last_error:
                            raise last_error
                        break

                    records = [TrainingExportRecord(**item) for item in resp.json()]
                    if self.settings.sync_debug:
                        logger.debug("Fetched page offset=%s size=%s", offset, len(records))
                    if not records:
                        break

                    page_count += 1
                    for record in records:
                        phash_value: str | None = None
                        embedding_vec = None
                        try:
                            image = await self.analyzer.fetch_image(record.url)
                            phash_value = self.analyzer.compute_phash(image)
                            if self.settings.similarity_mode in {"semantic", "hybrid"}:
                                try:
                                    embedding_vec = self.embedding_service.compute(image)
                                except Exception as exc:  # pylint: disable=broad-except
                                    logger.warning("Failed to compute embedding for record %s: %s", record.id, exc)
                        except Exception as exc:  # pylint: disable=broad-except
                            logger.warning("Failed to sync record %s: %s", record.id, exc)
                        # Fallback: try to derive pHash surrogate from provided hash/url to avoid empty index.
                        if record.hash:
                            phash_value = record.hash
                        else:
                            import hashlib
                            phash_value = hashlib.md5(record.url.encode("utf-8")).hexdigest()
                        if not phash_value:
                            continue
                        self.storage.upsert_positive(
                            hash=record.hash,
                            url=record.url,
                            phash=phash_value,
                            created_at=record.createdAt,
                        )
                        try:
                            phash_int = int(phash_value, 16)
                        except ValueError:
                            # If hash is not hex, derive a stable surrogate
                            import hashlib
                            phash_int = int(hashlib.md5(phash_value.encode("utf-8")).hexdigest(), 16)
                        bulk_items.append((phash_int, {
                            "id": record.id,
                            "url": record.url,
                            "hash": record.hash,
                            "created_at": record.createdAt,
                        }))
                        if embedding_vec is None:
                            fallback_key = phash_value or record.hash or record.url
                            embedding_vec = self.embedding_service.fallback_from_hash(fallback_key)
                        bulk_semantic.append((embedding_vec, {
                            "id": record.id,
                            "url": record.url,
                            "hash": record.hash,
                            "created_at": record.createdAt,
                        }))
                        processed += 1
                        latest_created = max(latest_created or record.createdAt, record.createdAt)
                    if self.settings.sync_debug:
                        logger.debug("Page done offset=%s processed_in_page=%s latest_created=%s",
                                     offset, processed, latest_created)
                    if 0 < self.settings.sync_max_pages_per_run <= page_count:
                        logger.info("Stopping sync early after %s pages (max per run)", page_count)
                        break
                    if len(records) < self.settings.sync_page_limit:
                        break
                    offset += len(records)

                if latest_created:
                    self.storage.set_last_sync(latest_created)
                if bulk_items:
                    await self.index_service.bulk_add(bulk_items)
                if bulk_semantic:
                    await self.index_service.bulk_add_semantic(bulk_semantic)
                added_count = max(0, self.index_service.size() - initial_index_size)
                duration_ms = (perf_counter() - start) * 1000
                run_ts = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
                self.metrics.record_sync(run_ts, duration_ms, added_count, success=not had_error)
                logger.info("Sync finished: processed=%s last_sync=%s index-added=%s error=%s",
                            processed, latest_created, added_count, had_error)
                if self.settings.sync_debug:
                    logger.debug("Sync summary | index_before=%s index_after=%s added=%s duration_ms=%.2f error=%s",
                                 initial_index_size, self.index_service.size(), added_count, duration_ms, had_error)
            except Exception as exc:  # pylint: disable=broad-except
                had_error = True
                duration_ms = (perf_counter() - start) * 1000
                self.metrics.record_sync(None, duration_ms, 0, success=False)
                logger.error("Sync cycle failed: %r", exc, exc_info=True)
                raise
            return {"processed": processed, "last_sync": latest_created, "added": added_count, "error": had_error}
