# API Gateway Service

The API Gateway is the unified entry point for all CLI requests in the CI/CD pipeline system. It serves as a routing layer that forwards requests to appropriate backend microservices while providing request validation, error handling, and consistent logging.

## Overview

As part of the microservices architecture (Phase 2), the API Gateway decouples the CLI from backend services, allowing independent scaling and deployment of execution and reporting capabilities. The gateway handles HTTP communication using Spring WebFlux for reactive, non-blocking I/O operations.

### Purpose

- **Unified API**: Single entry point for all CLI operations
- **Service Routing**: Intelligent forwarding to Execution and Report services
- **Request Validation**: Input validation before forwarding to backend services
- **Error Translation**: Consistent error formatting across all operations
- **Non-blocking I/O**: Efficient resource utilization for concurrent pipeline executions

## Architecture

### Component Structure

```
src/main/java/edu/northeastern/cs7580/cicd/apigateway/
├── ApiGatewayApplication.java          # Spring Boot entry point
├── controller/
│   ├── PipelineController.java         # Execution REST endpoints
│   ├── ReportController.java           # Report REST endpoints
│   └── StatusController.java           # Status proxy endpoints
├── service/
│   ├── ExecutionServiceClient.java     # Execution Service communication
│   └── ReportServiceClient.java        # Report Service communication
├── dto/
│   ├── PipelineExecutionRequest.java   # Execution request model
│   ├── PipelineExecutionResponse.java  # Execution response model
│   └── GitMetadata.java                # Git repository metadata
└── exception/
    └── GlobalExceptionHandler.java     # Centralized error handling
```

### Service Communication

The API Gateway communicates with backend services via HTTP:

- **Execution Service** (`POST /api/v1/execution/execute`) — handles pipeline execution requests, default port 8081
- **Report Service** (`GET /api/v1/report/pipelines/...`) — handles execution report queries, default port 8082

Communication is implemented using Spring WebClient for reactive, non-blocking operations with a 30-second timeout.

## API Endpoints

### Execute Pipeline

**Endpoint**: `POST /api/v1/pipelines/execute`

Initiates a pipeline execution by forwarding the request to the Execution Service.

**Request Body**:
```json
{
  "pipelineName": "default",
  "pipelineFilePath": ".pipelines/default.yaml",
  "gitMetadata": {
    "repositoryUrl": "git@github.com:user/repo.git",
    "branch": "main",
    "commitHash": "abc123def456..."
  }
}
```

**Response** (HTTP 202 — queued):
```json
{
  "executionId": "uuid-string",
  "pipelineName": "default",
  "runNumber": 42,
  "status": "PENDING",
  "message": "Pipeline queued for execution"
}
```

Use `GET /api/v1/pipelines/status/{pipeline}/runs/{runNo}` to track progress.

**Response** (HTTP 400 — validation error):
```json
{
  "status": "FAILED",
  "message": "Pipeline validation failed: Circular dependency detected"
}
```

---

### Get Execution Status (by repo)

**Endpoint**: `GET /api/v1/pipelines/status?repo={repoUrl}`

Proxies to Report Service. Returns active or most recent run for the repository.

---

### Get Execution Status (by run)

**Endpoint**: `GET /api/v1/pipelines/status/{pipeline}/runs/{runNo}`

Proxies to Report Service. Returns live per-stage and per-job status for a specific run.

---

### Get Pipeline Report

**Endpoint**: `GET /api/v1/pipelines/report/pipelines/{pipeline}`

Returns all past runs for the given pipeline.

---

### Get Run Report

**Endpoint**: `GET /api/v1/pipelines/report/pipelines/{pipeline}/runs/{runNo}`

Returns details of a specific run, including all stages.

---

### Get Stage Report

**Endpoint**: `GET /api/v1/pipelines/report/pipelines/{pipeline}/runs/{runNo}/stages/{stage}`

Returns details of a specific stage, including all jobs.

---

### Get Job Report

**Endpoint**: `GET /api/v1/pipelines/report/pipelines/{pipeline}/runs/{runNo}/stages/{stage}/jobs/{job}`

Returns details of a specific job, including the `failures` flag indicating whether the job was configured to allow failures.

---

### Health Check

**Endpoint**: `GET /api/v1/pipelines/health`

**Response** (HTTP 200):
```
API Gateway is healthy
```

## Configuration

### Application Properties

The API Gateway uses Spring Boot's configuration system with support for environment variables:

**Key Properties**:
- `server.port` — HTTP server port (default: 8080)
- `services.execution.url` — Execution Service base URL (default: `http://localhost:8081`)
- `services.report.url` — Report Service base URL (default: `http://localhost:8082`)

### Environment Variables

```bash
# API Gateway port
SERVER_PORT=8080

# Backend service URLs
EXECUTION_SERVICE_URL=http://execution-service:8081
REPORT_SERVICE_URL=http://report-service:8082
```

### Configuration Examples

**Local Development**:
```properties
server.port=8080
services.execution.url=http://localhost:8081
services.report.url=http://localhost:8082
```

## Data Models

### Request Models

**PipelineExecutionRequest**: Contains pipeline file path and Git context
- `pipelineName` (required): Pipeline identifier matching the `pipeline.name` field in the YAML
- `pipelineFilePath` (required): Relative path to the pipeline YAML file from the repo root (e.g. `.pipelines/default.yaml`)
- `gitMetadata` (required): Repository, branch, and commit information

