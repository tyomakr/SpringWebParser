from __future__ import annotations

from enum import Enum
from typing import List, Literal

from pydantic import AnyHttpUrl, BaseModel, Field


class Decision(str, Enum):
    PUBLISH = "PUBLISH"
    REVIEW = "REVIEW"
    SKIP = "SKIP"


class RecommendationCandidate(BaseModel):
    id: str
    url: AnyHttpUrl


class RecommendationRequest(BaseModel):
    candidates: List[RecommendationCandidate] = Field(default_factory=list)


class RecommendationItem(BaseModel):
    id: str
    url: AnyHttpUrl
    score: float
    reason: str
    decision: Decision
    zone: Literal["hit", "gray", "miss"] | None = None
    hash: str | None = None


class ConfigResponse(BaseModel):
    phashMaxDist: int
    grayBand: int
    trainingExportUrl: AnyHttpUrl
    syncEnabled: bool
    syncIntervalSec: int
    apiKeyConfigured: bool


class RecommendationResponse(BaseModel):
    recommendations: List[RecommendationItem]


class SyncStatus(BaseModel):
    processed: int
    last_sync: str | None
