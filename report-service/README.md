# Report Service

The Report Service is the read-only query layer of the CI/CD pipeline system. It exposes REST endpoints for retrieving historical pipeline execution data at four levels of granularity: pipeline, run, stage, and job. The service reads directly from the shared PostgreSQL database populated by the Execution Service.

## Overview

As part of the microservices architecture, the Report Service decouples execution history queries from the execution logic. It uses Spring WebFlux and R2DBC for fully reactive, non-blocking database access, making it efficient for concurrent CLI report queries.

### Purpose

- **Execution History**: Query past pipeline runs with status, timestamps, and Git metadata
- **Live Status**: Expose real-time execution state (`PENDING`, `RUNNING`) for the `cicd status` subcommand
- **Hierarchical Reports**: Drill down from pipeline → run → stage → job
- **Reactive I/O**: Non-blocking database access via R2DBC for high concurrency
- **CLI Support**: Provides the data backing the `cicd report` and `cicd status` subcommands

## Architecture

### Component Structure

```
src/main/java/edu/northeastern/cs7580/cicd/reportservice/
├── ReportServiceApplication.java       # Spring Boot entry point
├── controller/
│   ├── ReportController.java           # Report REST endpoints
│   └── StatusController.java           # Live status endpoints
├── service/
│   └── ReportService.java              # Report assembly logic
├── repository/
│   ├── PipelineRepository.java         # pipelines table queries
│   ├── PipelineRunRepository.java      # pipeline_runs table queries
│   ├── StageRunRepository.java         # stage_runs table queries
│   └── JobRunRepository.java           # job_runs table queries
├── entity/
│   ├── Pipeline.java                   # pipelines table mapping
│   ├── PipelineRun.java                # pipeline_runs table mapping
│   ├── StageRun.java                   # stage_runs table mapping
│   └── JobRun.java                     # job_runs table mapping
├── dto/
│   ├── PipelineReportResponse.java     # Pipeline-level report response
│   ├── RunDetailResponse.java          # Run-level report response
│   ├── StageDetailResponse.java        # Stage-level report response
│   ├── JobDetailResponse.java          # Job-level report response
│   ├── RunSummary.java                 # Run summary in pipeline report
│   ├── StageSummary.java               # Stage summary in run report
│   └── JobSummary.java                 # Job summary in stage report
└── exception/
    ├── ResourceNotFoundException.java  # 404 error type
    └── GlobalExceptionHandler.java     # Centralized error handling
```

### Database Schema

The service queries four tables shared with the Execution Service:

```
pipelines
├── id (PK)
├── pipeline_name
└── git_repo

pipeline_runs
├── id (PK)
├── pipeline_id (FK → pipelines)
├── run_no          ← auto-incremented per pipeline by DB trigger
├── status
├── git_branch
├── git_hash
├── start_time
└── end_time

stage_runs
├── id (PK)
├── pipeline_run_id (FK → pipeline_runs)
├── stage_name
├── status
├── start_time
└── end_time

job_runs
├── id (PK)
├── stage_run_id (FK → stage_runs)
├── job_name
├── status
├── allow_failures
├── start_time
└── end_time
```

## API Endpoints

### Get Pipeline Report

**Endpoint**: `GET /api/v1/report/pipelines/{pipeline}`

Returns all past runs for the given pipeline.

**Response** (HTTP 200):
```json
{
  "pipeline": {
    "name": "default",
    "runs": [
      {
        "run-no": 1,
        "status": "success",
        "git-repo": "https://github.com/user/repo.git",
        "git-branch": "main",
        "git-hash": "abc123def456",
        "start": "2025-01-01T10:00:00Z",
        "end": "2025-01-01T10:05:00Z"
      }
    ]
  }
}
```

---

### Get Run Report

**Endpoint**: `GET /api/v1/report/pipelines/{pipeline}/runs/{runNo}`

Returns details of a specific pipeline run, including all stages.

