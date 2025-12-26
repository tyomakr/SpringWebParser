from __future__ import annotations

import asyncio
import logging
from contextlib import asynccontextmanager

import httpx
from fastapi import Depends, FastAPI

from .config import Settings
from .index import IndexService
from .metrics import MetricsCollector
from .models import (
    ConfigResponse,
    Decision,
    RecommendationItem,
    RecommendationRequest,
    RecommendationResponse,
    SyncStatus,
)
from .phash import ImageAnalyzer
from .storage import Storage
from .syncer import TrainingSyncer

logger = logging.getLogger(__name__)


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or Settings()
    if settings.sync_debug:
        logging.getLogger().setLevel(logging.DEBUG)
        logging.getLogger("uvicorn.error").setLevel(logging.DEBUG)
        logging.getLogger("uvicorn.access").setLevel(logging.DEBUG)
        logging.getLogger("app.syncer").setLevel(logging.DEBUG)
    storage = Storage(settings.db_path)
    storage.init()
    index_service = IndexService()
    metrics = MetricsCollector()

    app = FastAPI(title="ML Recommendation Service", version="1.0.0")
    app.state.index_service = index_service
    app.state.metrics = metrics

    @asynccontextmanager
    async def lifespan(app: FastAPI):  # pylint: disable=unused-argument
        async with httpx.AsyncClient() as http_client:
            await index_service.warmup(storage.iter_positives(), settings.index_warmup_limit)
            analyzer = ImageAnalyzer(settings, http_client, storage, index_service, metrics)
            syncer = TrainingSyncer(
                settings, http_client, storage, analyzer, index_service, metrics
            )
            app.state.http_client = http_client
            app.state.analyzer = analyzer
            app.state.syncer = syncer
            app.state.storage = storage
            app.state.index_service = index_service
            app.state.metrics = metrics

            stop_event = asyncio.Event()
            sync_task = None
            if settings.sync_startup:
                # Try to populate index before serving requests (with short retries so startup isn't too slow).
                for attempt in range(1, 4):
                    try:
                        await syncer.run_once()
                        break
                    except Exception:  # pylint: disable=broad-except
                        logger.exception("Initial training sync failed (attempt %s/3)", attempt)
                        await asyncio.sleep(5)
                sync_task = asyncio.create_task(syncer.run_periodic(stop_event))
            try:
                yield
            finally:
                stop_event.set()
                if sync_task:
                    try:
                        await sync_task
                    except Exception:  # pylint: disable=broad-except
                        logger.exception("Background sync task failed during shutdown")

    app.router.lifespan_context = lifespan

    def get_analyzer() -> ImageAnalyzer:
        return app.state.analyzer

    def get_storage() -> Storage:
        return app.state.storage

    def get_index_service() -> IndexService:
        return app.state.index_service

    def get_metrics_collector() -> MetricsCollector:
        return app.state.metrics

    def get_syncer() -> TrainingSyncer:
        return app.state.syncer

    @app.post("/recommend", response_model=RecommendationResponse, response_model_exclude_none=True)
    async def recommend(payload: RecommendationRequest,
                        analyzer: ImageAnalyzer = Depends(get_analyzer),
                        syncer: TrainingSyncer = Depends(get_syncer),
                        index_service: IndexService = Depends(get_index_service)) -> RecommendationResponse:
        # If index is empty, try a one-off sync so recommendations have training data.
        needs_sync = index_service.size() == 0
        if settings.similarity_mode in {"semantic", "hybrid"}:
            needs_sync = needs_sync and index_service.semantic_size() == 0
        if needs_sync:
            try:
                await syncer.run_once()
            except Exception:  # pylint: disable=broad-except
                logger.exception("On-demand training sync failed before recommend")
        recommendations: list[RecommendationItem] = []
        tasks = [
            analyzer.analyze_candidate(candidate.id, str(candidate.url))
            for candidate in payload.candidates
        ]
        results = await asyncio.gather(*tasks)
        for candidate, (decision, score, reason, zone) in zip(payload.candidates, results, strict=False):
            item_data = {
                "id": candidate.id,
                "url": candidate.url,
                "score": round(score, 4),
                "reason": reason,
                "decision": Decision(decision),
                "hash": ImageAnalyzer.compute_hash(str(candidate.url)),
            }
            if zone is not None:
                item_data["zone"] = zone
            recommendations.append(RecommendationItem(**item_data))
        return RecommendationResponse(recommendations=recommendations)

    @app.post("/sync", response_model=SyncStatus)
    async def sync_endpoint(syncer: TrainingSyncer = Depends(get_syncer)) -> SyncStatus:
        result = await syncer.run_once()
        return SyncStatus(processed=result["processed"], last_sync=result["last_sync"])

    @app.get("/health")
    def health(index_service: IndexService = Depends(get_index_service)):
        return {"status": "ok", "indexSize": index_service.size()}

    @app.get("/metrics")
    def metrics_endpoint(
        index_service: IndexService = Depends(get_index_service),
        metrics_collector: MetricsCollector = Depends(get_metrics_collector),
    ):
        data = metrics_collector.snapshot()
        return {"indexSize": index_service.size(), **data}

    @app.get("/config", response_model=ConfigResponse)
    def config() -> ConfigResponse:
        return ConfigResponse(
            phashMaxDist=settings.phash_max_dist,
            grayBand=settings.gray_band,
            trainingExportUrl=settings.training_export_url,
            syncEnabled=settings.sync_startup,
            syncIntervalSec=settings.sync_interval_sec,
            apiKeyConfigured=bool(settings.training_export_api_key),
            similarityMode=settings.similarity_mode,
            semanticPublishThreshold=settings.semantic_publish_threshold,
            semanticGrayThreshold=settings.semantic_gray_threshold,
        )

    return app


app = create_app()
