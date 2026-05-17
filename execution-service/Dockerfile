# ── Build stage ───────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

COPY gradlew .
COPY gradle/ gradle/
COPY settings.gradle .
COPY build.gradle .
COPY execution-service/build.gradle execution-service/
COPY api-gateway/build.gradle api-gateway/
COPY pipeline-lib/build.gradle pipeline-lib/
COPY pipeline-lib/src pipeline-lib/src

ENV DOCKER_BUILD=execution-service

RUN ./gradlew :execution-service:dependencies --no-daemon -q 2>/dev/null || true

COPY execution-service/src execution-service/src
RUN ./gradlew :execution-service:bootJar --no-daemon -x test

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /workspace/execution-service/build/libs/execution-service.jar app.jar

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]