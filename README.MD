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


старт через docker compose up -d
Дополнительно о ML-предпросмотре см. ackend/docs/ml-preview.md.
\n## ML dev stack\n\nВ этом репозитории есть готовый ML-стек, который стартует на других портах, чтобы не мешать прежнему окружению.\n\n1. Собрать образы и поднять стек:\n   `ash\n   docker compose build\n   docker compose up\n   `\n2. Компоненты:\n   * frontend: http://localhost:3334\n   * backend (соответствует ml.publish.base-url): http://localhost:8112 (говорит с ml-service по http://ml-service:8000)\n   * ml-service: http://localhost:38000 (FastAPI-стаб по контракту из ackend/docs/ml-preview.md)\n3. Включён ml.publish.api-key через переменную ML_PUBLISH_API_KEY в compose, поэтому backend может ходить к ML по HTTPS-заголовку.\n\nПосле тестов: cd backend && ./mvnw clean test, cd ml-service && python -m pytest.
### Настройка frontend

При локальной разработке (без Docker) укажите `REACT_APP_BACKEND_URL=http://localhost:8112` в `ui/.env` или в других `.env.*`-файлах.
В Docker Compose фронт собирается с `REACT_APP_BACKEND_URL=http://swp-backend:8111`, так что внутри сети он обращается к контейнеру `backend`, а адреса хоста влияют только на внешние порты.
