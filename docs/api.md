# API (MVP)

## Авторизация

`POST /api/auth/login`

```json
{"username":"admin","password":"admin"}
```

Ответ:

```json
{"token":"...","tokenType":"Bearer"}
```

## Items

- `POST /api/items` — создать Item
- `GET /api/items` — список
- `GET /api/items/{id}` — получить один
- `PATCH /api/items/{id}/tags` — добавить/удалить теги

### Пагинация

`GET /api/items?cursor=&limit=&from=&to=&tag=`

- `cursor` — base64‑кодированная пара `(createdAt|id)`
- `limit` — размер страницы (по умолчанию 20, максимум 100)
- `from` / `to` — фильтр по времени (ISO‑8601)
- `tag` — фильтр по тегу

Ответ:

```json
{"items":[...],"nextCursor":"..."}
```

## Ingestion Web

`POST /api/ingestion/web/parse`

```json
{"url":"https://example.com","createItem":true}
```

Возвращает список найденных изображений и `createdItemId` при `createItem=true`.

## Publishing VK

`POST /api/publish/vk/{itemId}` — создаёт job со статусом QUEUED.

