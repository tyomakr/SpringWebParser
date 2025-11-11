from __future__ import annotations

import logging
from typing import Optional

from PIL import Image

try:
    import pytesseract
except ImportError:  # pragma: no cover
    pytesseract = None

logger = logging.getLogger(__name__)


class TextDetector:
    def __init__(self, enabled: bool, min_chars: int) -> None:
        self.enabled = enabled and pytesseract is not None
        self.min_chars = min_chars
        if enabled and pytesseract is None:
            logger.warning("OCR requested but pytesseract is not installed")

    def is_text_dominant(self, image: Image.Image) -> bool:
        if not self.enabled:
            return False
        try:
            text = pytesseract.image_to_string(image)
        except Exception as exc:  # pylint: disable=broad-except
            logger.warning("OCR failed: %s", exc)
            return False
        letters = [ch for ch in text if ch.isalpha() or ch.isdigit()]
        return len(letters) >= self.min_chars
