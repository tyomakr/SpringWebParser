# Модули

| Модуль | Ответственность | Не должен владеть |
| --- | --- | --- |
| `akcp-core` | Доменные модели, порты и общие контракты | Spring, HTTP, Postgres, VK |
| `akcp-library` | Каталог контента, теги, пагинация, media/recommendation seams | Auth wiring и transport adapters |
| `akcp-ingestion-web` | Web-ingest, bounded parsing и обработка URL | Публикация и прямой доступ к UI |
| `akcp-publishing-vk` | VK boundary, publish job и publisher adapters | Credentials в Git и обход job lifecycle |
| `akcp-jobs` | Job storage, claim/lease и выполнение фоновых задач | Доменную политику каталогов |
| `akcp-auth` | JWT-аутентификация и роли | Секреты и бизнес-операции других модулей |
| `akcp-app` | Spring Boot composition root, configuration и Flyway migrations | Доменную реализацию |

Зависимости направлены к контрактам: домен находится в `akcp-core`, реализации
живут в профильных модулях, а их wiring выполняется в `akcp-app`. Новую функцию
следует добавлять в один профильный модуль; изменение нескольких модулей должно
быть обосновано общим контрактом, миграцией или wiring.

Исторические границы и checkpoints описаны в
[`docs/architecture.md`](architecture.md). Legacy-MVP и текущий AKCP baseline
не смешиваются с незакоммиченным пользовательским snapshot.
