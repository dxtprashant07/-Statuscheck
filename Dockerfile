# syntax=docker/dockerfile:1
#
# Single-image build for PaaS hosts (Railway / Render / Fly): builds the React
# SPA, embeds it in the Spring Boot jar's static resources, and serves both the
# API and the UI from ONE service. The per-component backend/Dockerfile and
# frontend/Dockerfile + docker-compose.yml remain the path for multi-container
# (VM) deployments.

# ---- stage 1: build the React bundle ----
FROM node:20-alpine AS frontend
WORKDIR /fe
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build      # -> /fe/dist

# ---- stage 2: build the backend jar, embedding the SPA ----
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
COPY backend/pom.xml .
RUN mvn -q -e dependency:go-offline
COPY backend/src ./src
# Bundle the built SPA into Spring's static resources (served by SpaWebConfig).
COPY --from=frontend /fe/dist ./src/main/resources/static
RUN mvn -q -e clean package -DskipTests

# ---- stage 3: runtime ----
FROM eclipse-temurin:21-jre
# tess4j needs the native Tesseract libs; curl is for health checks.
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r app && useradd -r -g app -u 1001 app
WORKDIR /app
COPY --from=backend /build/target/*.jar app.jar
COPY backend/tessdata /app/tessdata
RUN mkdir -p /data/uploaded-documents && chown -R app:app /app /data
ENV SPRING_PROFILES_ACTIVE=prod \
    TESSDATA_PATH=/app/tessdata \
    APP_STORAGE_DIR=/data/uploaded-documents \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
USER app
# Informational; the app actually binds $PORT (Railway/Render inject it) or 8080.
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
