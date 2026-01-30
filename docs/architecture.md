# Архитектура AKCP

AKCP построен как монолит с модульной структурой и «hexagonal‑ish» границами:

- Контроллеры → Сервисы → Репозитории
- Доменные модели в `akcp-core`
- Отдельные модули по зонам ответственности (ingestion, library, publishing, jobs, auth)

## Основные домены

- **Item** — единица контента (текст + источник + вложения)
- **Attachment** — вложение (в MVP — изображения)
- **Tag** — метка для фильтрации
- **Job** — фоновые операции (публикация, вычисления pHash/эмбеддингов)
- **SourceRef / PublishTarget** — ссылки на источники/цели

## Потоки данных (MVP)

1. Ingestion Web → парсинг URL → Item + Attachments
2. Library → CRUD Item/Tags → хранение в Postgres
3. Publishing VK → постановка Job → JobRunner → заглушка

## Хранилище

PostgreSQL + Flyway миграции. R2DBC используется для реактивного доступа.

