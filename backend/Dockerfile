# ---------- Stage 1: build ----------
FROM maven:3.9.7-eclipse-temurin-21 AS build
WORKDIR /app

# Копируем только файлы зависимостей сначала для кеширования слоёв
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Копируем исходники и собираем
COPY src ./src
RUN mvn -B clean package -DskipTests && \
    rm -rf /root/.m2

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Устанавливаем только необходимые пакеты и создаём пользователя для безопасности
RUN apk add --no-cache wget && \
    addgroup -S appgroup && \
    adduser -S appuser -G appgroup && \
    mkdir -p /app/downloaded && \
    chown -R appuser:appgroup /app

# Копируем jar из build stage
COPY --from=build --chown=appuser:appgroup /app/target/spring-web-parser-backend.jar app.jar

# Переключаемся на непривилегированного пользователя
USER appuser

EXPOSE 8111
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8111/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
