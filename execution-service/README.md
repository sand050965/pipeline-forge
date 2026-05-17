# Execution Service

The Execution Service is the core orchestration component of the CI/CD pipeline system. It accepts execution requests from the API Gateway, validates the pipeline configuration, initializes database records, and publishes the execution to a RabbitMQ queue — returning `202 Accepted` immediately. An async consumer then clones the Git workspace, re-parses the pipeline YAML, and executes jobs sequentially inside Docker containers with fail-fast behavior.

## Overview

As part of the microservices architecture (Phase 2), the Execution Service decouples pipeline execution logic from the API layer, allowing independent scaling and deployment. The service manages the complete lifecycle of pipeline execution, from workspace preparation through job execution to result reporting and database persistence.

### Purpose

- **Async Execution**: Non-blocking pipeline submission via RabbitMQ; returns 202 immediately
- **Pipeline Orchestration**: Sequential execution of jobs in topological order
- **Workspace Management**: Git repository cloning and checkout for each execution
- **Container Execution**: Isolated job execution in Docker containers
- **Fail-Fast Behavior**: Immediate pipeline termination on job failure (overridden per job with `failures: true`)
- **Result Tracking**: Comprehensive execution results with job-level details
- **Persistence**: Full execution history stored in PostgreSQL at pipeline, stage, and job levels

## Architecture

### Component Structure

```
src/main/java/edu/northeastern/cs7580/cicd/executionservice/
├── ExecutionServiceApplication.java    # Spring Boot entry point (@EnableAsync)
├── controller/
│   └── ExecutionController.java        # REST API endpoints
├── service/
│   ├── ExecutionService.java           # Pipeline orchestration
│   ├── ExecutionPersistenceService.java # Database writes (fail-open)
│   └── WorkspaceService.java           # Git workspace preparation
├── executor/
│   └── DockerExecutor.java             # Docker container execution
├── model/
│   ├── ExecutionResult.java            # Pipeline execution result
│   ├── ExecutionStatus.java            # Shared DB/API status enumeration
│   ├── JobResult.java                  # Job execution result
│   └── JobStatus.java                  # Job status enumeration
├── dto/
│   ├── PipelineExecutionRequest.java   # Execution request model
│   ├── PipelineExecutionResponse.java  # Execution response model
│   └── GitMetadata.java                # Git repository metadata
├── entity/
│   ├── PipelineEntity.java             # pipelines table
│   ├── PipelineRunEntity.java          # pipeline_runs table
│   ├── StageRunEntity.java             # stage_runs table
│   └── JobRunEntity.java               # job_runs table
├── repository/
│   ├── PipelineRepository.java
│   ├── PipelineRunRepository.java
│   ├── StageRunRepository.java
│   └── JobRunRepository.java
├── messaging/
│   ├── PipelineExecutionMessage.java   # RabbitMQ message payload
│   ├── PipelineExecutionConsumer.java  # Async RabbitMQ consumer
│   └── RabbitMQConfig.java             # Exchange, queue, binding definitions
├── exception/
│   ├── DockerException.java            # Docker operation errors
│   └── WorkspaceException.java         # Workspace preparation errors
└── config/
    └── DockerClientConfiguration.java  # Docker client Spring bean
```

### Execution Flow

The service uses an async publish-subscribe pattern via RabbitMQ:

**Submission (HTTP handler — fast path):**
1. Receive execution request from API Gateway
2. Validate pipeline YAML — return HTTP 400 if invalid
3. Upsert pipeline definition, create pipeline run as `PENDING`, pre-create all stage/job rows as `PENDING`
4. Publish `PipelineExecutionMessage` to RabbitMQ exchange
5. Return `202 Accepted` immediately with assigned run number

