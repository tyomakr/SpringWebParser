import asyncio
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
        assert index_service.size() == 1
        snapshot = metrics.snapshot()
        assert snapshot["sync"]["added"] == 1
        assert snapshot["sync"]["lastRun"] is not None
        assert snapshot["sync"]["lastDurationMs"] > 0


@pytest.mark.asyncio
async def test_syncer_request_error_does_not_clear_index(tmp_path):
    settings = Settings(
        TRAINING_EXPORT_URL="http://backend/api",
        db_path=str(tmp_path / "db.sqlite"),
        sync_page_limit=1,
        sync_startup=False,
    )
    storage = Storage(settings.db_path)
    storage.init()
    storage.upsert_positive(hash="h", url="u", phash="ff", created_at="2024-01-01T00:00:00Z")
    index_service = IndexService()
    await index_service.bulk_add([(255, {"id": 1, "url": "u", "hash": "h", "created_at": "2024-01-01T00:00:00Z"})])
    metrics = MetricsCollector()

    def err_handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("boom")

    transport = httpx.MockTransport(err_handler)

    async with httpx.AsyncClient(transport=transport) as http_client:
        analyzer = ImageAnalyzer(settings, http_client, storage, index_service, metrics)
        syncer = TrainingSyncer(settings, http_client, storage, analyzer, index_service, metrics)
        with pytest.raises(httpx.RequestError):
            await syncer.run_once()
        assert index_service.size() == 1
        snapshot = metrics.snapshot()
        assert snapshot["sync"]["lastRun"] is not None


@pytest.mark.asyncio
async def test_run_periodic_stops_cleanly(monkeypatch):
    settings = Settings(
        TRAINING_EXPORT_URL="http://backend/api",
        db_path=":memory:",
        sync_interval_sec=1,
        sync_startup=False,
    )
    storage = Storage(settings.db_path)
    storage.init()
    index_service = IndexService()
    metrics = MetricsCollector()
    dummy_transport = httpx.MockTransport(lambda request: httpx.Response(200, json=[]))

    async with httpx.AsyncClient(transport=dummy_transport) as http_client:
        analyzer = ImageAnalyzer(settings, http_client, storage, index_service, metrics)
        syncer = TrainingSyncer(settings, http_client, storage, analyzer, index_service, metrics)

        calls: list[int] = []

        async def fake_run_once():
            calls.append(1)

        monkeypatch.setattr(syncer, "run_once", fake_run_once)
        stop_event = asyncio.Event()
        task = asyncio.create_task(syncer.run_periodic(stop_event))
        await asyncio.sleep(0.1)
        stop_event.set()
        await task
        assert calls
