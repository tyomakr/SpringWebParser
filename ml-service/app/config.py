from __future__ import annotations

from pydantic import AnyHttpUrl, BaseModel, Field, ConfigDict
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    model_config = ConfigDict(env_file=".env", case_sensitive=False, populate_by_name=True)

    training_export_url: AnyHttpUrl = Field(..., alias="TRAINING_EXPORT_URL")
    training_export_api_key: str | None = Field(default=None, alias="TRAINING_EXPORT_API_KEY")
    sync_startup: bool = Field(default=True, alias="SYNC_STARTUP")
    sync_interval_sec: int = Field(default=900, alias="SYNC_INTERVAL_SEC")
    sync_page_limit: int = Field(default=500, alias="SYNC_PAGE_LIMIT")
    phash_max_dist: int = Field(default=12, alias="PHASH_MAX_DIST")
    gray_band: int = Field(default=4, alias="GRAY_BAND")
    index_warmup_limit: int | None = Field(default=None, alias="INDEX_WARMUP_LIMIT")
    ocr_enabled: bool = Field(default=False, alias="OCR_ENABLED")
    ocr_min_chars: int = Field(default=24, alias="OCR_MIN_CHARS")
    max_concurrency: int = Field(default=8, alias="MAX_CONCURRENCY")
    port: int = Field(default=8000, alias="PORT")
    db_path: str = Field(default="ml.db", alias="DB_PATH")
    request_timeout: float = Field(default=5.0, alias="REQUEST_TIMEOUT")


class TrainingExportRecord(BaseModel):
    id: int
    url: str
    hash: str
    createdAt: str
    mlDecision: str | None = None
    mlScore: float | None = None
    mlReason: str | None = None
