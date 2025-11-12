import httpx
import pytest
from PIL import Image

from app.config import Settings
from app.index import IndexService
from app.metrics import MetricsCollector
from app.phash import ImageAnalyzer
from app.storage import Storage
from app.syncer import TrainingSyncer


@pytest.mark.asyncio
async def test_syncer_fetches_and_persists(tmp_path):
    settings = Settings(
        TRAINING_EXPORT_URL="http://backend/api",
        db_path=str(tmp_path / "db.sqlite"),
        sync_page_limit=1,
        sync_startup=False,
    )
    storage = Storage(settings.db_path)
    storage.init()

    sample_image = Image.new("RGB", (16, 16), color="white")

    def handler(request: httpx.Request) -> httpx.Response:
        params = dict(request.url.params)
        if params.get("offset") == "0":
            data = [{
                "id": 1,
                "url": "https://example.com/image.jpg",
                "hash": "hash-sample",
                "createdAt": "2024-01-01T00:00:00Z",
                "mlDecision": None,
                "mlScore": None,
                "mlReason": None,
            }]
        else:
            data = []
        return httpx.Response(200, json=data)

    transport = httpx.MockTransport(handler)
    index_service = IndexService()
    metrics = MetricsCollector()

    async with httpx.AsyncClient(transport=transport) as http_client:
        analyzer = ImageAnalyzer(settings, http_client, storage, index_service, metrics)

        async def fake_fetch(_):
            return sample_image

        analyzer.fetch_image = fake_fetch  # type: ignore[assignment]
        syncer = TrainingSyncer(settings, http_client, storage, analyzer, index_service, metrics)

        result = await syncer.run_once()
        assert result["processed"] == 1
        positives = storage.list_active_positives()
        assert len(positives) == 1
        assert positives[0]["hash"] == "hash-sample"
