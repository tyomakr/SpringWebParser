## ML preview service

Backend вызывает внутренний ML-сервис через HTTP, чтобы оценить кандидатов для публикации. Контракт описан ниже, а в конфигурации можно указать адрес внутри Docker-сети или `localhost`.

### Контракт

**URL:** `POST ${ml.publish.base-url}${ml.publish.recommendation-path}`  
`ml.publish.recommendation-path` по умолчанию `/recommend`.

**Request:**
```json
{
  "images": [
    { "id": "string", "url": "string" }
  ]
}
```

**Response:**
```json
{
  "recommendations": [
    {
      "id": "string",
      "url": "string",
      "score": 0.0,
      "reason": "string",
      "decision": "PUBLISH|REVIEW|SKIP"
    }
  ]
}
```

`decision` переводится в `RecommendationDecision` и возвращается пользователю как строка в `MlPublishPreviewResponse.recommendations[].recommendation`.

### Конфигурация

Сервис включается через свойства `ml.publish.*` (можно прописать их в `backend/src/main/resources/application-secret.yml`, `application-local.yml` или другом профиле):

```yaml
ml:
  publish:
    base-url: http://ml-service:8000          # адрес внутри Docker-сети
    recommendation-path: /recommend           # совпадает с MlRecommendationProperties.recommendationPath
    api-key: ${ML_PUBLISH_API_KEY:}           # если нужен заголовок Authorization
    timeout-seconds: 6                        # таймаут запроса
```

Согласно конфигурации создаётся бин `HttpMlRecommendationClient`, который отправляет JSON и парсит ответ. Если сервис недоступен или возвращает ошибку, `MlPublishController` отлавливает `MlRecommendationException` и отдаёт `200 OK` с пустым списком.
