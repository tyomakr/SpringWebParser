# Развертывание (Docker Compose)

1) Создайте `.env` и заполните обязательные секреты:

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

Порты хоста доступны только через loopback:

- UI: `http://localhost:3333`
- backend/Swagger: `http://localhost:8280`
- PostgreSQL: `localhost:5432` только для локальной диагностики.

Compose завершает обработку конфигурации, если не заданы `POSTGRES_USER`,
`POSTGRES_PASSWORD`, `AKCP_JWT_SECRET`, `AKCP_ADMIN_USERNAME` или
`AKCP_ADMIN_PASSWORD`.
