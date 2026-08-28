# UI Design (MVP)

## Навигация
- Базовый вариант: Stepper (1 страница, 3 шага)
- Альтернатива: Sidebar (Ingest / Library / Publish / Settings)

## Страницы и потоки
1) Login
   - ввод username/password
   - POST /api/auth/login
   - сохраняем JWT
2) Ingest URL (Step 1)
   - источник + стратегия (пример: fishki page range)
   - url + createItem
   - POST /api/ingestion/web/parse (или /api/ingestion/fishki/parse)
   - получаем attachments + createdItemId (batch item)
3) Library / Gallery (Step 2)
   - GET /api/items?cursor&limit
   - GET /api/items/{id}/attachments?cursor&limit (постранично ~40-50)
   - выбор кандидатов (UI‑only для ручного режима)
   - теги (PATCH /api/items/{id}/tags)
4) Publish VK (Step 3)
   - подтверждение выбранных attachments
   - POST /api/publish/vk/{itemId} + {attachmentIds}
   - группировка по 10 вложений на пост (VK)

## Состояния
- auth: token, username
- ingest: source, strategy, url, createItem, attachments[], createdItemId
- library: items[], cursor, selectedItemId, attachments[], attachmentCursor, selectedAttachments[]
- publish: lastJobId, status, batches[]

## Основные компоненты
- AppShell / Stepper
- LoginForm
- IngestForm + AttachmentGrid
- ItemGallery + Pagination
- SelectedPreview
- PublishPanel
- SettingsPanel (подсказки по env)

## Будущее (после MVP)
- Панель статуса/логов для UI (без доступа к контейнерным логам).
