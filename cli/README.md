# CLI Module

The CLI (Command-Line Interface) is the primary user interface for interacting with the CI/CD pipeline system. It provides commands for validating pipeline configurations, previewing execution plans, triggering remote pipeline executions, and retrieving execution reports through the API Gateway.

## Overview

The CLI operates in two modes:

**Local Operations** - Commands that work directly with the pipeline library without network communication:
- `verify` - Validates pipeline YAML configurations
- `dryrun` - Generates execution plan previews

**Remote Operations** - Commands that communicate with the API Gateway via HTTP:
- `run` - Submits pipeline for execution (returns immediately — non-blocking)
- `status` - Polls live or most recent execution status
- `report` - Retrieves execution reports and history

### Purpose

- **Developer-Friendly Interface**: Intuitive command structure with comprehensive help
- **Fast Feedback**: Local validation catches errors before remote execution
- **Git Integration**: Enforces branch/commit matching to prevent execution confusion
- **Repository-Aware**: All operations are scoped to Git repository boundaries
- **Standardized Exit Codes**: Machine-readable success/failure indicators

## Architecture

### Component Structure

```
src/main/java/edu/northeastern/cs7580/cicd/cli/
├── CicdCli.java                        # Main entry point
├── commands/
│   ├── VerifyCommand.java              # Pipeline validation
│   ├── DryrunCommand.java              # Execution preview
│   ├── RunCommand.java                 # Pipeline execution (async submit)
│   ├── StatusCommand.java              # Execution status polling
│   └── ReportCommand.java              # Execution reports
├── cores/
│   ├── RepoRootDetector.java           # Git repository detection
│   ├── PathPolicy.java                 # Path validation rules
│   ├── DefaultPathResolver.java        # Default path resolution
│   ├── GitStateValidator.java          # Git state validation interface
│   ├── DefaultGitStateValidator.java   # Git branch/commit validation
│   ├── GitStateException.java          # Git state validation errors
│   ├── OptionValidator.java            # CLI option validation
│   └── ExitCodes.java                  # Standard exit codes
├── formatters/
│   ├── DryrunFormatter.java            # Execution plan formatter interface
│   └── DefaultDryrunFormatter.java     # YAML-style formatter
├── clients/
│   ├── PipelineExecutionClient.java    # Execution client interface
│   ├── ApiGatewayClient.java           # HTTP client for execution
│   ├── PipelineReportClient.java       # Report client interface
│   ├── ApiGatewayReportClient.java     # HTTP client for reports
│   └── dtos/
│       ├── RunRequestDto.java          # Execution request model
│       └── RunResponseDto.java         # Execution response model
├── exceptions/
│   └── ReportNotFoundException.java    # Unchecked exception for HTTP 404
└── configs/
    └── CliConfig.java                  # Configuration management
```

### Command Flow

**Local Commands** (`verify`, `dryrun`):
1. Detect Git repository root
2. Validate and resolve input path
3. Delegate to pipeline library for validation/planning
4. Format and display results

**Remote Commands** (`run`):
1. Detect Git repository root
2. Validate Git state (branch/commit matching)
3. Read pipeline configuration
4. Send HTTP POST to API Gateway — returns immediately with run number (202 Accepted)
5. Display execution ID and run number; use `status` to track progress

**Remote Commands** (`status`):
1. Build status URL from provided options
2. Send HTTP GET to API Gateway status endpoint
3. Print YAML-formatted live or most recent execution state

**Remote Commands** (`report`):
1. Build API Gateway URL from provided options
2. Send HTTP GET request to API Gateway
3. Print YAML-formatted response to stdout

## Commands

### verify

Validates pipeline YAML configuration files.

**Usage**:
```bash
# Validate default pipeline
cicd verify

# Validate specific file
cicd verify .pipelines/release.yaml

# Validate all pipelines in directory
cicd verify .pipelines/
```

**Validation Rules**:
- Must run from Git repository root
- Configuration must be under `.pipelines/` directory
- Validates YAML structure, stages, jobs, and dependencies
- Detects circular dependencies with complete cycle reporting
- Reports all errors at once with line/column precision

**Exit Codes**:
- `0` - Configuration is valid
- `1` - Configuration validation error
- `3` - Git repository error
- `10` - Invalid CLI arguments

### dryrun

Generates and displays execution plan preview without running the pipeline.

**Usage**:
```bash
# Preview execution order
cicd dryrun .pipelines/default.yaml
```

