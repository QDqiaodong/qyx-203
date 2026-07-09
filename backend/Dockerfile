FROM ${DOCKER_REGISTRY}maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

COPY settings.xml /root/.m2/settings.xml
COPY pom.xml .
RUN mvn dependency:go-offline -Dmaven.repo.local=/root/.m2/repository

COPY src ./src
RUN mvn clean package -DskipTests -Dmaven.repo.local=/root/.m2/repository

FROM ${DOCKER_REGISTRY}amazoncorretto:17-alpine

WORKDIR /app

COPY --from=builder /app/target/maternity-room-management-1.0.0.jar app.jar

EXPOSE 8133

ENTRYPOINT ["java", "-jar", "app.jar"]