**Execution (async RabbitMQ consumer):**
1. Consumer picks message from durable queue
2. Clone Git repository and checkout branch/commit
3. Re-parse pipeline YAML and create execution plan with topologically sorted jobs
4. Mark run as `RUNNING`
5. Execute jobs one-by-one using Docker, updating DB status as each job progresses
6. Stop on first failure (fail-fast), unless job has `failures: true`
7. Mark remaining jobs as `SKIPPED`
8. Clean up workspace; mark run as `SUCCESS` or `FAILED`

## API Endpoints

### Execute Pipeline

**Endpoint**: `POST /api/v1/execution/execute`

Executes a complete pipeline by cloning the repository and running the specified pipeline file.

**Request Body**:
```json
{
  "pipelineName": "default",
  "pipelineFilePath": ".pipelines/default.yaml",
  "gitMetadata": {
    "repositoryUrl": "https://github.com/user/repo.git",
    "branch": "main",
    "commitHash": "abc123def456..."
  }
}
```

**Response** (HTTP 202 - Accepted):
```json
{
  "executionId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "pipelineName": "default",
  "runNumber": 42,
  "status": "PENDING",
  "message": "Pipeline queued for execution"
}
```

Use `GET /api/v1/status/{pipeline}/runs/{runNo}` (Report Service) to track progress.

**Response** (HTTP 400 - Validation Error):
```json
{
  "executionId": "uuid-string",
  "pipelineName": "default",
  "runNumber": 0,
  "status": "VALIDATION_FAILED",
  "message": "Pipeline validation failed: Circular dependency detected between jobs"
}
```

## Core Services

### ExecutionService

Orchestrates sequential pipeline execution with fail-fast behavior and delegates all database writes to `ExecutionPersistenceService`.

**Responsibilities**:
- Upsert pipeline definition and open a pipeline run record
- Pre-create all stage/job rows as `PENDING` for immediate UI visibility
- Execute jobs sequentially using DockerExecutor
- Track job status (COMPLETED, FAILED, SKIPPED)
- Mark remaining jobs as SKIPPED on failure
- Update DB status for each stage and job as execution progresses
- Aggregate results into ExecutionResult

**Execution Rules**:
- Jobs execute in dependency order (topological sort)
- Each job waits for its dependencies to complete successfully
- First job failure stops all remaining jobs, unless the job has `failures: true` — in that case execution continues with subsequent jobs
- Skipped jobs receive SKIPPED status with informative message

### ExecutionPersistenceService

Handles all database writes for pipeline execution records using a **fail-open** strategy — a DB outage produces warning logs but never interrupts pipeline execution.

**Responsibilities**:
- Find or create pipeline definition row (`upsertPipeline`)
- Insert pipeline run row with `status=RUNNING` (`createPipelineRun`)
- Pre-create all stage and job rows as `PENDING` (`preCreateStagesAndJobs`)
- Transition stage and job rows through `PENDING → RUNNING → SUCCESS|FAILED`
- Close the pipeline run row with final status (`updatePipelineRun`)

### WorkspaceService

Prepares isolated workspace directories for pipeline execution.

**Responsibilities**:
- Create temporary workspace directory
- Checkout specified branch and commit
- Clean up workspace after execution

**Workspace Lifecycle**:
1. Create unique temporary directory with prefix `cicd-workspace-`
2. Clone repository from specified URL
3. Checkout requested branch and commit
4. Return workspace path for Docker bind-mount
5. Recursively delete directory after execution

### DockerExecutor

Executes individual jobs inside Docker containers.

**Responsibilities**:
- Pull Docker images if not present locally
- Create and start containers with workspace bind-mount
- Execute job script steps sequentially
- Capture combined stdout/stderr output
- Track execution time and exit codes
- Clean up containers after execution

**Container Configuration**:
- Image: Specified in job configuration
- Working Directory: `/workspace`
- Volume Mount: Host workspace → `/workspace`
- Command: Shell execution of script steps
- Network: Default (future: isolated networks)

