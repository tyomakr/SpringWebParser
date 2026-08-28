FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
COPY backend/pom.xml ./backend/pom.xml
COPY backend/akcp-core/pom.xml ./backend/akcp-core/pom.xml
COPY backend/akcp-library/pom.xml ./backend/akcp-library/pom.xml
COPY backend/akcp-ingestion-web/pom.xml ./backend/akcp-ingestion-web/pom.xml
COPY backend/akcp-publishing-vk/pom.xml ./backend/akcp-publishing-vk/pom.xml
COPY backend/akcp-auth/pom.xml ./backend/akcp-auth/pom.xml
COPY backend/akcp-jobs/pom.xml ./backend/akcp-jobs/pom.xml
COPY backend/akcp-app/pom.xml ./backend/akcp-app/pom.xml
RUN mvn -pl backend/akcp-app -am -DskipTests dependency:go-offline
COPY backend ./backend
RUN mvn -pl backend/akcp-app -am -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/backend/akcp-app/target/akcp-app-*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
