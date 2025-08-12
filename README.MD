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