**Response** (HTTP 200):
```json
{
  "pipeline": {
    "name": "default",
    "run-no": 1,
    "status": "success",
    "start": "2025-01-01T10:00:00Z",
    "end": "2025-01-01T10:05:00Z",
    "stages": [
      { "name": "build", "status": "success", "start": "...", "end": "..." },
      { "name": "test",  "status": "success", "start": "...", "end": "..." }
    ]
  }
}
```

---

### Get Stage Report

**Endpoint**: `GET /api/v1/report/pipelines/{pipeline}/runs/{runNo}/stages/{stage}`

Returns details of a specific stage within a run, including all jobs.

**Response** (HTTP 200):
```json
{
  "pipeline": {
    "name": "default",
    "run-no": 1,
    "status": "success",
    "start": "...",
    "end": "...",
    "stage": {
      "name": "build",
      "status": "success",
      "start": "...",
      "end": "...",
      "jobs": [
        { "name": "compile", "status": "success", "start": "...", "end": "...", "failures": false }
      ]
    }
  }
}
```

---

### Get Job Report

**Endpoint**: `GET /api/v1/report/pipelines/{pipeline}/runs/{runNo}/stages/{stage}/jobs/{job}`

Returns details of a specific job within a stage.

**Response** (HTTP 200):
```json
{
  "pipeline": {
    "name": "default",
    "run-no": 1,
    "status": "success",
    "start": "...",
    "end": "...",
    "stage": {
      "name": "build",
      "status": "success",
      "start": "...",
      "end": "...",
      "job": {
        "name": "compile",
        "status": "success",
        "start": "...",
        "end": "...",
        "failures": false
      }
    }
  }
}
```

---

### Get Execution Status (by repo)

**Endpoint**: `GET /api/v1/status?repo={repoUrl}`

Returns the active or most recent run for the given repository URL. Used by `cicd status --repo`.

---

### Get Execution Status (by run)

**Endpoint**: `GET /api/v1/status/{pipeline}/runs/{runNo}`

Returns the current status of a specific run, including per-stage and per-job state. Returns live `RUNNING`/`PENDING` states while the pipeline is executing. Used by `cicd status --pipeline --run`.

---

### Health Check

**Endpoint**: `GET /api/v1/report/health`

**Response** (HTTP 200):
```
Report Service is healthy
```

**Not Found** (HTTP 404 — any endpoint):
```json
{
  "error": "Not found: Pipeline 'unknown' not found"
}
```

## Configuration

### Application Properties

```yaml
spring:
  application:
    name: cicd-report-service
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/cicd
    username: postgres
    password: cicd

server:
  port: 8082
```

### Environment Variables

Override configuration via environment:

```bash
# Service port
SERVER_PORT=8082

# Database connection
SPRING_R2DBC_URL=r2dbc:postgresql://localhost:5432/cicd
SPRING_R2DBC_USERNAME=postgres
SPRING_R2DBC_PASSWORD=cicd
```

## Error Handling

### Not Found (HTTP 404)

When a requested pipeline, run, stage, or job does not exist, the service returns HTTP 404 with a descriptive message:

```json
{ "error": "Not found: Run 99 not found for pipeline 'default'" }
{ "error": "Not found: Stage 'deploy' not found in run 1 of pipeline 'default'" }
{ "error": "Not found: Job 'compile' not found in stage 'build' of run 1 of pipeline 'default'" }
```

### Internal Errors (HTTP 500)

Unexpected errors (e.g. database connectivity) are caught by `GlobalExceptionHandler` and returned as HTTP 500.

## Running the Service

### Prerequisites

- **Java**: 21 or higher
- **PostgreSQL**: Running on port 5432 with database `cicd`
- The database must already be seeded by the Execution Service (shared schema)

### Local Development

