# AKCP Context

## Назначение
AK Content Pipeline (AKCP) — личный пайплайн контента: ingestion из источников, единая библиотека, публикация в цели (VK сейчас, Telegram позже).

## Модули
- akcp-core
- akcp-library
- akcp-ingestion-web
- akcp-jobs
- akcp-publishing-vk
- akcp-auth
- akcp-app
- ui

## Архитектура
- Spring Boot + WebFlux
- R2DBC для PostgreSQL
- JWT‑аутентификация, роли

## Порты
- backend: 8080 (наружу 8280)
- ui: 3333
- db: 5432

## Основные env‑переменные
- POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD
- AKCP_DB_HOST, AKCP_DB_PORT
- AKCP_JWT_SECRET
- AKCP_ADMIN_USERNAME, AKCP_ADMIN_PASSWORD
- AKCP_AI_MODE (local|remote), AKCP_AI_REMOTE_URL
- VK_ACCESS_TOKEN

## AI режимы
- local: заглушка локального вычисления
- remote: внешний воркер по URL

## JWT и VK
- JWT используется для доступа к /api/**.
- VK интеграция — заглушка, реальная публикация по токену при наличии VK_ACCESS_TOKEN.
