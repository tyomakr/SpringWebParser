from __future__ import annotations

import hashlib
import logging
import threading

import numpy as np
from PIL import Image

from .config import Settings

logger = logging.getLogger(__name__)


class LiteEmbeddingService:
    """Lightweight, CPU-friendly image embedding."""

    def __init__(self, vector_size: int = 128, hist_bins: int = 16, edge_bins: int = 16) -> None:
        self.vector_size = vector_size
        self.hist_bins = hist_bins
        self.edge_bins = edge_bins

    def compute(self, image: Image.Image) -> np.ndarray:
        resized = image.convert("RGB").resize((64, 64))
        arr = np.asarray(resized, dtype=np.float32) / 255.0

        # Color histogram captures rough content palette.
        hist_parts = []
        for channel in range(3):
            channel_data = arr[:, :, channel].reshape(-1)
            hist, _ = np.histogram(
                channel_data, bins=self.hist_bins, range=(0.0, 1.0), density=True
            )
            hist_parts.append(hist)
        color_hist = np.concatenate(hist_parts)

        # Edge magnitude histogram captures structure without relying on exact pixels.
        gray = 0.299 * arr[:, :, 0] + 0.587 * arr[:, :, 1] + 0.114 * arr[:, :, 2]
        gx = np.diff(gray, axis=1)
        gy = np.diff(gray, axis=0)
        mag = np.sqrt(gx[:-1, :] ** 2 + gy[:, :-1] ** 2)
        mag = np.clip(mag, 0.0, 1.0)
        edge_hist, _ = np.histogram(mag.reshape(-1), bins=self.edge_bins, range=(0.0, 1.0), density=True)

        features = np.concatenate([color_hist, edge_hist]).astype(np.float32)
        if features.size > self.vector_size:
            features = features[: self.vector_size]
        else:
            repeats = (self.vector_size + features.size - 1) // features.size
            features = np.tile(features, repeats)[: self.vector_size]
        return self._normalize(features)

    def fallback_from_hash(self, value: str) -> np.ndarray:
        return _hash_fallback(value, self.vector_size)

    @staticmethod
    def _normalize(arr: np.ndarray) -> np.ndarray:
        norm = float(np.linalg.norm(arr))
        if norm == 0:
            return arr
        return arr / norm


class ClipEmbeddingService:
    def __init__(self, model_name: str, pretrained: str, device: str) -> None:
        try:
            import open_clip  # type: ignore
            import torch  # type: ignore
        except Exception as exc:  # pylint: disable=broad-except
            raise RuntimeError("CLIP backend requires open_clip_torch and torch") from exc

        self._torch = torch
        self._open_clip = open_clip
        self.device = device or "cpu"
        self.model, _, self.preprocess = open_clip.create_model_and_transforms(
            model_name, pretrained=pretrained
        )
        self.model.eval()
        self.model.to(self.device)
        self.vector_size = int(getattr(self.model.visual, "output_dim", 512))

    def compute(self, image: Image.Image) -> np.ndarray:
        torch = self._torch
        with torch.no_grad():
            tensor = self.preprocess(image).unsqueeze(0).to(self.device)
            features = self.model.encode_image(tensor)
            features = features / features.norm(dim=-1, keepdim=True)
            return features.cpu().numpy().reshape(-1).astype(np.float32)

    def fallback_from_hash(self, value: str) -> np.ndarray:
        return _hash_fallback(value, self.vector_size)


class EmbeddingService:
    def __init__(self, settings: Settings) -> None:
        backend = (settings.semantic_backend or "lite").lower()
        self.backend = backend
        self._lock = threading.Lock()
        self._impl = None
        self._settings = settings
        if backend != "clip":
            self._impl = LiteEmbeddingService()

    def compute(self, image: Image.Image) -> np.ndarray:
        return self._get_impl().compute(image)

    def fallback_from_hash(self, value: str) -> np.ndarray:
        return self._get_impl().fallback_from_hash(value)

    def _get_impl(self):
        if self._impl is not None:
            return self._impl
        with self._lock:
            if self._impl is not None:
                return self._impl
            try:
                self._impl = ClipEmbeddingService(
                    self._settings.clip_model_name,
                    self._settings.clip_pretrained,
                    self._settings.clip_device,
                )
            except Exception as exc:  # pylint: disable=broad-except
                logger.warning("CLIP backend unavailable (%s), falling back to lite", exc)
                self._impl = LiteEmbeddingService()
                self.backend = "lite"
        return self._impl


def _hash_fallback(value: str, vector_size: int) -> np.ndarray:
    digest = hashlib.sha256(value.encode("utf-8")).digest()
    arr = np.frombuffer(digest, dtype=np.uint8).astype(np.float32)
    repeats = (vector_size + arr.size - 1) // arr.size
    arr = np.tile(arr, repeats)[: vector_size]
    norm = float(np.linalg.norm(arr))
    if norm == 0:
        return arr
    return arr / norm


__all__ = ["EmbeddingService", "LiteEmbeddingService", "ClipEmbeddingService"]
