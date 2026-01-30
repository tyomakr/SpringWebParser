from __future__ import annotations

import asyncio
import json
import os
from dataclasses import dataclass
from datetime import datetime, timezone
from io import BytesIO
from pathlib import Path
from typing import Iterable

import httpx
from PIL import Image

from .config import Settings, TrainingExportRecord
from .text_detector import TextDetector


@dataclass(frozen=True)
class OcrDiagnosticsResult:
    report: dict
    output_path: Path


def _expected_label(filename: str) -> str:
    lower = filename.lower()
    if lower.startswith("filtered_text_sample"):
        return "filtered"
    if lower.startswith("not_text_sample"):
        return "keep"
    return "unknown"


def _iter_images(path: Path) -> Iterable[Path]:
    if not path.exists():
        return []
    return sorted(p for p in path.iterdir() if p.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp"})


def build_report(settings: Settings, input_dir: str) -> dict:
    detector = TextDetector(
        settings.ocr_enabled,
        settings.ocr_min_chars,
        settings.ocr_min_conf,
        settings.ocr_text_area_min,
        settings.ocr_text_area_max,
        settings.ocr_low_detail_threshold,
        settings.ocr_low_detail_std_threshold,
    )
    root = Path(input_dir)
    samples = []
    false_positive = []
    false_negative = []
    error_samples = []
    total = 0
    predicted_text = 0
    expected_filtered = 0
    expected_keep = 0

    for path in _iter_images(root):
        total += 1
        expected = _expected_label(path.name)
        if expected == "filtered":
            expected_filtered += 1
        elif expected == "keep":
            expected_keep += 1
        try:
            image = Image.open(path).convert("RGB")
        except Exception as exc:  # pylint: disable=broad-except
            error_samples.append({"file": path.name, "error": str(exc)})
            continue
        analysis = detector.analyze(image)
        text_dominant = analysis["textDominant"]
        if text_dominant:
            predicted_text += 1
        if expected == "keep" and text_dominant:
            false_positive.append(path.name)
        if expected == "filtered" and not text_dominant:
            false_negative.append(path.name)
        samples.append({
            "file": path.name,
            "expected": expected,
            "textDominant": text_dominant,
            "reason": analysis["reason"],
            "charCount": analysis["charCount"],
            "textAreaRatio": round(float(analysis["textAreaRatio"]), 4),
            "outsideEdge": round(float(analysis["outsideEdge"]), 4),
            "outsideStd": round(float(analysis["outsideStd"]), 4),
        })

    report = {
        "generatedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "source": "files",
        "inputDir": str(root),
        "settings": {
            "ocrEnabled": settings.ocr_enabled,
            "minChars": settings.ocr_min_chars,
            "minConf": settings.ocr_min_conf,
            "textAreaMin": settings.ocr_text_area_min,
            "textAreaMax": settings.ocr_text_area_max,
            "lowDetailThreshold": settings.ocr_low_detail_threshold,
            "lowDetailStdThreshold": settings.ocr_low_detail_std_threshold,
        },
        "summary": {
            "total": total,
            "predictedTextDominant": predicted_text,
            "kept": total - predicted_text,
            "expectedFiltered": expected_filtered,
            "expectedKeep": expected_keep,
            "falsePositive": len(false_positive),
            "falseNegative": len(false_negative),
            "errors": len(error_samples),
        },
        "mismatches": {
            "falsePositive": false_positive,
            "falseNegative": false_negative,
        },
        "errors": error_samples,
        "samples": samples,
    }
    return report


async def _fetch_image(http_client: httpx.AsyncClient, url: str, timeout: float) -> Image.Image:
    resp = await http_client.get(url, timeout=httpx.Timeout(timeout, connect=timeout))
    resp.raise_for_status()
    return Image.open(BytesIO(resp.content)).convert("RGB")


async def build_report_from_training(settings: Settings,
                                     http_client: httpx.AsyncClient,
                                     limit: int,
                                     offset: int) -> dict:
    detector = TextDetector(
        settings.ocr_enabled,
        settings.ocr_min_chars,
        settings.ocr_min_conf,
        settings.ocr_text_area_min,
        settings.ocr_text_area_max,
        settings.ocr_low_detail_threshold,
        settings.ocr_low_detail_std_threshold,
    )
    headers = {}
    if settings.training_export_api_key:
        headers["Authorization"] = f"Bearer {settings.training_export_api_key}"
    params = {"limit": limit, "offset": offset}
    resp = await http_client.get(
        str(settings.training_export_url),
        params=params,
        headers=headers,
        timeout=httpx.Timeout(settings.request_timeout, connect=settings.request_timeout),
    )
    resp.raise_for_status()
    records = [TrainingExportRecord(**item) for item in resp.json()]

    samples = []
    errors = []
    total = 0
    predicted_text = 0
    skipped_fetch = 0
    semaphore = asyncio.Semaphore(settings.max_concurrency)

    async def analyze_record(record: TrainingExportRecord) -> None:
        nonlocal total, predicted_text, skipped_fetch
        async with semaphore:
            total += 1
            try:
                image = await _fetch_image(http_client, record.url, settings.request_timeout)
            except Exception as exc:  # pylint: disable=broad-except
                skipped_fetch += 1
                errors.append({"id": record.id, "url": record.url, "error": str(exc)})
                return
            analysis = detector.analyze(image)
            if analysis["textDominant"]:
                predicted_text += 1
            samples.append({
                "id": record.id,
                "url": record.url,
                "textDominant": analysis["textDominant"],
                "reason": analysis["reason"],
                "charCount": analysis["charCount"],
                "textAreaRatio": round(float(analysis["textAreaRatio"]), 4),
                "outsideEdge": round(float(analysis["outsideEdge"]), 4),
                "outsideStd": round(float(analysis["outsideStd"]), 4),
            })

    await asyncio.gather(*(analyze_record(record) for record in records))

    report = {
        "generatedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "source": "training-export",
        "limit": limit,
        "offset": offset,
        "settings": {
            "ocrEnabled": settings.ocr_enabled,
            "minChars": settings.ocr_min_chars,
            "minConf": settings.ocr_min_conf,
            "textAreaMin": settings.ocr_text_area_min,
            "textAreaMax": settings.ocr_text_area_max,
            "lowDetailThreshold": settings.ocr_low_detail_threshold,
            "lowDetailStdThreshold": settings.ocr_low_detail_std_threshold,
        },
        "summary": {
            "total": total,
            "predictedTextDominant": predicted_text,
            "kept": total - predicted_text,
            "errors": len(errors),
            "fetchFailed": skipped_fetch,
        },
        "errors": errors,
        "samples": samples,
    }
    return report


def load_report(path: str) -> dict | None:
    report_path = Path(path)
    if not report_path.exists():
        return None
    return json.loads(report_path.read_text(encoding="utf-8"))


def write_report(report: dict, output_path: str) -> OcrDiagnosticsResult:
    path = Path(output_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, ensure_ascii=True, indent=2), encoding="utf-8")
    return OcrDiagnosticsResult(report=report, output_path=path)


def build_report_from_env(input_dir: str | None = None, output_path: str | None = None) -> OcrDiagnosticsResult:
    if not os.getenv("TRAINING_EXPORT_URL"):
        os.environ["TRAINING_EXPORT_URL"] = "http://localhost"
    if not os.getenv("OCR_ENABLED"):
        os.environ["OCR_ENABLED"] = "true"
    settings = Settings()
    report = build_report(settings, input_dir or settings.ocr_diagnostics_dir or "for-gpt")
    result = write_report(report, output_path or settings.ocr_diagnostics_path)
    return result
