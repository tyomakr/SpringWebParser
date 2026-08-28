# AK Content Pipeline (AKCP)

AKCP — персональный контент‑пайплайн: сбор материалов из источников (сейчас — web), единая библиотека с тегами, затем публикация в целевые каналы (в MVP — VK как заглушка). Архитектура заранее оставляет место для AI‑рекомендаций и удалённого GPU‑ускорителя.

## Быстрый старт (локально)

1) Подготовьте переменные окружения:

```bash
cp .env.example .env
```

2) Запуск через Docker Compose:

```bash
docker compose up --build
```

3) Локальный запуск без Docker:

```bash
mvn -pl backend/akcp-app spring-boot:run
```

## Документация

- `docs/architecture.md`
- `docs/modules.md`
- `docs/security.md`
- `docs/deployment.md`
- `docs/api.md`
- `docs/ai-design.md`

## Полезные команды

```bash
mvn test
```

Swagger UI доступен по:

- `http://localhost:8080/swagger-ui.html`

## Основные эндпоинты MVP

- `POST /api/auth/login` — получение JWT
- `GET /api/items` — список элементов (cursor‑pagination)
- `POST /api/items` — создать элемент
- `GET /api/items/{id}` — получить элемент
- `PATCH /api/items/{id}/tags` — добавить/удалить теги
- `POST /api/ingestion/web/parse` — парсинг URL, опционально создание Item
- `POST /api/publish/vk/{itemId}` — постановка job на публикацию в VK
