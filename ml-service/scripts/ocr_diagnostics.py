from __future__ import annotations

import argparse
import sys

import asyncio

import httpx

from app.config import Settings
from app.ocr_diagnostics import build_report_from_env, build_report_from_training, write_report


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate OCR diagnostics report.")
    parser.add_argument("--input", help="Input directory with sample images")
    parser.add_argument("--output", help="Output JSON path (default from OCR_DIAGNOSTICS_PATH)")
    parser.add_argument("--limit", type=int, help="Limit when sampling from training export")
    parser.add_argument("--offset", type=int, help="Offset when sampling from training export")
    args = parser.parse_args()

    if args.input:
        result = build_report_from_env(args.input, args.output)
    else:
        settings = Settings()
        limit = args.limit or settings.ocr_diagnostics_limit
        offset = args.offset or settings.ocr_diagnostics_offset
        async def run():
            async with httpx.AsyncClient() as client:
                report = await build_report_from_training(settings, client, limit, offset)
                return write_report(report, args.output or settings.ocr_diagnostics_path)
        result = asyncio.run(run())
    print(f"OCR diagnostics report written to {result.output_path}")
    print(f"Total samples: {result.report['summary'].get('total')}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
