# ML Recommendation Service

FastAPI microservice that stores ML training samples, periodically syncs them from the backend
and scores publication candidates via perceptual hash similarity plus optional OCR text
filtering.

## Configuration

All settings are passed through environment variables (defaults in parentheses):

| Variable | Description |
|----------|-------------|
| `TRAINING_EXPORT_URL` | **Required.** Backend `training/export` endpoint URL. |
| `TRAINING_EXPORT_API_KEY` | Optional API key passed as `Authorization: Bearer ...`. |
| `DB_PATH` (`ml.db`) | Path to SQLite database file. |
| `SYNC_STARTUP` (`true`) | Run background sync loop on startup. |
| `SYNC_INTERVAL_SEC` (`900`) | Periodic sync interval in seconds. |
| `SYNC_PAGE_LIMIT` (`500`) | Page size when pulling training data. |
| `PHASH_MAX_DIST` (`12`) | Maximum Hamming distance to treat candidate as publishable. |
| `OCR_ENABLED` (`false`) | Enable OCR based text-only detection (requires Tesseract). |
| `OCR_MIN_CHARS` (`24`) | Minimum alphanumeric characters to consider image text-only. |
| `MAX_CONCURRENCY` (`8`) | Concurrent image downloads during recommendation. |
| `REQUEST_TIMEOUT` (`5.0`) | HTTP timeout (seconds) for downloads/sync. |
| `PORT` (`8000`) | Default uvicorn port. |

## Local development

```bash
cd ml-service
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

Trigger manual sync:

```bash
curl -X POST http://localhost:8000/sync
```

Example recommendation request:

```bash
curl -X POST http://localhost:8000/recommend \
  -H "Content-Type: application/json" \
  -d '{"candidates":[{"id":"1","url":"https://example.com/1.jpg"}]}'
```

## Tests

```bash
cd ml-service
python -m venv .venv
.\.venv\Scripts\activate   # on Linux/macOS use: source .venv/bin/activate
pip install -r requirements.txt
## Переменные окружения и запуск
Перед запуском убедитесь, что указали обязательные переменные:
```
TRAINING_EXPORT_URL=http://backend:8111/api/vk-history/training/export
ML_PUBLISH_API_KEY=<secret>          # только при необходимости авторизации
```
`ML_PUBLISH_API_KEY` выбирается отдельно (можно оставить пустым для тестов).

### Локально
```
python -m pip install -r requirements.txt
set TRAINING_EXPORT_URL=http://backend:8111/api/vk-history/training/export   # Windows
export TRAINING_EXPORT_URL=http://backend:8111/api/vk-history/training/export  # Linux/macOS
pytest -q
```

`TRAINING_EXPORT_URL` при отладке может указывать на локальный backend (например, `http://localhost:8111/api/vk-history/training/export`).
```

## Docker

```bash
cd ml-service
docker build -t ml-service .
docker run --rm -p 8000:8000 \
  -e TRAINING_EXPORT_URL=http://backend:8111/api/vk-history/training/export \
  ml-service
```

To include Tesseract OCR binaries in the image:

```bash
docker build --build-arg OCR_ENABLED=true -t ml-service-ocr .
```
