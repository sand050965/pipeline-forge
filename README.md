# Pipeline Forge (Distributed CI/CD Pipeline Execution System)

A custom CI/CD system designed for local and remote pipeline execution with comprehensive validation, reporting, and Git integration. This project provides developers with a powerful command-line interface to manage, validate, and execute CI/CD pipelines defined in YAML configuration files.

## Project Overview

This system addresses the need for a flexible CI/CD solution that supports both local development workflows and remote execution. The project has evolved from a monolithic architecture to a distributed microservices system enabling scalable pipeline execution.

### Vision & Goals

- **Local-First Development**: Run complete CI/CD pipelines on developer machines without cloud dependencies
- **Git-Native**: All configuration lives in the repository, versioned alongside code
- **Developer-Friendly**: Clear error messages, intuitive CLI, and comprehensive validation
- **Flexible Deployment**: Seamless transition from local to remote execution
- **Microservices Architecture**: Independent, scalable services for execution and orchestration

### Development Phases

**Phase 1**: All system components run locally on the developer's machine ✅
**Phase 2**: Distributed architecture with remote component execution, local CLI, and full-stack observability (distributed tracing, metrics, structured log aggregation) ✅

## Current Features ✅

### Pipeline Validation (`verify` command)

The system provides comprehensive validation of YAML pipeline configurations:

- **Multi-Level Validation**: YAML structure → Stages → Jobs → Dependencies
- **Precise Error Reporting**: IDE-friendly format with `filename:line:column: ERROR, message`
- **Multiple Error Collection**: See all issues at once instead of fix-one-at-a-time
- **Type Safety**: Validates data types to catch configuration mistakes early
- **Dependency Analysis**: Detects circular dependencies with complete cycle reporting
- **Repository Awareness**: Enforces `.pipelines/` directory structure
- **Directory Validation**: Validates all pipelines with duplicate name detection

#### Usage
```bash
# Verify a specific pipeline file
cicd verify .pipelines/default.yaml

# Verify all pipelines in directory
cicd verify .pipelines/
```

#### Example Output

**Valid Configuration:**
```bash
$ cicd verify .pipelines/default.yaml
OK: /path/to/repo/.pipelines/default.yaml
```

**Invalid Configuration:**
```bash
$ cicd verify .pipelines/bad-config.yaml
.pipelines/bad-config.yaml:2:9: ERROR, Wrong type for 'pipeline.name'. Expected String, got integer
.pipelines/bad-config.yaml:15:5: ERROR, Job 'deploy' references non-existent stage 'production'
.pipelines/bad-config.yaml:20:1: ERROR, Cycle detected in 'needs' requirements: job1 -> job2 -> job3 -> job1
```

### Dry Run (`dryrun` command)

Preview pipeline execution order without running:

```bash
cicd dryrun .pipelines/default.yaml
```

The `dryrun` command validates the configuration file and prints the execution order showing stages and their jobs with full job details.

#### Example Output
```yaml
build:
   compile:
      image: gradle:8.12-jdk21
      script:
         - ./gradlew classes
      failures: false
test:
   unittests:
      image: gradle:8.12-jdk21
      script:
         - ./gradlew test
      failures: false
   reports:
      image: gradle:8.12-jdk21
      script:
         - ./gradlew check
      failures: false
docs:
   javadoc:
      image: gradle:8.12-jdk21
      script:
         - ./gradlew javadoc
      failures: false
```

**Note**: The output of `dryrun` is valid YAML but not a valid pipeline configuration format. It shows the execution plan with jobs organized by stage in execution order, respecting job dependencies defined via `needs`.

### Pipeline Execution (`run` command)

Execute pipelines remotely through the microservices architecture. By default the CLI sends the
repository remote URL (HTTPS) and falls back to the local path if no `origin` is configured.
Use the optional `--local` flag to force the local repository path.

```bash
# Run pipeline by name
cicd run --name default

# Run specific pipeline file
cicd run --file .pipelines/pipeline.yaml

# Run from specific branch
cicd run --name default --branch feature-x

# Run specific commit on branch
cicd run --name default --branch main --commit abc123

# Run pipeline using local repository path (optional)
cicd run --name default --local
```