**Execution Steps**:
1. Pull image if not cached locally
2. Create container with workspace bind-mount at `/workspace`
3. Start container with long-running tail process
4. Execute each script step via `docker exec`
5. Capture combined stdout/stderr from each step
6. Check exit code after each step
7. Stop execution on first non-zero exit code
8. Stop and remove container (cleanup)

## Data Models

### Domain Models

**ExecutionResult**: Complete pipeline execution outcome
- `jobResults`: All job results in execution order
- `success`: Overall pipeline success flag
- `failedJobName`: Name of first failed job, or null
- `runNumber`: Database-assigned run number for this pipeline execution

**JobResult**: Individual job execution outcome
- `jobName`: Job identifier
- `status`: COMPLETED, FAILED, or SKIPPED
- `output`: Combined stdout/stderr logs
- `exitCode`: Process exit code (0 = success)
- `executionTime`: Wall-clock execution duration
- `failures`: Whether this job was configured with `failures: true`

**JobStatus**: Job execution state enumeration
- `PENDING`: Queued (used for pre-created DB rows)
- `RUNNING`: Executing (used for DB status transitions)
- `COMPLETED`: Success (exit code 0)
- `FAILED`: Failure (non-zero exit code)
- `SKIPPED`: Not executed due to dependency failure

**ExecutionStatus**: Shared status enum for DB entities and API responses
- `PENDING`: Row created, execution not started (stages and jobs only)
- `RUNNING`: Currently executing
- `SUCCESS`: Completed successfully
- `FAILED`: Failed or skipped due to prior failure
- `VALIDATION_FAILED`: Invalid YAML configuration (API response only, never written to DB)

### Database Entities

**PipelineEntity** (`pipelines`): Pipeline definition, keyed by SHA-256 hash of repo URL + pipeline name. Rows are upserted at the start of each execution.

**PipelineRunEntity** (`pipeline_runs`): One row per execution. `run_no` is assigned by a PostgreSQL trigger scoped per pipeline and read back via `@Generated` — no extra query needed.

**StageRunEntity** (`stage_runs`): One row per stage per execution. Pre-created as `PENDING` before execution begins.

**JobRunEntity** (`job_runs`): One row per job per execution. Pre-created as `PENDING`; transitions through `RUNNING` to `SUCCESS` or `FAILED`.

### Request/Response Models

**PipelineExecutionRequest**: Inbound execution request
- `pipelineName` (required): Pipeline identifier
- `pipelineFilePath` (required): Relative path to the pipeline YAML file from the repository root (e.g. `.pipelines/default.yaml`)
- `gitMetadata` (required): Repository, branch, commit

**GitMetadata**: Git repository context
- `repositoryUrl` (required): Git clone URL
- `branch` (required): Branch name
- `commitHash` (required): Full commit SHA-1

**PipelineExecutionResponse**: Execution tracking response
- `executionId`: Unique UUID for this execution
- `pipelineName`: Pipeline identifier
- `runNumber`: Sequential run counter assigned by DB trigger; `0` if execution failed before DB record was created
- `status`: SUCCESS, FAILED, or VALIDATION_FAILED
- `message`: Human-readable outcome description

## Configuration

### Application Properties

The Execution Service uses Spring Boot configuration:

**Key Properties**:
- `server.port` - HTTP server port (default: 8081)
- `spring.application.name` - Service name (execution-service)

### Environment Variables

Override configuration via environment:

```bash
# Service port
SERVER_PORT=8081

# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=cicd
DB_USERNAME=postgres
DB_PASSWORD=cicd

# RabbitMQ
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672

# Docker host (if not using default)
DOCKER_HOST=unix:///var/run/docker.sock
```

### Kubernetes Deployment

```bash
# Apply all k8s manifests (from repo root)
kubectl apply -f k8s/postgres/
kubectl apply -f k8s/rabbitmq/
kubectl apply -f k8s/execution-service/

# Or deploy the full system via Helm
helm install cicd-system helm/cicd-system/
```

