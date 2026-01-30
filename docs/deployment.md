# Развертывание (Docker Compose)

1) Создайте `.env`:

```bash
cp .env.example .env
```

2) Запуск:

```bash
docker compose up --build
```

Сервисы:

- `db` — PostgreSQL
- `backend` — Spring Boot (AKCP)

По умолчанию приложение слушает `http://localhost:8080`.

