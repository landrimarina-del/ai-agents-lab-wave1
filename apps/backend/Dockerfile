FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY apps/backend/pom.xml /workspace/apps/backend/pom.xml
COPY apps/backend/src /workspace/apps/backend/src

WORKDIR /workspace/apps/backend
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /workspace

COPY --from=build /workspace/apps/backend/target/backend-0.0.1-SNAPSHOT.jar /workspace/app.jar
COPY infra/db/migrations /workspace/infra/db/migrations

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/workspace/app.jar"]