**Key Capabilities:**
- Submits pipeline to Execution Service and returns immediately (non-blocking, 202 Accepted)
- Execute pipelines using Docker containers via Execution Service
- Support for specific Git branches and commits with validation
- Asynchronous job execution via RabbitMQ message queue
- Real-time execution tracking with unique execution IDs
- Git state validation (branch/commit matching)
- Workspace preparation with repository cloning
- Comprehensive error handling and reporting

### Execution Status (`status` command)

Query the live or most recent execution status of a pipeline:

```bash
# Active/most recent run for a repository
cicd status --repo https://github.com/org/repo.git

# Specific run by pipeline name and run number
cicd status --pipeline default --run 1
```

**Returned Information:**
- Per-stage and per-job execution status (`PENDING`, `RUNNING`, `SUCCESS`, `FAILED`, `SKIPPED`)
- Live state while a pipeline is actively running
- Most recent completed run if no pipeline is currently running

### Pipeline Reporting (`report` command)

Track and analyze pipeline execution history:
```bash
# All runs for a pipeline
cicd report --pipeline default

# Specific run details
cicd report --pipeline default --run 1

# Stage-level details
cicd report --pipeline default --run 1 --stage build

# Job-level details
cicd report --pipeline default --run 1 --stage build --job compile
```

**Tracked Information:**
- Execution timestamps (start/end)
- Success/failure status
- Git context (branch, commit hash, repository)
- Run numbers and sequences
- Stage and job execution details
- Job output logs and exit codes
- `trace-id` for correlating a run directly in Grafana Tempo

### Observability

The system ships with a full observability stack covering traces, metrics, and logs across all three microservices.

**Distributed Tracing (OpenTelemetry + Tempo)**
- All services (API Gateway, Execution Service, Report Service) are instrumented with the OpenTelemetry Java agent
- Traces propagate end-to-end from CLI request through execution and into Docker job containers
- `cicd report` output includes a `trace-id` field so any run can be correlated directly in Grafana Tempo

**Metrics (OpenTelemetry + Prometheus)**
- Services export OTLP metrics to the OTel Collector, which remote-writes to Prometheus
- Execution Service emits custom counters and timers per pipeline run, stage, and job

**Structured Logging (OTel Logback + Loki)**
- All services use an OTel Logback appender to forward structured JSON logs via OTLP
- Docker container stdout/stderr is streamed with MDC context (pipeline, run number, job name) and stored in Loki
- Logs are queryable by `run_no`, `pipeline`, `job`, and `source=container`

**Grafana Dashboards**
Four pre-provisioned dashboards are available at `http://localhost:3000` (no login required):

| Dashboard | Description |
|-----------|-------------|
| Pipeline Overview | Aggregated run counts, success/failure rates, and duration trends |
| Stage & Job Breakdown | Per-stage and per-job execution time and status |
| Logs Viewer | Structured log search across all services and container output |
| Trace Explorer | End-to-end trace search and waterfall view via Tempo |

**Observability Stack Ports**

| Service | Port | Purpose |
|---------|------|---------|
| Grafana | 3000 | Unified observability UI |
| Prometheus | 9090 | Metrics storage and query |
| Loki | 3100 | Log aggregation |
| Tempo | 3200 | Distributed trace storage |
| OTel Collector (gRPC) | 4317 | OTLP ingestion from services |
| OTel Collector (HTTP) | 4318 | OTLP ingestion from services |

All observability services start automatically with `docker-compose up -d`.

### Microservices Architecture

The system operates as a distributed architecture with the following components:

**CLI (Command-Line Interface)**
- Local operations: `verify`, `dryrun` (direct pipeline library usage)
- Remote operations: `run`, `status`, `report` (HTTP communication with API Gateway)
- Git state validation and repository detection
- Pipeline configuration management

**API Gateway Service**
- Unified entry point for all CLI requests (port 8080)
- Request routing to Execution and Report services
- Input validation and error translation
- Circuit breaker and retry support via Resilience4j
- Reactive, non-blocking with Spring Cloud Gateway WebFlux

**Execution Service**
- Accepts pipeline execution requests and returns 202 Accepted immediately (port 8081)
- Validates YAML and initializes DB records (`PENDING`) before publishing
- Publishes execution messages to RabbitMQ for asynchronous processing
- Consumer clones workspace, executes stages sequentially; jobs within each stage run concurrently via parallel wave scheduling
- Failed job skips its dependents only; independent jobs in later waves still run. Full stage skip propagates to subsequent stages (overridden per job with `failures: true`)
- Execution history persistence to PostgreSQL via R2DBC

