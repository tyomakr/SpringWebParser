# Roadmap

## Статус
- [x] Базовая стабилизация проекта (документация и runbook)
- [x] Security / JWT / роли
- [x] Ingestion pipeline
- [x] Publishing (VK)
- [ ] UI (manual pipeline)
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
- Ingestion: добавлены задержки и ротация User-Agent по хостам (по умолчанию fishki.net).
- Ingestion: добавлена отдельная стратегия Fishki с парсингом диапазона страниц.
- spring-web присутствует только транзитивно (через webflux/security), прямых зависимостей нет.
- Publishing: проверяем статус QUEUED/тип PUBLISH_VK и 404 на неизвестный item.
- UI: шаг 1 подключён к /api/ingestion/web/parse и /api/ingestion/web/fishki/parse.

## UI (manual pipeline) подзадачи
- [x] Auth (login, хранение JWT)
- [x] Stepper (3 шага)
- [ ] Gallery + pagination (cursor)
- [ ] Preview selection (выбор кандидатов)
- [ ] Publish (VK)
- [ ] Settings (подсказки по env)
- [ ] UI status/logs panel (future)
- [ ] Fishki strategy (page range, demo source)
