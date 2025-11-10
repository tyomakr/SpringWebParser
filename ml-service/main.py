import os
from enum import Enum
from typing import List, Optional

from fastapi import Depends, FastAPI, Header, HTTPException, status
from pydantic import BaseModel

class Decision(str, Enum):
    PUBLISH = "PUBLISH"
    REVIEW = "REVIEW"
    SKIP = "SKIP"


class ImageCandidate(BaseModel):
    id: str
    url: str


class RecommendationRequest(BaseModel):
    images: List[ImageCandidate]


class RecommendationItem(BaseModel):
    id: str
    url: str
    score: float
    reason: str
    decision: Decision


class RecommendationResponse(BaseModel):
    recommendations: List[RecommendationItem]


def get_expected_api_key() -> Optional[str]:
    return os.getenv("ML_PUBLISH_API_KEY")


def get_api_key(header: Optional[str] = Header(None, alias="Authorization")) -> None:
    expected = get_expected_api_key()
    if not expected:
        return
    if header is None or not header.startswith("Bearer "):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid API key")
    token = header.split(" ", 1)[1]
    if token != expected:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid API key")


def score_from_url(url: str) -> float:
    # deterministic hash: sum of code points modulo 100
    total = sum(ord(ch) for ch in url)
    return (total % 100) / 100.0


def decision_from_score(score: float) -> (Decision, str):
    if score >= 0.75:
        return Decision.PUBLISH, "high score"
    if score >= 0.4:
        return Decision.REVIEW, "medium score"
    return Decision.SKIP, "low score"


app = FastAPI(
    title="ML recommendation stub",
    description="Simple deterministic service that implements /recommend used by Spring backend.",
    version="0.1.0",
)


@app.post("/recommend", response_model=RecommendationResponse)
def recommend(payload: RecommendationRequest, _auth=Depends(get_api_key)):
    items: List[RecommendationItem] = []
    for image in payload.images:
        score = score_from_url(image.url)
        decision, reason = decision_from_score(score)
        items.append(
            RecommendationItem(
                id=image.id,
                url=image.url,
                score=score,
                reason=reason,
                decision=decision,
            )
        )
    return RecommendationResponse(recommendations=items)
