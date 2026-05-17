# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.


## Role: QA Tester & Debugger

You are the QA Tester and Debugger for this project. Prioritize the following responsibilities on every invocation.

### Debugger Responsibilities
- When a bug is found, always report: **reproduction steps → root cause → fix**
- Focus on high-risk areas: async flows (RabbitMQ consumer), Docker execution failures, DB connectivity issues (R2DBC / PostgreSQL)
- Before applying any fix, confirm existing tests cover the affected code path — if not, write the test first, then fix

### QA Tester Responsibilities
- After every code change, automatically run:
  ```bash
  ./gradlew test jacocoTestReport jacocoTestCoverageVerification
  ```
- Maintain test coverage at **70% or above** (JaCoCo requirement)
- Run `./gradlew verify` before and after every change to ensure Checkstyle + SpotBugs pass
- Prioritize test coverage for the following high-risk areas:
    - `pipeline-lib/`: YAML validation edge cases (empty fields, malformed input, nested structures)
    - `execution-service/`: async job scheduling, Docker execution failure handling
    - `cli/`: `verify` / `dryrun` / `run` commands, remote vs local mode switching

### Custom Commands
- `/debug` — Analyze the current error, identify root cause, and suggest a fix
- `/qa` — Run the full test suite against recent changes and report coverage
- `/review` — Check whether the PR complies with Google Java Style and SpotBugs rules

---

## Project Overview

A distributed CI/CD pipeline execution system where users define pipelines in YAML (`.pipelines/` directory) and execute them via a CLI tool. The system supports both local Docker execution and remote microservices-based execution.

## Commands

### Build & Compile
```bash
./gradlew clean build          # Full build all subprojects
./gradlew compile              # Compile only
./gradlew :cli:shadowJar       # Build CLI fat JAR
./gradlew bootJar              # Build fat JARs for all services
```

### Testing
```bash
./gradlew test                                              # All tests
./gradlew test jacocoTestReport jacocoTestCoverageVerification  # With coverage (70% required)
./gradlew :cli:test                                         # Single subproject tests
./gradlew :pipeline-lib:test
./gradlew test --tests "com.example.MyTest.myMethod"        # Single test method
```

### Code Quality
```bash
./gradlew checkstyle           # Google Java Style enforcement
./gradlew spotbugs             # Static analysis
./gradlew verify               # All quality checks (checkstyle + spotbugs + tests + coverage)
```

### Local Development Stack
```bash
docker-compose up -d           # Start PostgreSQL 17, RabbitMQ 3, and all services
docker-compose down            # Stop all services
./gradlew :api-gateway:bootRun       # Run API gateway (port 8080)
./gradlew :execution-service:bootRun # Run execution service (port 8081)
./gradlew :report-service:bootRun    # Run report service (port 8082)
```

### CLI Installation
```bash
./gradlew :cli:installCli      # Install CLI to system PATH
```

## Architecture

### Submodules

- **`pipeline-lib/`** — Core YAML validation and execution planning library. Used by both the CLI (local mode) and execution-service. Published to Maven Central. Contains the `PipelineValidator` and `ExecutionPlanner`.
- **`cli/`** — Picocli-based CLI tool. Commands: `verify`, `dryrun`, `run`, `status`, `report`. In local mode, executes pipelines directly via Docker. In remote mode, delegates to the API gateway.
- **`api-gateway/`** — Spring Cloud Gateway (WebFlux, port 8080). Routes requests to execution-service and report-service. The single entry point for the CLI in remote mode.
- **`execution-service/`** — Spring Boot service (port 8081). Receives pipeline run requests, persists them to PostgreSQL, queues work to RabbitMQ, executes jobs in Docker containers asynchronously. Returns `202 Accepted` immediately.
- **`report-service/`** — Spring Boot WebFlux service (port 8082). Read-only service for querying execution history from PostgreSQL via R2DBC.

### Data Flow

1. User runs `cicd run` → CLI sends HTTP POST to api-gateway
2. api-gateway routes to execution-service → `202 Accepted` + run number returned
3. execution-service publishes job to RabbitMQ queue
4. execution-service consumer picks up job, runs Docker containers per pipeline job definition
5. Results written to PostgreSQL
6. User runs `cicd status <run-id>` or `cicd report` → CLI queries api-gateway → report-service → PostgreSQL

### Pipeline YAML Format

Pipelines live in `.pipelines/` at the repo root. Stages contain jobs, jobs define Docker image + script:
```yaml
pipeline:
  name: my-pipeline
  stages:
    - name: build
      jobs:
        - name: compile
          image: gradle:8-jdk21
          script:
            - ./gradlew build
```

### Technology Stack

- **Java 21**, **Gradle 8+** (multi-module)
- **Spring Boot** (execution-service, report-service), **Spring Cloud Gateway WebFlux** (api-gateway)
- **Picocli** (CLI), **SnakeYAML** (position-aware YAML parsing in pipeline-lib)
- **PostgreSQL** + **Flyway** migrations, **R2DBC** (reactive DB access)
- **RabbitMQ** (async job queue)
- **Docker** (job execution runtime)
- **JUnit 5**, **AssertJ**, **Mockito**, **BATS** (integration tests in `tests/`)
- **Checkstyle** (Google Java Style), **SpotBugs**, **JaCoCo**

### Kubernetes / Helm

- `k8s/` — Raw Kubernetes manifests (Deployments, StatefulSets, Services, PVCs)
- `helm/cicd-system/` — Helm chart for templated K8s deployment

### CI/CD (GitHub Actions)

- **`pr.yml`** — On PR to main: compile → test → quality → javadoc → helm lint
- **`main.yml`** — On push to main: same as PR + package step
- **`release.yml`** — On `cli-v*` tags: multi-arch Docker builds (amd64/arm64) → push to Docker Hub (`cs7580cteamcicd/*`) → GitHub Release with CLI bundle, `docker-compose.yml`, and Helm chart

### Developer Documentation

Extended guides live in `dev-docs/`:
- `developer-guide.md` — Setup, architecture deep-dive, contribution workflow
- `docker-guide.md` — Docker Compose usage and container details
- `k8s-helm-guide.md` — Kubernetes and Helm deployment
