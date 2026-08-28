# DEV Runbook

## Запуск
```
mvn test
docker compose up -d --build
```

## Проверка
```
docker compose ps
docker compose logs --tail=100 backend
```

## Остановка и очистка
```
docker compose down -v
```

## Тесты
```
mvn test
```

## Порты
- backend: 8280
- ui: 3333
- postgres: 5432

## Конфиг через env
- Все настройки берутся из переменных окружения (см. `.env.example`).
- Пример: `AKCP_DB_HOST`, `AKCP_DB_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `AKCP_JWT_SECRET`.
- Для docker compose можно создать локальный `.env` (он в `.gitignore`).

## Типовые проблемы
- Порт занят: проверь, что 8280/3333/5432 свободны или измени порт-маппинг в `docker-compose.yml`.
- Docker daemon: Docker должен быть запущен и доступен из WSL (`docker info` без ошибок).
- WSL vs PowerShell: команды из этого runbook выполнять в WSL (bash), не в PowerShell.
- Миграции Flyway: если упали — проверь `backend` логи и соответствие версий БД/миграций; можно пересоздать БД `docker compose down -v` (удалит данные).

## WSL/Docker (кратко)
- Убедись, что `docker` доступен в WSL: `docker info` без ошибок.
- Если контейнеры не стартуют — перезапусти Docker Desktop и WSL (`wsl --shutdown`).
- Медленный доступ к диску: держи проект в `/mnt/c` только при необходимости; иначе лучше в `~/projects`.

## UI dev
Локальный dev‑сервер (Vite):
```
cd ui
npm install
npm run dev
```
Порт по умолчанию: 5173.

API URL:
- через `.env` в `ui/`: `VITE_API_BASE_URL=http://localhost:8280`
- через Docker: прокидывается из `docker-compose.yml` (будет использован на этапе сборки).
- Если браузер блокирует запросы (CORS), задай `AKCP_CORS_ALLOWED_ORIGINS=http://localhost:3333`.
