from __future__ import annotations

import logging

from PIL import Image
import numpy as np

try:
    import pytesseract
    from pytesseract import Output
except ImportError:  # pragma: no cover
    pytesseract = None
    Output = None

logger = logging.getLogger(__name__)


class TextDetector:
    def __init__(
        self,
        enabled: bool,
        min_chars: int,
        min_conf: int,
        text_area_min: float,
        text_area_max: float,
        low_detail_threshold: float,
        low_detail_std_threshold: float,
    ) -> None:
        self.enabled = enabled and pytesseract is not None
        self.min_chars = min_chars
        self.min_conf = min_conf
        self.text_area_min = text_area_min
        self.text_area_max = text_area_max
        self.low_detail_threshold = low_detail_threshold
        self.low_detail_std_threshold = low_detail_std_threshold
        if enabled and pytesseract is None:
            logger.warning("OCR requested but pytesseract is not installed")

    def is_text_dominant(self, image: Image.Image) -> bool:
        return self.analyze(image)["textDominant"]

    def analyze(self, image: Image.Image) -> dict:
        if not self.enabled:
            return {
                "enabled": False,
                "textDominant": False,
                "charCount": 0,
                "textAreaRatio": 0.0,
                "outsideEdge": 0.0,
                "outsideStd": 0.0,
                "reason": None,
            }
        try:
            data = pytesseract.image_to_data(image, output_type=Output.DICT)
        except Exception as exc:  # pylint: disable=broad-except
            logger.warning("OCR failed: %s", exc)
            return {
                "enabled": True,
                "textDominant": False,
                "charCount": 0,
                "textAreaRatio": 0.0,
                "outsideEdge": 0.0,
                "outsideStd": 0.0,
                "reason": "ocr-error",
            }
        width, height = image.size
        total_area = max(1, width * height)
        text_area = 0
        char_count = 0
        boxes: list[tuple[int, int, int, int]] = []
        for i in range(len(data.get("text", []))):
            raw = data["text"][i]
            if not raw:
                continue
            try:
                conf = float(data["conf"][i])
            except Exception:  # pylint: disable=broad-except
                conf = -1
            if conf < self.min_conf:
                continue
            text = raw.strip()
            if not text:
                continue
            letters = [ch for ch in text if ch.isalpha() or ch.isdigit()]
            if not letters:
                continue
            try:
                x = int(data["left"][i])
                y = int(data["top"][i])
                w = int(data["width"][i])
                h = int(data["height"][i])
            except Exception:  # pylint: disable=broad-except
                continue
            if w <= 0 or h <= 0:
                continue
            text_area += w * h
            char_count += len(letters)
            boxes.append((x, y, w, h))

        area_ratio = min(1.0, text_area / total_area)
        outside_edge, outside_std = self._outside_stats(image, boxes)
        is_text_heavy = area_ratio >= self.text_area_max
        is_low_detail = (
            outside_edge <= self.low_detail_threshold
            and outside_std <= self.low_detail_std_threshold
        )
        is_text_dominant = (
            char_count >= self.min_chars
            and area_ratio >= self.text_area_min
            and (is_text_heavy or is_low_detail)
        )
        reason = None
        if is_text_dominant:
            reason = "text-heavy" if is_text_heavy else "low-detail"
        logger.debug(
            "OCR stats chars=%s area=%.3f outside_edge=%.4f outside_std=%.4f heavy=%s low_detail=%s dominant=%s",
            char_count,
            area_ratio,
            outside_edge,
            outside_std,
            is_text_heavy,
            is_low_detail,
            is_text_dominant,
        )
        return {
            "enabled": True,
            "textDominant": is_text_dominant,
            "charCount": char_count,
            "textAreaRatio": area_ratio,
            "outsideEdge": outside_edge,
            "outsideStd": outside_std,
            "reason": reason,
        }

    @staticmethod
    def _outside_stats(image: Image.Image, boxes: list[tuple[int, int, int, int]]) -> tuple[float, float]:
        gray = np.asarray(image.convert("L"), dtype=np.float32) / 255.0
        if gray.shape[0] < 3 or gray.shape[1] < 3:
            return 0.0, 0.0
        gx = np.diff(gray, axis=1)
        gy = np.diff(gray, axis=0)
        mag = np.sqrt(gx[:-1, :] ** 2 + gy[:, :-1] ** 2)
        mask = np.ones_like(mag, dtype=bool)
        max_y, max_x = mag.shape
        for x, y, w, h in boxes:
            x0 = max(0, x)
            y0 = max(0, y)
            x1 = min(max_x, x + w)
            y1 = min(max_y, y + h)
            if x0 < x1 and y0 < y1:
                mask[y0:y1, x0:x1] = False
        outside_mag = mag[mask]
        if outside_mag.size == 0:
            return 0.0, 0.0
        outside_gray = gray[1:1 + max_y, 1:1 + max_x][mask]
        if outside_gray.size == 0:
            outside_std = 0.0
        else:
            outside_std = float(outside_gray.std())
        return float(outside_mag.mean()), outside_std
