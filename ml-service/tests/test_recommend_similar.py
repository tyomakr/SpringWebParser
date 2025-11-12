import asyncio

import httpx
import pytest
from PIL import Image

from app.config import Settings
from app.index import IndexService
from app.metrics import MetricsCollector
from app.phash import ImageAnalyzer
from app.storage import Storage


@pytest.mark.asyncio
async def test_recommend_publish_when_similar(tmp_path):
    settings = Settings(TRAINING_EXPORT_URL="http://backend/api", db_path=str(tmp_path / "db.sqlite"))
    storage = Storage(settings.db_path)
    storage.init()

    base_image = Image.new("RGB", (32, 32), color="white")
    phash_value = ImageAnalyzer.compute_phash(base_image)
    storage.upsert_positive(hash="hash-1", url="https://positive/1.jpg",
                           phash=phash_value, created_at="2024-01-01T00:00:00Z")

    index_service = IndexService()
    metrics = MetricsCollector()

    async with httpx.AsyncClient() as http_client:
        analyzer = ImageAnalyzer(settings, http_client, storage, index_service, metrics)

        async def fake_fetch(_):  # noqa: ANN001
            return base_image

        analyzer.fetch_image = fake_fetch  # type: ignore[assignment]
        await index_service.add(int(phash_value, 16), {
            "id": 1,
            "url": "https://positive/1.jpg",
            "hash": "hash-1",
            "created_at": "2024-01-01T00:00:00Z",
        })
        decision, score, reason = await analyzer.analyze_candidate("123", "https://candidate/1.jpg")

        assert decision == "PUBLISH"
        assert pytest.approx(score, rel=1e-3) == 1.0
        assert "dist=0" in reason
