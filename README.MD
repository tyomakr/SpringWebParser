Небольшой проект для изучения нескольких моментов:
1) Парсинг страниц
2) Обработка данных, полученных с бэкэнд в фронтэнде
3) Взаимодействие с VK API


Для взаимодействия с VK API необходимо:

0) В resources создать файл application-secret.yml такого типа:

   vk:

   &emsp; app-id: 

   &emsp; user-id: 

   &emsp; group-id: 

   &emsp; access-token: 


1) Создать standalone app в VK apps и получить его ID. Его нужно будет прописать в application-secret.yml в vk.app-id
2) Заполняем поля user-id (числовой идентификатор пользователя), group-id - идентификатор группы, в которую будет публикация (и есть соответствующие права). group-id указывается со знаком "-" перед числовым идентификатором

3) Получаем новый токен (access_token) выполнением запроса:
https://oauth.vk.com/authorize?client_id=<сюда app_id>&display=page&redirect_uri=https://oauth.vk.com/blank.html&scope=friends,notify,photos,wall,email,mail,groups,stats,offline&response_type=token&v=5.74
Получаем ответ в виде:
https://oauth.vk.com/blank.html#access_token=<отсюда копируем access_token>&expires_in=0&user_id=<тут user_id>&email=<почта пользователя>


## ML dev stack

В репозитории есть минимальный ML-stub сервис (`ml-service/`), который реализует контракт из
`backend/docs/ml-preview.md` и используется бэкендом для ML-превью.

### Переменные окружения

#### Backend
```
VK_API_TOKEN=<секрет>
ML_PUBLISH_API_KEY=<по необходимости>
```
`vk.api.token` читается из `application-secret.yml`, остальные публичные `vk.api.*` прописаны в `application.yml`.

#### ML-service
```
TRAINING_EXPORT_URL=http://backend:8111/api/vk-history/training/export
ML_PUBLISH_API_KEY=<по необходимости>
```

Локально можно указать `http://localhost:8111/...` для `TRAINING_EXPORT_URL`.

### Тесты

- Backend: `./mvnw clean test`
- ML-service: `python -m pip install -r requirements.txt && pytest -q`
- UI: `cd ui && npm i && npm run build`

### Порты dev-стека (docker)

- backend: `8111`
- frontend: `3333`
- ml-service: `38000`

### Запуск через Docker

```bash
docker compose build
docker compose up -d
```
