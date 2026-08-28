# AI / Recommendation (план)

В MVP AI‑модуль не реализуется, но архитектура резервирует место под него.

## Планы

- pHash для дедупликации изображений
- Эмбеддинги (DJL + ONNX) для семантической близости
- Опциональный удалённый GPU‑воркер ("accelerator node")

## Контракты

- `EmbeddingProvider` с режимами:
  - `LOCAL` — CPU (заглушка)
  - `REMOTE` — удалённый воркер по HTTP

## Конфиг

- `akcp.ai.mode=local|remote`
- `akcp.ai.remote.url`

В базу данных добавлен тип job `COMPUTE_EMBEDDING` и `COMPUTE_PHASH`.
