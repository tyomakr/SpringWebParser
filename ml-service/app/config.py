from __future__ import annotations

import os

from pydantic import AnyHttpUrl, BaseModel, Field, ConfigDict
from pydantic_settings import BaseSettings
from pydantic import model_validator


class Settings(BaseSettings):
    model_config = ConfigDict(
        env_file=".env",
        case_sensitive=False,
        populate_by_name=True,
        extra="ignore",
    )

    training_export_url: AnyHttpUrl = Field(..., alias="TRAINING_EXPORT_URL")
    training_export_api_key: str | None = None
    sync_startup: bool = Field(default=True, alias="SYNC_STARTUP")
    sync_interval_sec: int = Field(default=900, alias="SYNC_INTERVAL_SEC")
    sync_page_limit: int = Field(default=500, alias="SYNC_PAGE_LIMIT")
    sync_max_pages_per_run: int = Field(default=0, alias="SYNC_MAX_PAGES_PER_RUN")
    sync_debug: bool = Field(default=False, alias="SYNC_DEBUG")
    similarity_mode: str = Field(default="phash", alias="SIMILARITY_MODE")
    semantic_publish_threshold: float = Field(default=0.35, alias="SEMANTIC_MIN_SIMILARITY_PUBLISH")
    semantic_gray_threshold: float = Field(default=0.30, alias="SEMANTIC_MIN_SIMILARITY_GRAY")
    phash_max_dist: int = Field(default=12, alias="PHASH_MAX_DIST")
    gray_band: int = Field(default=4, alias="GRAY_BAND")
    index_warmup_limit: int | None = Field(default=None, alias="INDEX_WARMUP_LIMIT")
    ocr_enabled: bool = Field(default=False, alias="OCR_ENABLED")
    ocr_min_chars: int = Field(default=24, alias="OCR_MIN_CHARS")
    max_concurrency: int = Field(default=8, alias="MAX_CONCURRENCY")
    port: int = Field(default=8000, alias="PORT")
    db_path: str = Field(default="ml.db", alias="DB_PATH")
    request_timeout: float = Field(default=5.0, alias="REQUEST_TIMEOUT")

    @staticmethod
    def _clean_bool(value):
        if isinstance(value, str):
            value = value.strip().strip('"').strip("'")
            if value.lower() in {"true", "1", "yes", "y", "on"}:
                return True
            if value.lower() in {"false", "0", "no", "n", "off"}:
                return False
        return value

    @staticmethod
    def _clean_int(value):
        if isinstance(value, str):
            try:
                return int(value.strip().strip('"').strip("'"))
            except ValueError:
                return value
        return value

    @model_validator(mode="before")
    @classmethod
    def normalize_env(cls, data):
        if isinstance(data, dict):
            if "sync_startup" in data:
                data["sync_startup"] = cls._clean_bool(data["sync_startup"])
            if "sync_interval_sec" in data:
                data["sync_interval_sec"] = cls._clean_int(data["sync_interval_sec"])
            if "sync_max_pages_per_run" in data:
                data["sync_max_pages_per_run"] = cls._clean_int(data["sync_max_pages_per_run"])
            if "sync_debug" in data:
                data["sync_debug"] = cls._clean_bool(data["sync_debug"])
            if not data.get("training_export_api_key"):
                from_env = os.getenv("TRAINING_EXPORT_API_KEY") or os.getenv("ML_PUBLISH_API_KEY")
                if from_env:
                    data["training_export_api_key"] = from_env
            if "similarity_mode" in data and isinstance(data["similarity_mode"], str):
                data["similarity_mode"] = data["similarity_mode"].lower()
        return data


class TrainingExportRecord(BaseModel):
    id: int
    url: str
    hash: str
    createdAt: str
    mlDecision: str | None = None
    mlScore: float | None = None
    mlReason: str | None = None
