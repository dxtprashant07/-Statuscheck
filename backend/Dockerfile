# syntax=docker/dockerfile:1

# ---- build stage: compile + package the Spring Boot jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -e dependency:go-offline
COPY src ./src
RUN mvn -q -e clean package -DskipTests

# ---- runtime stage ----
FROM eclipse-temurin:21-jre
# tess4j loads the system Tesseract/Leptonica native libs on Linux; curl is for the healthcheck.
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
# Deterministic tessdata location (matches ocr.tessdata-path in application-prod.yml).
COPY tessdata /app/tessdata
ENV SPRING_PROFILES_ACTIVE=prod \
    TESSDATA_PATH=/app/tessdata \
    JAVA_OPTS=""
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