The Execution Service K8s Deployment mounts the Docker socket (`/var/run/docker.sock`) so it can execute jobs in containers on the host, and a `/tmp` volume for workspace directories.

### Configuration Examples

**Local Development**:
```properties
server.port=8081
spring.application.name=execution-service
```

**Production**:
```properties
server.port=8081
spring.application.name=execution-service
logging.level.edu.northeastern=INFO
```

### Docker Requirements

**Prerequisites**:
- Docker daemon running and accessible
- Default: `unix:///var/run/docker.sock`
- Remote: `tcp://docker-host:2375`
- Docker API version: 1.41+

**Permissions**:
- Service user must have Docker socket access
- On Linux: Add user to `docker` group
- On macOS/Windows: Docker Desktop handles permissions

## Error Handling

### Validation Errors

Pipeline configuration validation failures return HTTP 400 with details about what's invalid in the configuration.

**Common Validation Errors**:
- Circular dependencies between jobs
- References to non-existent jobs
- Invalid stage or job names
- Missing required fields

### Workspace Errors

Git operations (clone, checkout) failures throw `WorkspaceException`:

**Common Workspace Errors**:
- Repository not found or unreachable
- Invalid branch or commit
- Authentication failures
- Network timeouts
- Insufficient disk space

### Docker Errors

Container operations failures throw `DockerException`:

**Common Docker Errors**:
- Image not found or pull failed
- Container creation failed
- Insufficient resources (CPU, memory)
- Docker daemon unreachable
- Volume mount failures

### Job Execution Failures

Job failures are captured in JobResult with FAILED status, including:
- Exit code from failed command
- Complete output logs (stdout/stderr)
- Execution time before failure
- Error messages from container

### Database Errors

All database writes are fail-open: exceptions are caught and logged as warnings by `ExecutionPersistenceService` so a DB outage never interrupts pipeline execution.

## Running the Service

### Prerequisites

- **Java**: 21 or higher
- **Spring Boot**: 4.x
- **Docker**: Running daemon with accessible socket
- **Git**: Available on system PATH (for JGit operations)
- **PostgreSQL**: Running instance for execution persistence

### Local Development

```bash
# Build the service
./gradlew :execution-service:build

# Run locally (default port 8081)
./gradlew :execution-service:bootRun

# Run with custom port
./gradlew :execution-service:bootRun --args='--server.port=8082'

# Run with debug logging
./gradlew :execution-service:bootRun --args='--logging.level.edu.northeastern=DEBUG'
```

The service starts on http://localhost:8081 by default.

### Docker Compose

Start with supporting services:

```bash
# Start Postgres, RabbitMQ
docker-compose up -d

# Start execution service
./gradlew :execution-service:bootRun
```

## Testing

### Unit Tests

Run unit tests with coverage:

```bash
# Run all tests
./gradlew :execution-service:test

# Generate coverage report
./gradlew :execution-service:jacocoTestReport

# View coverage report
open execution-service/build/reports/jacoco/test/html/index.html
```

### Integration Tests

Test with real Docker daemon:

```bash
# Requires Docker running
./gradlew :execution-service:integrationTest
```

### Manual Testing

Test execution endpoint using curl:

```bash
# Health check
curl http://localhost:8081/actuator/health

# Execute simple pipeline
curl -X POST http://localhost:8081/api/v1/execution/execute \
  -H "Content-Type: application/json" \
  -d '{
    "pipelineName": "test",
    "pipelineFilePath": ".pipelines/test.yaml",
    "gitMetadata": {
      "repositoryUrl": "https://github.com/user/repo.git",
      "branch": "main",
      "commitHash": "abc123def456"
    }
  }'
```

## Technology Stack

### Core Dependencies

