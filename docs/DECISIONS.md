# Decisions (ADR)

## ADR-0001: Spring Boot 4.0.0
- Date: 2026-01-31
- Status: Accepted
- Context: требуется актуальная платформа с поддержкой Java 21 и Spring Framework 7.
- Decision: используем Spring Boot 4.0.0 как parent BOM.
- Consequences: обновлены версии Spring и экосистемы, проверены сборка и тесты.

## ADR-0002: PostgreSQL 16
- Date: 2026-01-31
- Status: Accepted
- Context: целевая БД для production и локального Docker.
- Decision: используем postgres:16 в docker-compose и тестах.
- Consequences: совместимость миграций и драйверов подтверждена.

## ADR-0003: WebFlux + Netty
- Date: 2026-01-31
- Status: Accepted
- Context: требуются реактивные endpoints и интеграции.
- Decision: используем Spring WebFlux (Netty) для backend.
- Consequences: реактивный стек, без servlet‑контейнера.

## ADR-0004: Явный WebClient.Builder
- Date: 2026-01-31
- Status: Accepted
- Context: после апгрейда отсутствовал bean WebClient.Builder.
- Decision: объявляем WebClient.Builder вручную в конфигурации.
- Consequences: зависимые сервисы получают builder через DI.
