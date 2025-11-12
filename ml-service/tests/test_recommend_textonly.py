import httpx
import pytest
from PIL import Image

from app.config import Settings
from app.index import IndexService
from app.metrics import MetricsCollector
from app.phash import ImageAnalyzer
from app.storage import Storage


@pytest.mark.asyncio
async def test_recommend_text_only(tmp_path):
    settings = Settings(TRAINING_EXPORT_URL="http://backend/api", db_path=str(tmp_path / "db.sqlite"),
                        ocr_enabled=True)
    storage = Storage(settings.db_path)
    storage.init()
    index_service = IndexService()
    metrics = MetricsCollector()
    async with httpx.AsyncClient() as http_client:
        analyzer = ImageAnalyzer(settings, http_client, storage, index_service, metrics)

        class DummyDetector:
            def is_text_dominant(self, _):
                return True

        analyzer.text_detector = DummyDetector()  # type: ignore[assignment]

        async def fake_fetch(_):
            return Image.new("RGB", (32, 32), color="white")

        analyzer.fetch_image = fake_fetch  # type: ignore[assignment]
        decision, _, reason = await analyzer.analyze_candidate("1", "https://candidate/tt.jpg")
        assert decision == "SKIP"
        assert reason == "text-only"
