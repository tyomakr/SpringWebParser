from __future__ import annotations

import threading
from typing import Literal


class MetricsCollector:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._recommend_count = 0
        self._recommend_total_ms = 0.0
        self._recommend_hits = 0
        self._recommend_gray = 0
        self._recommend_miss = 0
        self._semantic_count = 0
        self._semantic_total_ms = 0.0
        self._semantic_hits = 0
        self._semantic_gray = 0
        self._semantic_miss = 0
        self._semantic_max_sim_total = 0.0
        self._sync_last_run: str | None = None
        self._sync_last_duration_ms = 0.0
        self._sync_added = 0

    def record_candidate_result(self, category: Literal["hit", "gray", "miss"]) -> None:
        with self._lock:
            if category == "hit":
                self._recommend_hits += 1
            elif category == "gray":
                self._recommend_gray += 1
            elif category == "miss":
                self._recommend_miss += 1

    def record_recommend_request(self, duration_ms: float) -> None:
        with self._lock:
            self._recommend_count += 1
            self._recommend_total_ms += duration_ms

    def record_semantic_request(self, duration_ms: float, category: Literal["hit", "gray", "miss"] | None,
                                max_similarity: float | None = None) -> None:
        with self._lock:
            self._semantic_count += 1
            self._semantic_total_ms += duration_ms
            if category == "hit":
                self._semantic_hits += 1
            elif category == "gray":
                self._semantic_gray += 1
            elif category == "miss":
                self._semantic_miss += 1
            if max_similarity is not None:
                self._semantic_max_sim_total += max_similarity

    def record_sync(self, last_run: str | None, duration_ms: float, added: int, success: bool = True) -> None:
        with self._lock:
            if success:
                self._sync_last_run = last_run
                self._sync_added = added
            self._sync_last_duration_ms = duration_ms

    def snapshot(self) -> dict:
        with self._lock:
            avg_ms = (self._recommend_total_ms / self._recommend_count) if self._recommend_count else 0.0
            semantic_avg_ms = (self._semantic_total_ms / self._semantic_count) if self._semantic_count else 0.0
            semantic_max_sim_avg = (self._semantic_max_sim_total / self._semantic_count) if self._semantic_count else 0.0
            return {
                "recommend": {
                    "count": self._recommend_count,
                    "avgMs": round(avg_ms, 2),
                    "hits": self._recommend_hits,
                    "gray": self._recommend_gray,
                    "miss": self._recommend_miss,
                },
                "semantic": {
                    "count": self._semantic_count,
                    "avgMs": round(semantic_avg_ms, 2),
                    "hits": self._semantic_hits,
                    "gray": self._semantic_gray,
                    "miss": self._semantic_miss,
                    "maxSimAvg": round(semantic_max_sim_avg, 4),
                },
                "sync": {
                    "lastRun": self._sync_last_run,
                    "lastDurationMs": round(self._sync_last_duration_ms, 2),
                    "added": self._sync_added,
                },
            }


__all__ = ["MetricsCollector"]
