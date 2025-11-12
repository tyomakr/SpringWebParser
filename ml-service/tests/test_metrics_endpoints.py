import httpx
import pytest

from app.config import Settings
from app.main import create_app


@pytest.mark.asyncio
async def test_metrics_and_health(tmp_path):
    settings = Settings(
        TRAINING_EXPORT_URL="http://backend/api",
        db_path=str(tmp_path / "db.sqlite"),
        sync_startup=False,
    )
    app = create_app(settings)

    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        health = await client.get("/health")
        assert health.status_code == 200
        assert health.json()["status"] == "ok"
        assert isinstance(health.json()["indexSize"], int)

        metrics = await client.get("/metrics")
        assert metrics.status_code == 200
        data = metrics.json()
        assert "indexSize" in data
        assert "recommend" in data
        assert "sync" in data

        config = await client.get("/config")
        assert config.status_code == 200
        conf_data = config.json()
        assert conf_data["phashMaxDist"] == settings.phash_max_dist
        assert conf_data["grayBand"] == settings.gray_band