**Report Service**
- Query and serve pipeline execution history (port 8082)
- Live status queries (returns `RUNNING`/`PENDING` states for active pipelines)
- Per-pipeline, per-run, per-stage, and per-job reporting
- PostgreSQL-backed execution record retrieval
- Includes `trace-id` in run output for direct correlation in Grafana Tempo

**Observability Stack**
- **OpenTelemetry Collector** — Receives OTLP signals (traces, metrics, logs) from all services and routes them to Tempo, Prometheus, and Loki
- **Prometheus** — Stores and queries metrics (port 9090); fed via OTel Collector remote write
- **Loki** — Log aggregation (port 3100); receives structured service logs and container stdout/stderr
- **Tempo** — Distributed trace backend (port 3200); stores end-to-end traces from all services
- **Grafana** — Unified observability UI (port 3000) with four pre-provisioned dashboards

**RabbitMQ**
- Decouples pipeline submission from execution (durable topic exchange)
- Enables async processing with configurable concurrency and retry backoff
- Survives broker restarts via durable queue configuration

**PostgreSQL**
- Source of truth for all execution state
- Schema managed via Flyway migrations

## Pipeline Configuration Format

### Basic Structure
```yaml
pipeline:
  name: my-pipeline
  description: A sample CI/CD pipeline

stages:
  - build
  - test
  - deploy

jobs:
  compile:
    - stage: build
    - image: gradle:jdk21
    - script: gradle build

  unit-tests:
    - stage: test
    - image: gradle:jdk21
    - script: gradle test

  check-styles:
    - stage: test
    - image: gradle:jdk21
    - script: gradle checkstyleMain checkstyleTest 
    - needs: [unit-tests]

  deploy-prod:
    - stage: deploy
    - image: alpine:latest
    - script: ./deploy.sh
```

### Configuration Reference

#### Pipeline Metadata
| Key | Type | Required | Description |
|-----|------|----------|-------------|
| `pipeline.name` | String | ✅ | Unique pipeline identifier |
| `pipeline.description` | String | ⭕ | Optional pipeline description |

#### Stages
| Key | Type | Required | Description |
|-----|------|----------|-------------|
| `stages` | List[String] | ⭕ | Stage names in execution order. Defaults to `["build", "test", "docs"]` |

#### Jobs
| Key | Type | Required | Description |
|-----|------|----------|-------------|
| `<job-name>.stage` | String | ✅ | Stage assignment (must match defined stage) |
| `<job-name>.image` | String | ✅ | Docker image from DockerHub |
| `<job-name>.script` | String or List[String] | ✅ | Command(s) to execute |
| `<job-name>.needs` | List[String] | ⭕ | Job dependencies (same stage only) |
| `<job-name>.failures` | Boolean | ⭕ | If `true`, pipeline continues even if this job fails. Default: `false` |

### Validation Rules

A valid pipeline configuration must satisfy:

1. **Stage Requirements**
   - At least one stage defined (or use defaults)
   - No empty stages (each stage must have ≥1 job)
   - Stage names must be unique

2. **Job Requirements**
   - Job names must be unique within pipeline
   - Required fields: `stage`, `image`, `script`
   - Stage reference must be valid
   - Script can be single string or list of strings

3. **Dependency Rules**
   - `needs` list cannot be empty if specified
   - Referenced jobs must exist in same stage
   - No circular dependencies allowed

### Example: Complex Pipeline
```yaml
pipeline:
  name: default
  description: Default build for nightly builds

stages:
  - build
  - test
  - doc
  - deploy

# Build Stage
compile:
  - stage: build
  - image: gradle:jdk21-corretto
  - script: 'gradle build'

# Test Stage - Multiple Jobs
statics:
  - stage: test
  - image: gradle:jdk21-corretto
  - script:
    - gradle checkstyleMain
    - gradle checkstyleTest
    - gradle spotbugsMain

tests:
  - stage: test
  - image: gradle:jdk21-corretto
  - needs: [statics]
  - script: 'gradle test jacocoTestReport'

# Documentation
docs:
  - stage: doc
  - image: gradle:jdk21-corretto
  - script: 'gradle javadoc'

# Deployment
package:
  - stage: deploy
  - image: gradle:jdk21-corretto
  - script: 'gradle assembleDist'
```

## Architecture

### System Architecture

The system uses a microservices architecture with clear separation of concerns:

**CLI Module** - Local command-line interface
- Path: `cli/`
- Direct pipeline library integration for validation and dry run
- HTTP client for remote execution, status queries, and reporting via API Gateway
- Git state validation and repository management

**Pipeline Library** - Core validation and planning logic
- Path: `pipeline-lib/`
- YAML parsing with position tracking
- Multi-stage validation pipeline
- Execution plan generation with dependency resolution
- Shared by CLI and services

**API Gateway Service** - HTTP routing layer
- Path: `api-gateway/`
- Spring Boot + WebFlux (reactive)
- Request validation and error translation
- Routes to Execution Service and Report Service

**Execution Service** - Pipeline orchestration
- Path: `execution-service/`
- Spring Boot application
- Git workspace management (JGit)
- Docker container execution
- Parallel wave scheduling within each stage; stages run sequentially
- Execution history persistence to PostgreSQL

**Report Service** - Execution history
- Path: `report-service/`
- Spring Boot application
- PostgreSQL-backed execution record queries
- Per-pipeline, per-run, per-stage, per-job reporting

### Project Structure

```
cli/
pipeline-lib/
api-gateway/
execution-service/
report-service/
```

### Current Validation Architecture

The validation system uses a four-stage pipeline:

1. **YamlValidator**: Basic YAML structure and required fields
2. **StageValidator**: Stage rules and requirements
3. **JobValidator**: Job definitions and constraints
4. **NeedValidator**: Dependency validation and cycle detection

**Error Reporting System:**
- `PositionAwareYamlParser`: Tracks precise line/column positions using SnakeYAML's Composer API
- `ValidationErrorCollector`: Accumulates errors during validation
- `ValidationError`: Immutable error records with location data

This architecture enables:
- Reporting multiple errors simultaneously
- Exact file locations for each error
- IDE-friendly error message formatting (`file:line:column: message`)

### Execution Architecture

The execution system uses an asynchronous publish-subscribe architecture via RabbitMQ:

**Components:**
- `ExecutionController`: Accepts HTTP requests, validates YAML, initializes DB, publishes to RabbitMQ, returns 202
- `PipelineExecutionConsumer`: RabbitMQ listener that performs actual pipeline execution asynchronously
- `ExecutionService`: Orchestrates pipeline execution with topological job ordering
- `ExecutionPersistenceService`: Writes execution records to PostgreSQL via R2DBC (fail-open)
- `WorkspaceService`: Prepares Git workspaces (clone, checkout) for execution
- `DockerExecutor`: Executes jobs in isolated Docker containers
- `ExecutionResult`: Aggregates job results with success/failure status
- `JobResult`: Tracks individual job execution (status, output, exit code, time)

**Process:**
1. HTTP POST received — validate YAML, initialize DB records as `PENDING`, publish `PipelineExecutionMessage` to RabbitMQ
2. Return `202 Accepted` immediately with assigned run number
3. Consumer picks message from queue asynchronously
4. Clone workspace and re-parse pipeline YAML
5. Mark run as `RUNNING` in DB
6. For each stage (sequential): group jobs into dependency waves, run each wave concurrently via `CompletableFuture`
7. A failed job skips its direct/transitive dependents; independent jobs in later waves are unaffected
8. If any non-optional job fails, all subsequent stages are fully skipped
9. Clean up workspace; mark run as `SUCCESS` or `FAILED`

### CLI Framework