**Output Format**:
- YAML-style display of stages and jobs
- Jobs ordered by dependencies within each stage
- Shows image, script commands, needs, and `failures` for each job
- `failures:` is always emitted for every job, defaulting to `false` when not set in the YAML
- Output is for display only (not a valid pipeline config)

**Example output**:
```
OK: dryrun validation succeeded
build:
    compile:
        image: gradle:jdk21
        script:
            - gradle compileJava
        failures: false
    security-scan:
        image: aquasec/trivy:latest
        script:
            - trivy image gradle:jdk21
        failures: true
test:
    unit-tests:
        image: gradle:jdk21
        script:
            - gradle test
        needs:
            - compile
        failures: false
```

**Exit Codes**:
- `0` - Validation and preview successful
- `1` - Configuration validation error
- `3` - Git repository error
- `10` - Invalid CLI arguments

### run

Executes a pipeline remotely through the API Gateway.

**Usage**:
```bash
# Execute by pipeline name
cicd run --name default

# Execute by file path
cicd run --file .pipelines/release.yaml

# Execute on specific branch
cicd run --name default --branch feature-x

# Execute specific commit on branch
cicd run --name default --branch main --commit abc123
```

**Options**:
- `--name` - Pipeline name from `.pipelines/` directory (mutually exclusive with `--file`)
- `--file` - Path to pipeline YAML file (mutually exclusive with `--name`)
- `--branch` - Git branch name (default: `main`)
- `--commit` - Git commit hash or `latest` (default: `latest`)
- `--local` - Optional. Use local repository path for execution (default: remote URL)

**Git State Validation**:
- Requested branch must match current branch (detached HEAD rejected)
- Requested commit must match current HEAD (when not `latest`)
- Prevents execution confusion by ensuring CLI context matches request

**Endpoint**: `POST /api/v1/pipelines/execute`

**Exit Codes**:
- `0` - Pipeline execution started successfully
- `2` - Runtime execution error
- `3` - Git repository error (branch/commit mismatch)
- `10` - Invalid CLI arguments

### status

Queries the live or most recent execution status for a pipeline run. Because `run` returns immediately (non-blocking), use `status` to track progress.

**Usage**:
```bash
# Active/most recent run for a repository
cicd status --repo https://github.com/org/repo.git

# Specific run by pipeline name and run number
cicd status --pipeline default --run 1
```

**Options**:
- `--repo` - Git repository URL (mutually exclusive with `--pipeline`)
- `--pipeline` - Pipeline name (mutually exclusive with `--repo`)
- `--run` - Run number; requires `--pipeline`

**Exit Codes**:
- `0` - Status retrieved successfully
- `1` - Pipeline or run not found (HTTP 404)
- `2` - API Gateway unreachable
- `10` - Invalid CLI arguments

### report

Retrieves execution reports and history from the API Gateway.

**Usage**:
```bash
# All runs for a pipeline
cicd report --pipeline default

# Specific run with stage summaries
cicd report --pipeline default --run 1

# Specific stage with job summaries
cicd report --pipeline default --run 1 --stage build

# Specific job
cicd report --pipeline default --run 1 --stage build --job compile
```

**Options**:
- `--pipeline` - Pipeline name matching `pipeline.name` in the YAML config file (required)
- `--run` - Run number to report on. Requires `--pipeline`
- `--stage` - Stage name to report on. Requires `--run`
- `--job` - Job name to report on. Requires `--stage`

**Output**: YAML-formatted report printed to stdout. The level of detail depends on which options are provided — from a summary of all runs down to a single job. Run-level output includes a `trace-id` field that can be used to locate the end-to-end trace in Grafana Tempo.

**API Gateway Endpoints**:

| CLI Options | Endpoint |
|-------------|----------|
| `--pipeline` | `GET /api/v1/report/pipelines/{name}` |
| `--pipeline --run` | `GET /api/v1/report/pipelines/{name}/runs/{n}` |
| `--pipeline --run --stage` | `GET /api/v1/report/pipelines/{name}/runs/{n}/stages/{s}` |
| `--pipeline --run --stage --job` | `GET /api/v1/report/pipelines/{name}/runs/{n}/stages/{s}/jobs/{j}` |

**Exit Codes**:
- `0` - Report retrieved and printed successfully
- `1` - Pipeline, run, stage, or job not found (HTTP 404)
- `2` - API Gateway unreachable
- `10` - Invalid CLI arguments (e.g. `--stage` without `--run`)

## Configuration

### API Gateway URL

The CLI communicates with the API Gateway for remote operations. The base URL can be configured via:

**Resolution Order**:
1. System property: `-Dcicd.gateway.baseUrl=http://gateway:8080`
2. Environment variable: `CICD_GATEWAY_BASE_URL=http://gateway:8080`

### Repository URL Selection

By default, `run` uses the Git remote URL from `origin` to execute remotely. SSH remotes like
`git@github.com:org/repo.git` are normalized to HTTPS for public repositories. If no `origin`
remote is configured, it falls back to the local repository path. Use the optional `--local` flag
to always send the local path instead of the remote URL.
3. Default: `http://localhost:8080`

**Examples**:

```bash
# Using environment variable
export CICD_GATEWAY_BASE_URL=http://api-gateway:8080
cicd run --name default

# Using system property
java -Dcicd.gateway.baseUrl=http://api-gateway:8080 -jar cli.jar run --name default

# Kubernetes deployment (use the LoadBalancer external IP or ingress host)
export CICD_GATEWAY_BASE_URL=http://<api-gateway-external-ip>:8080
cicd run --name default
```

### Default Paths

**Pipeline Configuration Default**: `.pipelines/pipeline.yaml`

When no path is provided to `verify`, this default path is used relative to the repository root.

## Exit Codes

The CLI uses standardized exit codes for consistent error handling:

### Success
- **0 (OK)** - Command completed successfully

### Operational Errors (1-5)
- **1 (CONFIG_VALIDATION_ERROR)** - Invalid pipeline configuration, YAML errors, circular dependencies, resource not found (404)
- **2 (RUNTIME_EXECUTION_ERROR)** - Pipeline execution failed, network errors, API Gateway unreachable
- **3 (GIT_REPOSITORY_ERROR)** - Not in Git repository, branch/commit mismatch
- **4 (DOCKER_ERROR)** - Container operations failed (future)
- **5 (DATABASE_ERROR)** - Report storage/retrieval failed (future)

### User Input Errors (10+)
- **10 (INVALID_CLI_ARGUMENTS)** - Missing required options, invalid paths, malformed arguments

**Usage in Scripts**:
```bash
#!/bin/bash
cicd verify .pipelines/default.yaml
if [ $? -eq 0 ]; then
  echo "Validation passed"
  cicd run --name default
else
  echo "Validation failed"
  exit 1
fi
```

## Path Policy

The CLI enforces strict path validation rules:

### Rules
1. **Relative Paths Only** - Absolute paths are rejected
2. **No Path Traversal** - Paths with `..` that escape repository root are rejected
3. **Under .pipelines/** - All configurations must be in `.pipelines/` directory
4. **Must Exist** - Path must reference an existing file or directory

### Examples

**Valid Paths**:
```bash
cicd verify .pipelines/default.yaml
cicd verify .pipelines/prod/release.yaml
cicd verify .pipelines/
```

**Invalid Paths**:
```bash
cicd verify /home/user/pipeline.yaml           # Absolute path
cicd verify .pipelines/../../config.yaml       # Path traversal
cicd verify config/pipeline.yaml               # Not under .pipelines/
cicd verify .pipelines/nonexistent.yaml        # File not found
```

## Git Repository Requirements

All CLI commands must be executed from within a Git repository:

### Repository Detection
- CLI searches upward from current directory for `.git/` folder
- Operations are scoped to the repository containing `.git/`
- All paths are resolved relative to repository root

### Git State Validation (for `run` command)

The `run` command validates that the current Git state matches execution parameters:

**Branch Validation**:
- If `--branch` is specified, current branch must match
- Detached HEAD is rejected when branch is required
- Provides clear error messages for mismatches

**Commit Validation**:
- If `--commit` is specified (and not `latest`), HEAD must match
- Accepts full SHA-1 hash or prefix
- Keyword `latest` skips commit validation

**Error Examples**:
```
Branch mismatch: requested 'feature-x' but current branch is 'main'.
Commit mismatch: requested 'abc123' but current HEAD is 'def456'.
Detached HEAD at abc123; expected branch 'main'.
```

## Running the CLI

### Installation (Pre-built Release — No Java Required)

Download and extract the pre-built release archive. No Java installation needed.

```bash
curl -L "https://github.com/CS7580-SEA-SP26/c-team/releases/download/cli-v0.1.0-beta/cicd-linux-x64.tar.gz" | tar xz
export PATH="$PWD/cicd-linux-x64:$PATH"
cicd --version
cicd --help
```

The archive bundles a trimmed JRE — the `cicd` command works on a fresh Ubuntu 24.04 machine with no prior setup.

### Building from Source

**Prerequisites**:
- Java 21 or higher
- Git repository initialized
- API Gateway running for remote operations (default: `http://localhost:8080`)

```bash
# Build and install locally
./gradlew :cli:installCli

# Add to PATH
export PATH="$PWD/cli/build/install/cicd:$PATH"

# Verify
cicd --version
cicd --help
```

### Execution

```bash
# Show help
cicd --help
cicd verify --help
cicd report --help

# Run a command
cicd verify .pipelines/default.yaml
```

### Common Workflows

**Local Validation Before Commit**:
```bash
# Validate all pipelines
cicd verify .pipelines/

# Preview execution
cicd dryrun .pipelines/default.yaml

# Commit if valid
git add .pipelines/
git commit -m "Add pipeline configuration"
```

**Remote Execution (Default)**:
```bash
# Execute on current branch
cicd run --name default

# Execute on specific branch
git checkout feature-x
cicd run --name default --branch feature-x

# Execute specific commit
git checkout main
cicd run --name default --branch main --commit abc123
```

**Local Execution (optional `--local`)**:
```bash
# Execute using local repository path
cicd run --local --name default
```

**Async Execution (run + status)**:
```bash
# Submit pipeline — returns immediately with run number
cicd run --name default

# Poll status until complete
cicd status --pipeline default --run 1

# Or check by repo URL
cicd status --repo https://github.com/org/repo.git
```

**Viewing Reports**:
```bash
# Check all runs for a pipeline
cicd report --pipeline default

# Investigate a specific failed run
cicd report --pipeline default --run 2

# Drill into a failing stage
cicd report --pipeline default --run 2 --stage build
```

## Testing

### Unit Tests

```bash
# Run all CLI tests
./gradlew :cli:test

# Run specific test class
./gradlew :cli:test --tests VerifyCommandTest

# Generate coverage report
./gradlew :cli:test :cli:jacocoTestReport
```

### Integration Tests

```bash
# Build and install CLI
./gradlew :cli:installCli

# Run BATS integration tests
bats tests/integration/verify.bats
bats tests/integration/dryrun.bats
bats tests/integration/run.bats
bats tests/integration/report.bats
```

### Manual Testing

```bash
# Test verify command
cd /path/to/git/repo
cicd verify .pipelines/default.yaml

# Test with invalid config
cicd verify .pipelines/broken.yaml

# Test dryrun
cicd dryrun .pipelines/default.yaml

# Test run (requires API Gateway and Execution Service)
cicd run --name default

# Test report (requires API Gateway and Report Service)
cicd report --pipeline default
```

## Technology Stack

### Core Dependencies

- **Picocli** 4.7.6: Command-line interface framework
- **JGit**: Git repository operations
- **Jackson**: JSON serialization for API Gateway communication
- **SnakeYAML** 2.3: YAML output formatting for the `report` command
- **Java HTTP Client**: Standard library HTTP client for API Gateway requests

### Testing

- **JUnit 5** 5.10.0: Testing framework
- **AssertJ** 3.24.2: Fluent assertions
- **Mockito** 5.21.0: Mocking framework
- **BATS**: Bash Automated Testing System for integration tests

### Integration

**Pipeline Library**: Local validation and execution planning
- `PipelineService` - Validates configurations and creates execution plans
- `PipelineServiceFactory` - Factory for service instances
- Exception handling for validation errors

## Integration with Services

### API Gateway Communication

The CLI communicates with the API Gateway for remote operations:

**Request Flow (`run`)**:
1. CLI validates local Git state
2. Reads pipeline configuration from `.pipelines/`
3. Constructs JSON request with pipeline config and Git metadata
4. Sends HTTP POST to API Gateway
5. Displays response (execution ID, status, message)

**Request Flow (`report`)**:
1. CLI builds URL from provided options
2. Sends HTTP GET to API Gateway report endpoint
3. Deserializes JSON response into a map
4. Prints YAML-formatted output to stdout

**Run Request Format**:
```json
{
  "pipelineName": "default",
  "pipelineConfig": "pipeline:\n  name: default\n...",
  "gitMetadata": {
    "repository": "git@github.com:user/repo.git",
    "branch": "main",
    "commitHash": "abc123def456..."
  }
}
```

**Run Response Format**:
```json
{
  "executionId": "uuid-string",
  "pipelineName": "default",
  "runNumber": 42,
  "status": "QUEUED",
  "message": "Pipeline queued for execution"
}
```

### Pipeline Library Integration

Local commands (`verify`, `dryrun`) use the pipeline library directly:

**Validation Flow**:
1. CLI resolves and validates file path
2. Delegates to `PipelineService.validatePipeline()`
3. Catches `ValidationException` with detailed error messages
4. Formats errors for CLI output with file:line:column

**Execution Plan Flow**:
1. CLI validates pipeline configuration
2. Calls `PipelineService.createExecutionPlan()`
3. Uses `DryrunFormatter` to convert plan to YAML-style output
4. Displays formatted execution preview

## Error Handling

### Validation Errors

Configuration validation errors include precise locations:

```
.pipelines/bad-config.yaml:2:9: ERROR, Wrong type for 'pipeline.name'. Expected String, got integer
.pipelines/bad-config.yaml:15:5: ERROR, Job 'deploy' references non-existent stage 'production'
.pipelines/bad-config.yaml:20:1: ERROR, Cycle detected in 'needs' requirements: job1 -> job2 -> job3 -> job1
```

### Git Errors

Git-related errors are clear and actionable:

```
Error: cicd must be run from within a Git repository
Branch mismatch: requested 'feature-x' but current branch is 'main'
Commit mismatch: requested 'abc123' but current HEAD is 'def456'
```

### Network Errors

Remote operation errors include HTTP details:

```
Execution request failed: HTTP 400 - Invalid pipeline configuration
Execution request failed: HTTP 500 - Internal server error
Failed to connect to the API Gateway. Is it running?
```

## Troubleshooting

### Common Issues

**"Error: cicd must be run from within a Git repository"**
- Ensure current directory is within a Git repository
- Check that `.git/` directory exists in repository root
- Run `git init` if starting a new repository

**"absolute paths are not allowed"**
- Use repository-relative paths only
- Example: Use `.pipelines/default.yaml` instead of `/home/user/repo/.pipelines/default.yaml`

**"configuration files must be located under .pipelines/"**
- All pipeline configs must be in `.pipelines/` directory at repo root
- Create directory: `mkdir -p .pipelines`
- Move configuration files to `.pipelines/`

**"Branch mismatch: requested 'X' but current branch is 'Y'"**
- Checkout the correct branch: `git checkout X`
- Or run without `--branch` to use current branch
- Use `git branch` to verify current branch

**"Commit mismatch: requested 'X' but current HEAD is 'Y'"**
- Checkout the correct commit: `git checkout X`
- Or use `--commit latest` to skip validation
- Use `git log --oneline` to find commit hashes

**"Failed to connect to the API Gateway. Is it running?"**
- Verify API Gateway is running: `curl http://localhost:8080/api/v1/pipelines/health`
- Check API Gateway URL configuration
- Ensure firewall allows connection to API Gateway port

**"Invalid options: --name and --file are mutually exclusive"**
- Use either `--name` OR `--file`, not both
- `--name` for pipelines in `.pipelines/`
- `--file` for specific pipeline file path

**"Error: --stage requires --run" / "Error: --job requires --stage"**
- Provide options in order: `--pipeline` → `--run` → `--stage` → `--job`
- Each level requires the one above it

## Development Guidelines

### Adding New Commands

1. Create command class in `commands/` package
2. Implement `Callable<Integer>` interface
3. Add Picocli `@Command` annotation
4. Implement validation and error handling
5. Register command in `CicdCli` subcommands
6. Write unit tests for command logic
7. Add BATS integration tests for CLI behavior
8. Update help documentation

### Adding New Validators

1. Create validator in `cores/` package
2. Implement clear validation rules
3. Throw specific exceptions with helpful messages
4. Write comprehensive unit tests
5. Document validation rules in Javadoc
6. Integrate with command execution flow

### Code Quality Standards

- Follow existing code patterns and structure
- Write comprehensive Javadoc for all public classes/methods
- Maintain test coverage above 70%
- Use Picocli best practices for command definition
- Provide clear, actionable error messages
- Run all checks before committing: `./gradlew check`

## Project Structure

```
cli/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── edu/northeastern/cs7580/cicd/cli/
│   │   │       ├── CicdCli.java
│   │   │       ├── commands/
│   │   │       ├── cores/
│   │   │       ├── formatters/
│   │   │       ├── clients/
│   │   │       ├── exceptions/
│   │   │       └── configs/
│   │   └── resources/
│   │       └── logback.xml
│   └── test/
│       ├── java/
│       │   └── edu/northeastern/cs7580/cicd/cli/
│       └── resources/
│           └── test-fixtures/
├── tests/
│   └── integration/
│       ├── verify.bats
│       ├── dryrun.bats
│       ├── run.bats
│       └── report.bats
└── build.gradle
```