**GitMetadata**: Git repository context
- `repositoryUrl` (required): Git repository URL
- `branch` (required): Git branch name
- `commitHash` (required): Full Git commit SHA-1 hash

### Response Models

**PipelineExecutionResponse**: Execution tracking information
- `executionId`: Unique identifier for tracking execution
- `pipelineName`: Name of executed pipeline
- `runNumber`: Sequential run number for this pipeline
- `status`: Execution status (`SUCCESS`, `FAILED`, `VALIDATION_FAILED`)
- `message`: Human-readable status message or error details

## Error Handling

### Backend Service Errors

When backend services return error responses, the gateway preserves the original HTTP status code and response body:

```json
{
  "status": "FAILED",
  "message": "Backend service error details"
}
```

### Internal Errors

Unexpected errors are caught and returned with HTTP 500:

```json
{
  "status": "FAILED",
  "message": "Failed to execute pipeline: <error details>"
}
```

### Validation Errors

Request validation errors are rejected before forwarding to backend services and returned with HTTP 400.

## Running the Service

### Prerequisites

- **Java**: 21 or higher
- **Execution Service**: Running on port 8081
- **Report Service**: Running on port 8082

### Local Development

```bash
# Build the service
./gradlew :api-gateway:build

# Run locally (default port 8080)
./gradlew :api-gateway:bootRun

# Run with custom port
./gradlew :api-gateway:bootRun --args='--server.port=9090'

# Run with custom backend URLs
./gradlew :api-gateway:bootRun \
  --args='--services.execution.url=http://localhost:8081 --services.report.url=http://localhost:8082'
```

The service starts on http://localhost:8080 by default.

## Testing

### Unit Tests

```bash
# Run all tests
./gradlew :api-gateway:test

# Generate coverage report
./gradlew :api-gateway:jacocoTestReport
```

### Manual Testing

```bash
# Health check
curl http://localhost:8080/api/v1/pipelines/health

# Execute pipeline
curl -X POST http://localhost:8080/api/v1/pipelines/execute \
  -H "Content-Type: application/json" \
  -d '{
    "pipelineName": "default",
    "pipelineFilePath": ".pipelines/default.yaml",
    "gitMetadata": {
      "repositoryUrl": "https://github.com/user/repo.git",
      "branch": "main",
      "commitHash": "abc123def456"
    }
  }'

# Get pipeline report
curl http://localhost:8080/api/v1/pipelines/report/pipelines/default

# Get run report
curl http://localhost:8080/api/v1/pipelines/report/pipelines/default/runs/1

# Get stage report
curl http://localhost:8080/api/v1/pipelines/report/pipelines/default/runs/1/stages/build

# Get job report
curl http://localhost:8080/api/v1/pipelines/report/pipelines/default/runs/1/stages/build/jobs/compile
```

## Technology Stack

### Core Dependencies

- **Spring Boot** 4.x: Application framework
- **Spring Cloud Gateway**: Reactive API gateway and request routing
- **Spring WebFlux**: Reactive web framework for non-blocking I/O
- **Spring Validation**: Request validation with Jakarta Bean Validation
- **Resilience4j**: Circuit breaker and retry support for backend service calls
- **Lombok**: Reduce boilerplate code
- **SLF4J + Logback**: Logging infrastructure
- **OpenTelemetry Java Agent**: Distributed tracing and metrics instrumentation
- **OTel Logback Appender**: Forwards structured logs via OTLP to the OTel Collector

### Testing

- **JUnit 5**: Testing framework
- **Spring Test**: Integration testing support
- **MockWebServer**: Backend service mocking
- **Reactor Test (StepVerifier)**: Testing reactive streams

## Observability

The API Gateway is instrumented with the OpenTelemetry Java agent. Traces originating from CLI requests are propagated downstream to the Execution Service and Report Service, enabling end-to-end trace correlation in Grafana Tempo.

Structured logs are forwarded to Grafana Loki via the OTel Logback appender (`OtelLogbackInstaller`). Standard Spring Boot / JVM metrics are exported via OTLP to the OTel Collector and stored in Prometheus.

**Log Levels**:
- `INFO`: Incoming requests, successful routing, execution tracking
- `DEBUG`: Request/response details
- `ERROR`: Backend service errors, unexpected exceptions

**Example Log Output**:
```
INFO  PipelineController - Received pipeline execution request for: default
INFO  PipelineController - Pipeline execution initiated: default (ID: abc-123-def)
INFO  ReportController - Routing report request for pipeline: default
ERROR PipelineController - Execution Service returned error: 400 BAD_REQUEST
```

## Troubleshooting

**Connection Refused to Backend Service**
- Verify Execution Service is running on port 8081
- Verify Report Service is running on port 8082
- Check `services.execution.url` and `services.report.url` in application properties

**Request Timeout**
- Default timeout is 30 seconds per backend call
- Check backend service logs for slow operations

**Validation Errors**
- Ensure all required fields are provided: `pipelineName`, `pipelineFilePath`, `gitMetadata`
- Verify `gitMetadata` contains `repositoryUrl`, `branch`, and `commitHash`

**Port Already in Use**
- Another service is using port 8080
- Use `--server.port=<port>` to specify an alternative port

---
