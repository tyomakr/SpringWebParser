# Безопасность

## JWT

- `/api/auth/login` возвращает JWT (Bearer)
- Все `/api/**` требуют JWT, кроме `/api/auth/login`
- `/actuator/health` доступен без авторизации

## Роли

- `ADMIN`, `MODERATOR`, `AGENT`
- В MVP выдаётся только `ADMIN` (dev‑пользователь)

## Секреты

- Никогда не хранить токены в репозитории
- Все секреты через env‑переменные или внешние файлы
- Используйте `.env.example` как шаблон

Обязательные env‑переменные:

- `AKCP_JWT_SECRET`
- `AKCP_ADMIN_USERNAME`
- `AKCP_ADMIN_PASSWORD`
