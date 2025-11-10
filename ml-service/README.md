# ML recommendation stub

Минимальный FastAPI-сервис, реализующий контракт из `backend/docs/ml-preview.md`.

## Как работает

- `POST /recommend` принимает JSON `{ "images": [{ "id": "...", "url": "..." }] }`.
- Возвращает `{ "recommendations": [...] }` с теми же `id/url`, простым скором `[0,1)` и решением (`PUBLISH|REVIEW|SKIP`).
- При `score >= 0.75` ответ будет `PUBLISH`, от 0.4 до 0.74 — `REVIEW`, иначе `SKIP`.
- Если задана переменная окружения `ML_PUBLISH_API_KEY`, то сервер требует заголовок `Authorization: Bearer <token>`.

## Запуск

```bash
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

Сервис слушает порт `8000` и готов принимать запросы от Spring backend при настройке `ml.publish.base-url: http://ml-service:8000`.

## Тесты

```bash
pytest
```