- **Spring Boot** 4.x: Application framework
- **Spring WebFlux**: Reactive web framework for non-blocking I/O
- **Spring Validation**: Request validation with Jakarta Bean Validation
- **Spring Data R2DBC**: Reactive repository layer for PostgreSQL persistence
- **RabbitMQ (AMQP)**: Async message queue for decoupled pipeline execution
- **Flyway**: Database schema migrations
- **Docker Java**: Docker API client library
- **JGit**: Git operations (clone, checkout)
- **Lombok**: Reduce boilerplate code
- **SLF4J + Logback**: Logging infrastructure
- **OpenTelemetry Java Agent**: Distributed tracing and metrics instrumentation
- **OTel Logback Appender**: Forwards structured logs via OTLP to the OTel Collector

### Docker Integration

- **docker-java**: Docker API client library
- **Apache HttpClient 5**: Docker HTTP transport
- **Container Lifecycle**: Create, start, exec, stop, remove
- **Image Management**: Pull, inspect

### Database

- **PostgreSQL**: Execution history persistence
- **R2DBC PostgreSQL driver**: Reactive PostgreSQL connectivity
- **Flyway**: Version-controlled schema migrations
- **PostgreSQL triggers**: Auto-increment `run_no` per pipeline, `updated_at` maintenance

### Testing

- **JUnit 5**: Testing framework
- **Spring Test**: Integration testing support
- **Mockito**: Mocking framework for unit tests
- **TestContainers**: Real PostgreSQL instances for R2DBC integration tests

## Observability

### Distributed Tracing

The Execution Service is instrumented with the OpenTelemetry Java agent. Traces propagate end-to-end from the HTTP submission through the RabbitMQ consumer and into Docker container execution. Each pipeline run stores its `trace_id` in the database, which is surfaced in `cicd report` output for direct correlation in Grafana Tempo.

### Metrics

Custom OTel metrics are emitted per pipeline run, stage, and job:
- Pipeline execution count and duration
- Job success/failure rates
- Stage execution times

Metrics are exported via OTLP to the OTel Collector, which remote-writes to Prometheus.

### Structured Logging

Logs are forwarded to Grafana Loki via the OTel Logback appender (`OtelLogbackInstaller`). Container stdout/stderr is streamed from `DockerExecutor` to SLF4J with MDC fields (`pipeline`, `run_no`, `job`, `source=container`), making container output searchable in Loki alongside service logs.

**Log Levels**:
- `INFO`: Service startup, execution lifecycle, job completions
- `DEBUG`: Detailed execution flow, Docker operations
- `ERROR`: Failures, exceptions with stack traces

**Example Log Output**:
```
INFO  ExecutionServiceApplication - Starting ExecutionServiceApplication
INFO  ExecutionController - Received execution request (ID: abc-123): pipeline=default
INFO  WorkspaceService - Preparing workspace for repo=https://github.com/user/repo.git
INFO  ExecutionService - Starting pipeline execution: 5 stage(s)
INFO  ExecutionService - Executing job 'build'
INFO  DockerExecutor - Pulling image: maven:3.8
INFO  ExecutionService - Job 'build' finished: COMPLETED
ERROR ExecutionService - Job 'test' failed with exit code 1, skipping 2 remaining jobs
INFO  ExecutionController - Pipeline execution completed (ID: abc-123)
```

## Monitoring

### Health Endpoints

Spring Boot Actuator provides monitoring endpoints:

```bash
# Overall health
curl http://localhost:8081/actuator/health

# Detailed health with components
curl http://localhost:8081/actuator/health/diskSpace
```

### Metrics

Metrics are exported via OTLP to the OTel Collector and stored in Prometheus:
- Execution rate and latency per pipeline
- Job success/failure counts by pipeline and stage
- Workspace preparation and Docker pull times
- Container lifecycle counts

## Security Considerations

### Current Implementation

- Input validation on all request fields
- Git URL validation before cloning
- Docker image name validation
- Workspace isolation (unique temp directories)
- Container cleanup (always removes containers)
- No privileged container access

### Docker Security

