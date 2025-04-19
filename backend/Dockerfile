# ---------- Stage 1: build ----------
FROM maven:3.9.7-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn -B clean package -DskipTests

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/spring-web-parser-backend.jar app.jar
EXPOSE 8111
ENTRYPOINT ["java","-jar","app.jar"]
