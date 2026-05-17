# User Guide — Developer Installation and Usage

This guide is for evaluators and developers who want to build and run the CI/CD system from source.

---

## Prerequisites

Install the following tools before proceeding.


| Tool           | Minimum Version | Notes                                                                      |
| -------------- | --------------- | -------------------------------------------------------------------------- |
| **Java**       | 21              | JDK required (not just JRE)                                                |
| **Git**        | Any recent      | Required to clone and for CLI operation                                    |
| **Docker**     | 20+             | Daemon must be running; Docker Compose v2 included                         |
| **Gradle**     | 8.x             | The Gradle wrapper (`./gradlew`) is included — no separate install needed |
| **PostgreSQL** | 15+             | Run via Docker (recommended) or native install                             |
| **kubectl**    | Latest          | Required for Kubernetes deployment (optional)                              |
| **minikube**   | Latest          | Local Kubernetes cluster (optional)                                        |
| **Helm**       | 3.12+           | Required for Helm-based K8s deployment (optional)                          |

Verify your environment:

```bash
java --version        # should print 21 or higher
git --version
docker --version
docker compose version
./gradlew --version   # uses the wrapper in the repo
kubectl version --client   # if using Kubernetes
minikube version           # if using Kubernetes
helm version               # if using Helm
```

---

## Clone the Repository

```bash
git clone https://github.com/CS7580-SEA-SP26/c-team.git
cd c-team
```

---

## Build

### Build All Modules

Compiles all subprojects (cli, api-gateway, execution-service, report-service) and runs unit tests:

```bash
./gradlew build
```

To compile without running tests:

```bash
./gradlew classes testClasses
```

### Build the CLI Fat JAR

Produces a single self-contained JAR at `cli/build/install/cicd/cicd.jar` and a launcher script at `cli/build/install/cicd/cicd`:

```bash
./gradlew :cli:installCli
```

Add the CLI to your `PATH` for this session:

```bash
export PATH="$PWD/cli/build/install/cicd:$PATH"
cicd --version
cicd --help
```

To build only the fat JAR (without the launcher script):

```bash
./gradlew :cli:shadowJar
# Output: cli/build/libs/cicd.jar
java -jar cli/build/libs/cicd.jar --help
```

### Download Pre-built CLI (Alternative)

To use a released CLI binary instead of building from source, download it from the latest GitHub release.

```bash
curl -L \
  "https://github.com/CS7580-SEA-SP26/c-team/releases/latest/download/cicd-linux-x64.tar.gz" \
  -o cicd-linux-x64.tar.gz
```

### Build Individual Service JARs

Each Spring Boot service has a bootJar task that produces a runnable JAR:

```bash
# API Gateway
./gradlew :api-gateway:bootJar
# Output: api-gateway/build/libs/api-gateway-*.jar

# Execution Service
./gradlew :execution-service:bootJar
# Output: execution-service/build/libs/execution-service-*.jar

# Report Service
./gradlew :report-service:bootJar
# Output: report-service/build/libs/report-service-*.jar
```

---

## Running Services

There are three ways to run the system. Choose one based on your goal:

