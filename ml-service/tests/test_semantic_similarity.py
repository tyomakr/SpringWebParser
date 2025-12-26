import httpx
import numpy as np
import pytest
from PIL import Image

from app.config import Settings
from app.index import IndexService
from app.metrics import MetricsCollector
from app.phash import ImageAnalyzer
from app.storage import Storage


@pytest.mark.asyncio
async def test_semantic_mode_publish(tmp_path):
    settings = Settings(
        TRAINING_EXPORT_URL="http://backend/api",
        db_path=str(tmp_path / "db.sqlite"),
        similarity_mode="semantic",
        semantic_publish_threshold=0.5,
        semantic_gray_threshold=0.2,
    )
    storage = Storage(settings.db_path)
    storage.init()
    index_service = IndexService()
    metrics = MetricsCollector()
    train_vec = np.array([1.0, 0.0, 0.0], dtype=np.float32)
    await index_service.bulk_add_semantic([(train_vec, {"id": "t1"})])

    image = Image.new("RGB", (8, 8), color="white")

    async with httpx.AsyncClient() as http_client:
        analyzer = ImageAnalyzer(settings, http_client, storage, index_service, metrics)

        async def fake_fetch(_):
            return image

        analyzer.fetch_image = fake_fetch  # type: ignore[assignment]
        analyzer.embedding_service.compute = lambda _img: np.array([1.0, 0.0, 0.0], dtype=np.float32)  # type: ignore[assignment]

        decision, score, reason, zone = await analyzer.analyze_candidate("c1", "https://candidate/1.jpg")
        assert decision == "PUBLISH"
        assert zone == "hit"
        assert score == pytest.approx(1.0, rel=1e-3)
        assert "maxSim" in reason
        snapshot = metrics.snapshot()
        assert snapshot["semantic"]["hits"] == 1


@pytest.mark.asyncio
async def test_semantic_mode_gray_when_below_publish(tmp_path):
    settings = Settings(
        TRAINING_EXPORT_URL="http://backend/api",
        db_path=str(tmp_path / "db.sqlite"),
        similarity_mode="semantic",
        semantic_publish_threshold=0.9,
        semantic_gray_threshold=0.4,
    )
    storage = Storage(settings.db_path)
    storage.init()
    index_service = IndexService()
    metrics = MetricsCollector()
    await index_service.bulk_add_semantic([
        (np.array([1.0, 0.0], dtype=np.float32), {"id": "t1"})
    ])

    image = Image.new("RGB", (8, 8), color="white")

    async with httpx.AsyncClient() as http_client:
        analyzer = ImageAnalyzer(settings, http_client, storage, index_service, metrics)

        async def fake_fetch(_):
            return image

        analyzer.fetch_image = fake_fetch  # type: ignore[assignment]
        analyzer.embedding_service.compute = lambda _img: np.array([0.5, 0.5], dtype=np.float32)  # type: ignore[assignment]

        decision, score, reason, zone = await analyzer.analyze_candidate("c1", "https://candidate/2.jpg")
        assert decision == "SKIP"
        assert zone == "gray"
        assert score < settings.semantic_publish_threshold
        assert score >= settings.semantic_gray_threshold
        assert "maxSim" in reason
        snapshot = metrics.snapshot()
        assert snapshot["semantic"]["gray"] == 1
