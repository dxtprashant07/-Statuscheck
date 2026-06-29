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
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r app && useradd -r -g app -u 1001 app
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
# Deterministic tessdata location (matches ocr.tessdata-path in application-prod.yml).
COPY tessdata /app/tessdata
# Upload volume mount point, owned by the non-root runtime user so writes succeed.
RUN mkdir -p /data/uploaded-documents && chown -R app:app /app /data
ENV SPRING_PROFILES_ACTIVE=prod \
    TESSDATA_PATH=/app/tessdata \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health | grep -q UP || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
