# Roadmap

## Статус
- [x] Базовая стабилизация проекта (документация и runbook)
- [x] Security / JWT / роли
- [x] Ingestion pipeline
- [ ] Publishing (VK)
- [ ] AI / ML интеграция
- [ ] Observability (logs/metrics)
- [ ] CI (mvn test)

## Критерии готовности (для текущего этапа)
- docs/CONTEXT.md заполнен
- docs/DEV.md актуален
- docs/DECISIONS.md содержит ключевые ADR

## Заметки
- Введены роли для /api/publish/vk и /api/ingestion/web, добавлены проверки в интеграционных тестах.
- Ingestion: добавлена валидация URL, таймаут запроса и unit‑тесты сервиса.
- spring-web присутствует только транзитивно (через webflux/security), прямых зависимостей нет.