Built with [Picocli](https://picocli.info/) for:
- Command parsing and validation
- Automatic help generation
- Type-safe option handling
- Subcommand architecture

**Exit Codes:**
The CLI uses standardized exit codes to indicate different failure conditions:

**Success:**
- `0` (OK) - Command completed successfully

**Operational Errors (1-5):**
- `1` (CONFIG_VALIDATION_ERROR) - Invalid YAML structure, missing fields, circular dependencies, or validation errors
- `2` (RUNTIME_EXECUTION_ERROR) - Pipeline execution failed
- `3` (GIT_REPOSITORY_ERROR) - Command not run from Git repository root or Git operations failed
- `4` (DOCKER_ERROR) - Container operations failed
- `5` (DATABASE_ERROR) - Storage/retrieval of execution history failed

**User Input Errors (10+):**
- `10` (INVALID_CLI_ARGUMENTS) - Missing arguments, invalid path format, or malformed options

## Evaluator Guide

Step-by-step instructions for evaluators to run all required examples after installing the system.

### Prerequisites

- Docker Desktop installed and running
- Java 21 or higher
- Git

---

### System Setup

Follow the **[Non-Developer Guide](dev-docs/non-developer-guide.md)** to install Docker, download the release files (`docker-compose.yml` and the CLI), install the CLI, and start the services. Then return here for the evaluation examples.

---

### Example Repositories

Two public repositories are used across all examples below:

| Repository | Language | Demonstrates |
|------------|----------|--------------|
| [LelinZheng/Calendar-CLI-App](https://github.com/LelinZheng/Calendar-CLI-App) | Java/Maven | Successful pipeline, validation failures, reports |
| [helloxujc/failed-cicd-run-click](https://github.com/helloxujc/failed-cicd-run-click) | Python | Failed pipeline run, reports |

Clone both repositories into the parent directory of `c-team`:

```bash
cd ..
git clone https://github.com/LelinZheng/Calendar-CLI-App.git
git clone https://github.com/helloxujc/failed-cicd-run-click.git
```

---

### Example 1: Successful Pipeline & Generate Report

**Repository:** `Calendar-CLI-App`

The `PR` pipeline compiles the project, runs static analysis (SpotBugs + Checkstyle), executes unit tests and BATS integration tests, and generates Javadocs. All jobs are expected to pass.

```bash
cd Calendar-CLI-App
```

**1a. Preview the execution plan (no services required):**

```bash
cicd dryrun .pipelines/test.yaml
```

Expected output:
```
OK: dryrun validation succeeded
build:
    build:
        image: maven:3.9-eclipse-temurin-21
        script:
            - mvn -B clean package -DskipTests
        failures: false
test:
    spotbugs:
        image: maven:3.9-eclipse-temurin-21
        script:
            - mvn -B spotbugs:check
        failures: false
    checkstyle:
        image: maven:3.9-eclipse-temurin-21
        script:
            - mvn -B checkstyle:check
        failures: false
    unit-tests:
        image: maven:3.9-eclipse-temurin-21
        script:
            - mvn -B test
        needs:
            - spotbugs
            - checkstyle
        failures: false
    bats-tests:
        image: eclipse-temurin:21-alpine
        script:
            - apk add --no-cache bash bats python3
            - bats bats/calctl.bats
        needs:
            - unit-tests
        failures: false
doc:
    javadoc:
        image: maven:3.9-eclipse-temurin-21
        script:
            - mvn -B javadoc:javadoc
        failures: false
```

**1b. Run the pipeline:**

```bash
cicd run --name PR
```

Expected output:
```
Pipeline started. Execution ID: <uuid>
Status: SUCCESS
Pipeline completed successfully. All 6 jobs passed.
```

**1c. View reports (all levels):**

```bash
# All runs for this pipeline
cicd report --pipeline PR
```

Expected output:
```yaml
pipeline:
  name: PR
  runs:
  - run-no: 1
    status: SUCCESS
    git-repo: /path/to/Calendar-CLI-App
    git-branch: main
    git-hash: <commit-hash>
    start: '<timestamp>'
    end: '<timestamp>'
```

```bash
# Run-level detail
cicd report --pipeline PR --run 1
```

Expected output:
```yaml
pipeline:
  name: PR
  run-no: 1
  status: SUCCESS
  start: '<timestamp>'
  end: '<timestamp>'
  stages:
  - name: build
    status: SUCCESS
    start: '<timestamp>'
    end: '<timestamp>'
  - name: test
    status: SUCCESS
    start: '<timestamp>'
    end: '<timestamp>'
  - name: doc
    status: SUCCESS
    start: '<timestamp>'
    end: '<timestamp>'
```

```bash
# Stage-level detail
cicd report --pipeline PR --run 1 --stage test
```

Expected output:
```yaml
pipeline:
  name: PR
  run-no: 1
  status: SUCCESS
  start: '<timestamp>'
  end: '<timestamp>'
  stage:
    name: test
    status: SUCCESS
    start: '<timestamp>'
    end: '<timestamp>'
    jobs:
    - name: spotbugs
      status: SUCCESS
      start: '<timestamp>'
      end: '<timestamp>'
      failures: false
    - name: checkstyle
      status: SUCCESS
      start: '<timestamp>'
      end: '<timestamp>'
      failures: false
    - name: unit-tests
      status: SUCCESS
      start: '<timestamp>'
      end: '<timestamp>'
      failures: false
    - name: bats-tests
      status: SUCCESS
      start: '<timestamp>'
      end: '<timestamp>'
      failures: false
```

```bash
# Job-level detail
cicd report --pipeline PR --run 1 --stage test --job unit-tests
```

Expected output:
```yaml
pipeline:
  name: PR
  run-no: 1
  status: SUCCESS
  start: '<timestamp>'
  end: '<timestamp>'
  stage:
    name: test
    status: SUCCESS
    start: '<timestamp>'
    end: '<timestamp>'
    job:
      name: unit-tests
      status: SUCCESS
      start: '<timestamp>'
      end: '<timestamp>'
      failures: false
```

---

### Example 2: Failed Pipeline Run & Generate Report

**Repository:** `failed-cicd-run-click`

This Python project contains an intentionally broken test assertion to demonstrate pipeline run failure.

```bash
cd ../failed-cicd-run-click
```

**2a. Verify the pipeline configuration (passes validation):**

```bash
cicd verify .pipelines/click-ci.yaml
```

Expected output:
```
OK: /path/to/failed-cicd-run-click/.pipelines/click-ci.yaml
```

**2b. Preview the execution plan:**

```bash
cicd dryrun .pipelines/click-ci.yaml
```

Expected output:
```
OK: dryrun validation succeeded
build:
  install:
    image: python:3.12-slim
    script:
    - pip install pytest
    - pip install -e .
    failures: false
test:
  unit-tests:
    image: python:3.12-slim
    script:
    - pip install pytest
    - pip install -e .
    - pytest tests/test_basic.py -v
    failures: false
```

**2c. Run the pipeline (fails at the test stage):**

```bash
cicd run --file .pipelines/click-ci.yaml
```

Expected output:
```
Pipeline started. Execution ID: <uuid>
Status: FAILED
Pipeline failed at job 'unit-tests'
```

The `unit-tests` job fails because `tests/test_basic.py` contains an intentionally broken assertion (`assert rv == 99` instead of `assert rv == 42`).

**2d. View the failure report (all levels):**

```bash
# All runs for this pipeline
cicd report --pipeline click-ci
```

Expected output:
```yaml
pipeline:
  name: click-ci
  runs:
  - run-no: 1
    status: FAILED
    git-repo: /path/to/failed-cicd-run-click
    git-branch: main
    git-hash: <commit-hash>
    start: '<timestamp>'
    end: '<timestamp>'
```

```bash
# Run-level detail
cicd report --pipeline click-ci --run 1

# Stage-level detail
cicd report --pipeline click-ci --run 1 --stage test

# Job-level detail
cicd report --pipeline click-ci --run 1 --stage test --job unit-tests
```

---

### Example 3: Pipeline Validation Failures

**Repository:** `Calendar-CLI-App`

These examples use `cicd verify` only — no services required.

```bash
cd ../Calendar-CLI-App
```

**3a. Structural validation errors:**

`invalid.yaml` contains three intentional errors: a job missing the required `image` field, a job referencing an undeclared stage, and a `needs` reference to a non-existent job.

```bash
cicd verify .pipelines/invalid.yaml
```

Expected output:
```
/path/to/Calendar-CLI-App/.pipelines/invalid.yaml:9:1: ERROR, Job 'compile' missing required 'image' field
/path/to/Calendar-CLI-App/.pipelines/invalid.yaml:15:12: ERROR, Job 'deploy-job' references unknown stage 'deploy'
/path/to/Calendar-CLI-App/.pipelines/invalid.yaml:9:1: ERROR, Job 'compile' missing required 'image' field
/path/to/Calendar-CLI-App/.pipelines/invalid.yaml:23:12: ERROR, Job 'package' references non-existent job 'nonexistent-job' in 'needs'
```

Exit code: `1`

**3b. Circular dependency detection:**

`cycle-detection.yaml` creates a dependency cycle across four Maven jobs (`package → unit-tests → spotbugs → compile → package`).

```bash
cicd verify .pipelines/cycle-detection.yaml
```

Expected output:
```
/path/to/Calendar-CLI-App/.pipelines/cycle-detection.yaml:18:12: ERROR, Cycle detected in 'needs' requirements: package -> unit-tests -> spotbugs -> compile -> package
```

Exit code: `1`

## Getting Started

- **Non-developers** (no Java required): follow the **[Non-Developer Guide](dev-docs/non-developer-guide.md)** — install Docker, download the release, and run.
- **Developers** (building from source): follow the **[Developer Guide](dev-docs/developer-guide.md)** — prerequisites, build commands, service startup, and testing.

### Deployment Options

**Docker Compose (local/development)**:
```bash
docker-compose up -d
```
Starts PostgreSQL, RabbitMQ, API Gateway, Execution Service, and Report Service.

**Kubernetes**:
```bash
kubectl apply -f k8s/
```
Applies all manifests (PostgreSQL, RabbitMQ, API Gateway, Execution Service, Report Service) as Deployments/StatefulSets with PersistentVolumes.

**Helm**:
```bash
helm install cicd-system helm/cicd-system/
```
Deploys the full system via the Helm chart with configurable `values.yaml`.

### Project Requirements

- CLI must run from Git repository root (directory containing `.git/`)
- Pipeline configs must be in `.pipelines/` directory at repo root
- All paths must be relative to repository root
- For `run` command: current branch/commit must match execution parameters
- Docker daemon must be running for pipeline execution

## Development

See the **[Developer Guide](dev-docs/developer-guide.md)** for build commands, running tests, coverage reports, BATS integration tests, and code quality checks.

### Adding New Features

1. **Add Validator:**
   - Create validator in `pipeline-lib/internal/validators/`
   - Implement error collection pattern
   - Integrate with `PipelineValidationService`
   - Write comprehensive unit tests
   - Update BATS tests if CLI behavior changes

2. **Add CLI Command:**
   - Create command in `cli/commands/`
   - Extend `Callable<Integer>`
   - Add Picocli annotations
   - Register in main CLI class
   - Add BATS integration tests

3. **Add Service:**
   - Create service in appropriate package
   - Use Spring Boot dependency injection
   - Follow validation patterns
   - Add integration tests

4. **Add Microservice:**
   - Create Spring Boot application
   - Define REST API with controllers
   - Implement service layer logic
   - Configure in application.properties
   - Add comprehensive tests
   - Update docker-compose if needed

## Technology Stack

### Core Dependencies

- **SnakeYAML**: YAML parsing with position tracking
- **Picocli**: Command-line interface framework
- **Lombok**: Reduce boilerplate code
- **SLF4J + Logback**: Logging infrastructure

### Microservices Stack

- **Spring Boot**: Application framework for services
- **Spring Cloud Gateway**: Reactive API gateway and request routing (API Gateway)
- **Spring WebFlux**: Reactive web framework (Execution Service, Report Service)
- **Spring Data R2DBC**: Reactive repository layer for PostgreSQL persistence
- **Jackson**: JSON serialization for HTTP communication
- **RabbitMQ (AMQP)**: Async message queue for decoupled pipeline execution
- **Resilience4j**: Circuit breaker and retry support (API Gateway)
- **Flyway**: Database schema migrations
- **Docker Java**: Docker API client library
- **JGit**: Git repository operations
- **PostgreSQL**: Execution history persistence
- **Docker**: Container runtime for isolated job execution
- **Kubernetes**: Container orchestration platform for service deployment
- **Helm**: Kubernetes package manager for templated service deployment
- **OpenTelemetry (OTel Java Agent + Logback Appender)**: Distributed tracing, metrics, and structured log export across all services
- **OpenTelemetry Collector**: OTLP signal ingestion and routing to backends
- **Prometheus**: Metrics storage and query
- **Grafana Loki**: Log aggregation (service logs + container stdout/stderr)
- **Grafana Tempo**: Distributed trace backend
- **Grafana**: Unified observability dashboards (Pipeline Overview, Stage/Job Breakdown, Logs Viewer, Trace Explorer)

### Testing

- **JUnit 5**: Testing framework
- **AssertJ**: Fluent assertions
- **Mockito**: Mocking framework
- **BATS**: Bash Automated Testing System for CLI and observability integration tests
- **Spring Test**: Spring Boot test support

## Configuration Examples

### Simple Build Pipeline
```yaml
pipeline:
  name: simple-build
  description: Basic build and test

jobs:
  build:
    - stage: build
    - image: gradle:jdk21
    - script: gradle build

  test:
    - stage: test
    - image: gradle:jdk21
    - script: gradle test
```

### Complex Multi-Stage Pipeline
```yaml
pipeline:
  name: full-pipeline
  description: Complete CI/CD workflow

stages:
  - lint
  - build
  - test
  - security
  - deploy

jobs:
  code-style:
    - stage: lint
    - image: gradle:jdk21
    - script: gradle checkstyleMain

  compile:
    - stage: build
    - image: gradle:jdk21
    - script: gradle compileJava

  unit-tests:
    - stage: test
    - image: gradle:jdk21
    - needs: [compile]
    - script: gradle test

  integration-tests:
    - stage: test
    - image: gradle:jdk21
    - needs: [unit-tests]
    - script: gradle integrationTest

  security-scan:
    - stage: security
    - image: aquasec/trivy:latest
    - script: trivy image gradle:jdk21
    - failures: true

  deploy-staging:
    - stage: deploy
    - image: alpine:latest
    - script: ./deploy.sh staging
```

## Troubleshooting

### Common Issues

**Error: "must be run from the root of a Git repository"**
- Ensure you're in a directory containing `.git/`
- Run `git init` if starting a new repository

**Error: "path must be under .pipelines/ directory"**
- All pipeline configs must be in `.pipelines/` at repo root
- Create directory: `mkdir -p .pipelines`

**Error: "Cycle detected in 'needs' requirements"**
- Check job dependencies within each stage
- Dependencies can only reference jobs in the same stage
- Remove circular references

**Error: "Branch mismatch: requested 'X' but current branch is 'Y'"**
- Checkout the correct branch: `git checkout X`
- Or run without `--branch` to use current branch

**Error: "Connection refused: API Gateway not reachable"**
- Ensure API Gateway is running: `./gradlew :api-gateway:bootRun`
- Check API Gateway URL (default: http://localhost:8080)
- Verify no firewall blocking port 8080

**Error: "Execution Service unavailable"**
- Start Execution Service: `./gradlew :execution-service:bootRun`
- Verify Docker daemon is running: `docker ps`
- Check service logs for error details

**Error: "Report Service unavailable"**
- Start Report Service: `./gradlew :report-service:bootRun`
- Verify PostgreSQL is running and accessible
- Check service logs for error details

**Error: "Docker daemon not running"**
- Start Docker Desktop (macOS/Windows) or daemon (Linux)
- Verify with `docker ps` command
- Check Docker socket permissions

**Validation errors with line/column numbers:**
- Error messages point to exact location in YAML
- Format: `file:line:column: ERROR, message`
- Many IDEs support clicking these to jump to location

**Grafana shows no data after a pipeline run:**
- Wait ~30 seconds for the OTel Collector to batch-flush telemetry and Prometheus to complete a scrape cycle
- Verify the collector is running: `docker ps | grep otel-collector`
- Check collector logs: `docker logs cicd-otel-collector`

**`trace-id` missing from `cicd report` output:**
- Trace ID is populated only after the execution-service processes the run; allow the run to reach a terminal state first
- Ensure all services have `OTEL_EXPORTER_OTLP_ENDPOINT` configured (set automatically by `docker-compose up -d`)

## Contributing

### Development Workflow

1. Create feature branch from `main`
2. Implement feature with tests
3. Ensure all tests pass: `./gradlew test`
4. Run code quality checks: `./gradlew check`
5. Update documentation as needed
6. Submit pull request

### Code Style

- Follow existing code patterns
- Write comprehensive Javadoc
- Use Lombok to reduce boilerplate
- Maintain test coverage above 70%
- Follow checkstyle rules

### Testing Requirements

- Unit tests for all new validators and services
- Integration tests for command flows
- Test fixtures for validation scenarios
- Minimum 70% code coverage
- BATS tests for CLI behavior

## Resources & References

- [SnakeYAML Documentation](https://bitbucket.org/snakeyaml/snakeyaml/wiki/Home)
- [Picocli Documentation](https://picocli.info/)
- [YAML 1.2 Specification](https://yaml.org/spec/1.2.2/)
- [Docker Documentation](https://docs.docker.com/)
- [JGit Documentation](https://www.eclipse.org/jgit/)
- [BATS Documentation](https://bats-core.readthedocs.io/)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Docker Java Library](https://github.com/docker-java/docker-java)

## License

This project was created as coursework for CS7580 DevOps at Northeastern University.

**Academic Use Only** - Not licensed for redistribution or commercial use.

For questions about usage, please contact the authors.

## Acknowledgments

Built as part of CS7580 DevOps course at Northeastern University.

---
