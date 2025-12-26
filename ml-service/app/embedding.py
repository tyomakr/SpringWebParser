from __future__ import annotations

import hashlib

import numpy as np
from PIL import Image


class EmbeddingService:
    """Lightweight, CPU-friendly image embedding."""

    def __init__(self, vector_size: int = 512) -> None:
        self.vector_size = vector_size

    def compute(self, image: Image.Image) -> np.ndarray:
        # Downscale to keep vector small and deterministic
        resized = image.convert("RGB").resize((32, 32))
        arr = np.asarray(resized, dtype=np.float32).reshape(-1)
        if arr.size > self.vector_size:
            # Simple truncation keeps us fast and deterministic
            arr = arr[: self.vector_size]
        else:
            repeats = (self.vector_size + arr.size - 1) // arr.size
            arr = np.tile(arr, repeats)[: self.vector_size]
        return self._normalize(arr)

    def fallback_from_hash(self, value: str) -> np.ndarray:
        digest = hashlib.sha256(value.encode("utf-8")).digest()
        arr = np.frombuffer(digest, dtype=np.uint8).astype(np.float32)
        repeats = (self.vector_size + arr.size - 1) // arr.size
        arr = np.tile(arr, repeats)[: self.vector_size]
        return self._normalize(arr)

    @staticmethod
    def _normalize(arr: np.ndarray) -> np.ndarray:
        norm = float(np.linalg.norm(arr))
        if norm == 0:
            return arr
        return arr / norm


__all__ = ["EmbeddingService"]