| Option | Best for |
|--------|----------|
| [Option 1: Local (from source)](#option-1-local-from-source) | Active development, debugging |
| [Option 2: Docker Compose](#option-2-docker-compose) | Quick full-stack testing with pre-built images |
| [Option 3: Kubernetes + Helm](#option-3-kubernetes--helm) | Production-like deployment |

---

### Option 1: Local (from source)

Run each service directly via Gradle. Requires PostgreSQL and RabbitMQ to be running first.

#### Step 1 — Start Infrastructure

See the **[Docker Compose Guide — Start Infrastructure Only](docker-guide.md#start-infrastructure-only-developer-use)** for instructions on starting PostgreSQL and RabbitMQ.

#### Step 2 — Environment Variables

Each service reads configuration from environment variables with sensible defaults. Override these when connecting to non-default hosts or credentials.

**Execution Service** (`execution-service`, port 8081):

| Variable        | Default     | Description        |
| --------------- | ----------- | ------------------ |
| `DB_HOST`       | `localhost` | PostgreSQL host    |
| `DB_PORT`       | `5432`      | PostgreSQL port    |
| `DB_NAME`       | `cicd`      | Database name      |
| `DB_USER`       | `postgres`  | Database user      |
| `DB_PASSWORD`   | `cicd`      | Database password  |
| `RABBITMQ_HOST` | `localhost` | RabbitMQ host      |
| `RABBITMQ_PORT` | `5672`      | RabbitMQ AMQP port |

**Report Service** (`report-service`, port 8082):

| Variable      | Default     | Description       |
| ------------- | ----------- | ----------------- |
| `DB_HOST`     | `localhost` | PostgreSQL host   |
| `DB_PORT`     | `5432`      | PostgreSQL port   |
| `DB_NAME`     | `cicd`      | Database name     |
| `DB_USER`     | `postgres`  | Database user     |
| `DB_PASSWORD` | `cicd`      | Database password |

**API Gateway** (`api-gateway`, port 8080):

| Variable                | Default                 | Description                |
| ----------------------- | ----------------------- | -------------------------- |
| `EXECUTION_SERVICE_URL` | `http://localhost:8081` | Execution Service base URL |
| `REPORT_SERVICE_URL`    | `http://localhost:8082` | Report Service base URL    |

**CLI**:

| Variable                | Default                 | Description          |
| ----------------------- | ----------------------- | -------------------- |
| `CICD_GATEWAY_BASE_URL` | `http://localhost:8080` | API Gateway base URL |

#### Step 3 — Start Services

Services must start in this order because each depends on the one before it:

```
PostgreSQL → Report Service → Execution Service → API Gateway
```

Open four terminals and run one command in each.

**Terminal 1 — Start Report Service** (requires PostgreSQL):

```bash
./gradlew :report-service:bootRun
# Runs on http://localhost:8082
# Wait until you see: "Started ReportServiceApplication"
```

**Terminal 2 — Start Execution Service** (requires PostgreSQL + RabbitMQ):

```bash
./gradlew :execution-service:bootRun
# Runs on http://localhost:8081
# Wait until you see: "Started ExecutionServiceApplication"
```

**Terminal 3 — Start API Gateway** (requires Execution Service and Report Service):

```bash
./gradlew :api-gateway:bootRun
# Runs on http://localhost:8080
# Wait until you see: "Started ApiGatewayApplication"
```

**Terminal 4 — Use the CLI**:

```bash
./gradlew :cli:installCli
export PATH="$PWD/cli/build/install/cicd:$PATH"
cicd --help
```

---

### Option 2: Docker Compose

See the **[Docker Compose Guide](docker-guide.md)** for full instructions on running all services via Docker Compose, including image download, startup, verification, and stopping.

---

### Option 3: Kubernetes + Helm

See the **[Kubernetes & Helm Guide](k8s-helm-guide.md)** for full instructions covering Helm deployment, raw manifest deployment, CLI connection, and Helm values reference.

---

## Running Tests

### Unit Tests

Run all unit tests across all modules:

```bash
./gradlew test
```

Run tests for a specific module:

```bash
./gradlew :cli:test
./gradlew :pipeline-lib:test
./gradlew :api-gateway:test
./gradlew :execution-service:test
./gradlew :report-service:test
```

Run a specific test class:

```bash
./gradlew :cli:test --tests VerifyCommandTest
```

### JaCoCo Coverage Report

Generate the aggregate HTML coverage report for all modules:

```bash
./gradlew jacocoTestReport
# Report written to: build/reports/jacoco/aggregate/html/index.html
open build/reports/jacoco/aggregate/html/index.html
```

Generate the coverage report for a single module:

```bash
./gradlew :cli:jacocoTestReport
open cli/build/reports/jacoco/test/html/index.html
```

Verify coverage meets the 70% threshold (fails the build if below):

```bash
./gradlew jacocoTestCoverageVerification
```

### BATS Integration Tests

BATS (Bash Automated Testing System) tests run the compiled CLI binary against real pipeline files. Install BATS first:

```bash
# macOS
brew install bats-core

# Ubuntu/Debian
sudo apt install bats
```

Build and install the CLI, then run the integration tests:

```bash
./gradlew :cli:installCli
export PATH="$PWD/cli/build/install/cicd:$PATH"

bats tests/integration/verify.bats
bats tests/integration/dryrun.bats
bats tests/integration/run.bats
bats tests/integration/report.bats
```

> **Note:** The `run.bats` and `report.bats` tests require all services to be running. Start them as described in [Startup Order](#startup-order) before running those suites.

### Run All Quality Checks

Runs Checkstyle, SpotBugs, and JaCoCo coverage verification across all modules:

```bash
./gradlew verify
```

---

## Verify Installation

Run these commands after starting all services to confirm everything is working correctly.

### Services Health


| Command                                              | Expected Output                           |
| ---------------------------------------------------- | ----------------------------------------- |
| `docker compose ps`                                  | All containers show`running` or `healthy` |
| `curl http://localhost:8080/api/v1/pipelines/health` | `API Gateway is healthy`                  |
| `curl http://localhost:8081/actuator/health`         | `{"status":"UP",...}`                     |
| `curl http://localhost:8082/actuator/health`         | `{"status":"UP",...}`                     |

### CLI Health


| Command          | Expected Output                                                   |
| ---------------- | ----------------------------------------------------------------- |
| `cicd --version` | Version string, e.g.`cicd 0.0.1-SNAPSHOT`                         |
| `cicd --help`    | Usage text listing`verify`, `dryrun`, `run`, `report` subcommands |

### Full Verification Script

Copy and paste this block to run all checks at once:

```bash
echo "=== Docker services ==="
docker compose ps

echo ""
echo "=== API Gateway (port 8080) ==="
curl -s http://localhost:8080/api/v1/pipelines/health
echo ""

echo ""
echo "=== Execution Service (port 8081) ==="
curl -s http://localhost:8081/actuator/health
echo ""

echo ""
echo "=== Report Service (port 8082) ==="
curl -s http://localhost:8082/actuator/health
echo ""

echo ""
echo "=== CLI ==="
cicd --version
cicd --help
```

### End-to-End Smoke Test

Once all services are verified healthy, clone the repo and run a sample pipeline:

```bash
git clone https://github.com/CS7580-SEA-SP26/c-team.git demo-repo
cd demo-repo

# Validate a pipeline config
cicd verify .pipelines/valid-pipeline.yaml
# Expected: OK: .pipelines/valid-pipeline.yaml

# Preview execution order
cicd dryrun .pipelines/valid-pipeline.yaml
# Expected: YAML-formatted execution plan

# Run a pipeline
cicd run --name good-pipeline
# Expected: Pipeline completed successfully. Run #1

# Use local repository path instead of remote URL (optional)
cicd run --name good-pipeline --local

# View the report
cicd report --pipeline good-pipeline
# Expected: YAML-formatted run history
```

---

## Troubleshooting

### Port Already in Use

This is the most common startup failure. Each service has a fixed default port:


| Service           | Default Port |
| ----------------- | ------------ |
| PostgreSQL        | 5432         |
| Execution Service | 8081         |
| Report Service    | 8082         |
| API Gateway       | 8080         |

Find and stop the conflicting process:

```bash
# Find process using a port (e.g. 8080)
lsof -i :8080          # macOS / Linux
# or
ss -tlnp | grep 8080   # Linux only

# Kill the process
kill <PID>
```

To start a service on a different port:

```bash
./gradlew :api-gateway:bootRun --args='--server.port=9090'
./gradlew :execution-service:bootRun --args='--server.port=9091'
./gradlew :report-service:bootRun --args='--server.port=9092'
```

Then update the CLI and API Gateway to point to the new addresses:

```bash
export EXECUTION_SERVICE_URL=http://localhost:9091
export REPORT_SERVICE_URL=http://localhost:9092
export CICD_GATEWAY_BASE_URL=http://localhost:9090
```

### Service Fails to Connect to PostgreSQL / Docker Daemon Not Running

See the **[Docker Compose Guide — Troubleshooting](docker-guide.md#troubleshooting)** for steps to diagnose PostgreSQL connection failures and Docker daemon issues.

### Git Repository Error When Running CLI

All CLI commands must be run from within a Git repository root (a directory containing `.git/`). If you see the error `cicd must be run from within a Git repository`:

```bash
git init           # if starting a new project
# or
cd /path/to/repo   # navigate to the repo root
```

### Build Fails — Java Version

Confirm the active JDK is version 21 or higher:

```bash
java --version
javac --version
```

If multiple JDKs are installed, set `JAVA_HOME` to a 21+ installation:

```bash
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
```