- Containers run with default user (non-root where possible)
- No privileged mode enabled
- No host network access
- Bind mounts are scoped to workspace only
- Resource limits enforced (future)

### Future Enhancements

- API authentication/authorization
- Rate limiting per pipeline/user
- Signed Docker images only
- Network isolation between jobs
- Mandatory resource limits (CPU, memory, disk)
- Audit logging for all executions
- Secret management for credentials

## Troubleshooting

### Common Issues

**Docker Daemon Not Running**
- **Symptom**: Connection refused errors
- **Solution**: Start Docker daemon
  - Linux: `sudo systemctl start docker`
  - macOS/Windows: Start Docker Desktop
- **Verification**: `docker ps` should work without errors

**Docker Permission Denied**
- **Symptom**: Permission denied accessing Docker socket
- **Solution**: Add user to docker group
  - `sudo usermod -aG docker $USER`
  - Log out and back in
  - Or run with sudo (not recommended for production)

**Git Clone Failures**
- **Symptom**: Workspace preparation fails with clone errors
- **Solutions**:
  - Verify repository URL is accessible
  - Check SSH keys for git@ URLs
  - Try HTTPS URL instead of SSH
  - Check network/firewall rules
  - Verify authentication credentials

**Image Pull Failures**
- **Symptom**: Image not found errors
- **Solutions**:
  - Verify image exists: `docker pull <image>`
  - Check image name spelling
  - Ensure Docker Hub or registry is accessible
  - Use fully qualified image names
  - Check registry authentication

**Port Already in Use**
- **Symptom**: Web server failed to start
- **Solutions**:
  - Stop other service using port 8081
  - Change port: `--server.port=8082`
  - Find process: `lsof -i :8081` (macOS/Linux)
  - Kill process: `kill <PID>`

**Out of Disk Space**
- **Symptom**: Workspace creation or Docker operations fail
- **Solutions**:
  - Clean up old workspaces in `/tmp`
  - Remove unused Docker images: `docker image prune -a`
  - Remove unused containers: `docker container prune`
  - Remove unused volumes: `docker volume prune`
  - Increase available disk space

**Slow Job Execution**
- **Symptom**: Jobs taking longer than expected
- **Possible Causes**:
  - Large repository clone
  - Slow Docker image pull
  - Resource contention
  - Network latency
- **Solutions**:
  - Cache Docker images locally
  - Use smaller base images
  - Optimize repository structure
  - Check system resource usage

## Development Guidelines

### Adding New Features

1. Update domain models in `model/` package
2. Update entities in `entity/` and repositories in `repository/` if DB changes are needed
3. Extend service logic in `service/` package
4. Modify Docker executor if container changes needed
5. Update DTOs for API changes
6. Add comprehensive Javadoc for all public methods
7. Write unit tests (target: 70%+ coverage)
8. Write integration tests for end-to-end flows
9. Update this README with new features

### Code Quality Standards

- Follow Spring Boot best practices and conventions
- Write comprehensive Javadoc for all public APIs
- Maintain test coverage above 70%
- Use Lombok to reduce boilerplate code
- Run quality checks before committing: `./gradlew check`
- Pass Checkstyle and SpotBugs validation
- Follow existing code patterns and structure

### Testing Best Practices

- Mock external dependencies (Docker, Git, DB repositories) in unit tests
- Use Testcontainers for integration tests
- Test all error handling paths
- Verify cleanup logic (workspaces, containers)
- Test with various pipeline configurations
- Validate edge cases (empty jobs, missing dependencies)
- Test concurrent executions

## Integration with Other Services

### API Gateway Integration

The Execution Service receives requests from the API Gateway:
- API Gateway forwards validated requests including the pipeline file path and Git metadata
- Execution Service clones the repository, reads the YAML, and executes the pipeline
- Returns detailed execution results including the DB-assigned run number
- API Gateway formats for CLI consumption

---
