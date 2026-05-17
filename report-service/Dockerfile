# ── Build stage ───────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

COPY gradlew .
COPY gradle/ gradle/
COPY settings.gradle .
COPY build.gradle .
COPY report-service/build.gradle report-service/
COPY pipeline-lib/build.gradle pipeline-lib/
COPY pipeline-lib/src pipeline-lib/src

ENV DOCKER_BUILD=report-service

RUN ./gradlew :report-service:dependencies --no-daemon -q 2>/dev/null || true

COPY report-service/src report-service/src
RUN ./gradlew :report-service:bootJar --no-daemon -x test

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /workspace/report-service/build/libs/report-service.jar app.jar

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]
