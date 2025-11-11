from __future__ import annotations

import asyncio
import logging
from datetime import datetime
from typing import List

import httpx
from PIL import Image

from .config import Settings, TrainingExportRecord
from .phash import ImageAnalyzer
from .storage import Storage

logger = logging.getLogger(__name__)


class TrainingSyncer:
    def __init__(self, settings: Settings, http_client: httpx.AsyncClient,
                 storage: Storage, analyzer: ImageAnalyzer) -> None:
        self.settings = settings
        self.http_client = http_client
        self.storage = storage
        self.analyzer = analyzer
        self._lock = asyncio.Lock()

    async def run_periodic(self, stop_event: asyncio.Event) -> None:
        while not stop_event.is_set():
            try:
                await self.run_once()
            except Exception as exc:  # pylint: disable=broad-except
                logger.error("Sync cycle failed: %s", exc)
            await asyncio.wait(
                [stop_event.wait()],
                timeout=self.settings.sync_interval_sec,
            )

    async def run_once(self) -> dict:
        async with self._lock:
            logger.info("Starting training export sync")
            last_sync = self.storage.get_last_sync()
            processed = 0
            latest_created = last_sync
            offset = 0
            headers = {}
            if self.settings.training_export_api_key:
                headers["Authorization"] = f"Bearer {self.settings.training_export_api_key}"

            while True:
                params = {
                    "limit": self.settings.sync_page_limit,
                    "offset": offset,
                }
                if latest_created:
                    params["since"] = latest_created

                resp = await self.http_client.get(
                    str(self.settings.training_export_url),
                    params=params,
                    headers=headers,
                    timeout=self.settings.request_timeout,
                )
                resp.raise_for_status()
                records = [TrainingExportRecord(**item) for item in resp.json()]
                if not records:
                    break

                for record in records:
                    try:
                        image = await self.analyzer.fetch_image(record.url)
                        phash_value = self.analyzer.compute_phash(image)
                        self.storage.upsert_positive(
                            hash=record.hash,
                            url=record.url,
                            phash=phash_value,
                            created_at=record.createdAt,
                        )
                        processed += 1
                        latest_created = max(latest_created or record.createdAt, record.createdAt)
                    except Exception as exc:  # pylint: disable=broad-except
                        logger.warning("Failed to sync record %s: %s", record.id, exc)
                if len(records) < self.settings.sync_page_limit:
                    break
                offset += self.settings.sync_page_limit

            if latest_created:
                self.storage.set_last_sync(latest_created)
            logger.info("Sync finished: processed=%s last_sync=%s", processed, latest_created)
            return {"processed": processed, "last_sync": latest_created}