```bash
# Build the service
./gradlew :report-service:build

# Run locally (default port 8082)
./gradlew :report-service:bootRun

# Run with custom port
./gradlew :report-service:bootRun --args='--server.port=9090'

# Run with custom database URL
./gradlew :report-service:bootRun \
  --args='--spring.r2dbc.url=r2dbc:postgresql://myhost:5432/cicd'
```

The service starts on http://localhost:8082 by default.

## Testing

### Unit Tests

```bash
# Run all tests
./gradlew :report-service:test

# Generate coverage report
./gradlew :report-service:jacocoTestReport

# View coverage report
open report-service/build/reports/jacoco/test/html/index.html
```

### Manual Testing

```bash
# Health check
curl http://localhost:8082/api/v1/report/health

# Get all runs for a pipeline
curl http://localhost:8082/api/v1/report/pipelines/default

# Get a specific run
curl http://localhost:8082/api/v1/report/pipelines/default/runs/1

# Get a specific stage
curl http://localhost:8082/api/v1/report/pipelines/default/runs/1/stages/build

# Get a specific job
curl http://localhost:8082/api/v1/report/pipelines/default/runs/1/stages/build/jobs/compile
```

## Technology Stack

### Core Dependencies

- **Spring Boot** 4.x: Application framework
- **Spring WebFlux**: Reactive web framework for non-blocking I/O
- **Spring Data R2DBC**: Reactive database access
- **PostgreSQL R2DBC driver**: Reactive PostgreSQL connectivity
- **Lombok**: Reduce boilerplate code
- **SLF4J + Logback**: Logging infrastructure
- **OpenTelemetry Java Agent**: Distributed tracing and metrics instrumentation
- **OTel Logback Appender**: Forwards structured logs via OTLP to the OTel Collector

### Testing

- **JUnit 5**: Testing framework
- **Spring Test / WebFlux Test**: Controller and service testing
- **Mockito**: Mocking framework for unit tests
- **Reactor Test (StepVerifier)**: Testing reactive streams

## Observability

The Report Service is instrumented with the OpenTelemetry Java agent. Trace context propagated from the API Gateway is continued here, linking report queries back to the originating CLI request in Grafana Tempo.

Structured logs are forwarded to Grafana Loki via the OTel Logback appender (`OtelLogbackInstaller`). Standard Spring Boot / JVM metrics are exported via OTLP to the OTel Collector and stored in Prometheus.

Report responses include a `trace-id` field at the run level, sourced from the `trace_id` column written by the Execution Service. This allows a pipeline run to be located directly in Grafana Tempo.

**Log Levels**:
- `INFO`: Incoming report requests
- `DEBUG`: Repository queries and data assembly
- `ERROR`: Not-found errors, unexpected exceptions

**Example Log Output**:
```
INFO  ReportController - Received report request for pipeline: default
DEBUG ReportService - Fetching report for pipeline: default
INFO  ReportController - Received report request for pipeline: default, run: 1
```

## Troubleshooting

**Database Connection Refused**
- Verify PostgreSQL is running: `pg_isready -h localhost -p 5432`
- Check `spring.r2dbc.url`, `username`, and `password` in `application.yml`
- Ensure the `cicd` database exists and is accessible

**404 on Valid Pipeline**
- The Report Service reads data written by the Execution Service — ensure at least one pipeline has been executed successfully first
- Verify the pipeline name matches exactly (case-sensitive)

**Port Already in Use**
- Another service is using port 8082
- Change port: `--server.port=8083`
- Find conflicting process: `lsof -i :8082`

## Integration with Other Services

The Report Service shares the PostgreSQL database with the Execution Service — it does **not** communicate with it directly over HTTP. Data flows as follows:

```
CLI ──→ API Gateway ──→ Report Service ──→ PostgreSQL (read-only)
                                               ↑
                        Execution Service ─────┘ (writes execution records)
```

The CLI invokes the `cicd report` subcommand, which the API Gateway forwards to this service. The response JSON is formatted as YAML by the CLI before display.

---
