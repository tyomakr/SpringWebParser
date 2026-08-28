# AK Content Pipeline (AKCP)

AKCP — персональный контент‑пайплайн: сбор материалов из источников (сейчас — web), единая библиотека с тегами, затем публикация в целевые каналы (в MVP — VK как заглушка). Архитектура заранее оставляет место для AI‑рекомендаций и удалённого GPU‑ускорителя.

## Быстрый старт (локально)

1) Скопируйте шаблон и заполните обязательные секреты своими значениями:

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

- `http://localhost:8280/swagger-ui.html`

Compose публикует UI и backend только на loopback-хосте, а PostgreSQL нужен
только внутри compose-сети. Файл `.env.example` намеренно содержит пустые
значения для обязательных секретов.

## Основные эндпоинты MVP

- `POST /api/auth/login` — получение JWT
- `GET /api/items` — список элементов (cursor‑pagination)
- `POST /api/items` — создать элемент
- `GET /api/items/{id}` — получить элемент
- `PATCH /api/items/{id}/tags` — добавить/удалить теги
- `POST /api/ingestion/web/parse` — парсинг URL, опционально создание Item
- `POST /api/publish/vk/{itemId}` — постановка job на публикацию в VK
