from __future__ import annotations

import asyncio
import logging
from contextlib import asynccontextmanager

import httpx
from fastapi import Depends, FastAPI

from .config import Settings
from .models import Decision, RecommendationRequest, RecommendationResponse, RecommendationItem, SyncStatus
from .phash import ImageAnalyzer
from .storage import Storage
from .syncer import TrainingSyncer

logger = logging.getLogger(__name__)


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or Settings()
    storage = Storage(settings.db_path)
    storage.init()

    app = FastAPI(title="ML Recommendation Service", version="1.0.0")

    @asynccontextmanager
    async def lifespan(app: FastAPI):  # pylint: disable=unused-argument
        async with httpx.AsyncClient() as http_client:
            analyzer = ImageAnalyzer(settings, http_client, storage)
            syncer = TrainingSyncer(settings, http_client, storage, analyzer)
            app.state.http_client = http_client
            app.state.analyzer = analyzer
            app.state.syncer = syncer
            app.state.storage = storage

            stop_event = asyncio.Event()
            sync_task = None
            if settings.sync_startup:
                sync_task = asyncio.create_task(syncer.run_periodic(stop_event))
            try:
                yield
            finally:
                stop_event.set()
                if sync_task:
                    await sync_task

    app.router.lifespan_context = lifespan

    def get_analyzer() -> ImageAnalyzer:
        return app.state.analyzer

    def get_storage() -> Storage:
        return app.state.storage

    def get_syncer() -> TrainingSyncer:
        return app.state.syncer

    @app.post("/recommend", response_model=RecommendationResponse)
    async def recommend(payload: RecommendationRequest,
                        analyzer: ImageAnalyzer = Depends(get_analyzer),
                        storage: Storage = Depends(get_storage)) -> RecommendationResponse:
        positives = analyzer.load_positives()
        recommendations: list[RecommendationItem] = []
        tasks = [
            analyzer.analyze_candidate(candidate.id, str(candidate.url), positives)
            for candidate in payload.candidates
        ]
        results = await asyncio.gather(*tasks)
        for candidate, (decision, score, reason) in zip(payload.candidates, results, strict=False):
            recommendations.append(
                RecommendationItem(
                    id=candidate.id,
                    url=candidate.url,
                    score=round(score, 4),
                    reason=reason,
                    decision=Decision(decision),
                )
            )
        return RecommendationResponse(recommendations=recommendations)

    @app.post("/sync", response_model=SyncStatus)
    async def sync_endpoint(syncer: TrainingSyncer = Depends(get_syncer)) -> SyncStatus:
        result = await syncer.run_once()
        return SyncStatus(processed=result["processed"], last_sync=result["last_sync"])

    return app


app = create_app()
