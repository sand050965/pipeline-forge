# Feature Status

## Fully Implemented Features

### CLI Commands
- **`verify`** — Validates pipeline YAML with precise error reporting (file:line:column), circular dependency detection, and multi-error collection
- **`dryrun`** — Previews execution plan (stages, jobs, dependencies, wave order) without running anything
- **`run`** — Submits pipeline for remote execution; returns immediately with execution ID (202 Accepted); supports branch/commit parameters
- **`status`** — Queries live or most recent execution status; supports lookup by repository URL or pipeline name
- **`report`** — Hierarchical execution reporting at pipeline / run / stage / job levels with timestamps and trace IDs

### Execution Engine
- **Parallel execution** — Jobs within a stage are grouped into waves by dependency order; all jobs in a wave run concurrently via `CompletableFuture`
- **Allow-failures feature** — Per-job `failures: true` override: downstream dependents treat the job as always satisfied and run regardless of whether it succeeded or failed

### Observability Stack
- **OpenTelemetry** — Distributed tracing end-to-end (CLI → Gateway → services → Docker containers); custom metrics (counters/timers per run/stage/job); structured logging via OTel Logback appender
- **Prometheus** (port 9090) — Metrics storage and query
- **Grafana Loki** (port 3100) — Log aggregation for service logs and container stdout/stderr
- **Grafana Tempo** (port 3200) — Distributed trace storage
- **Grafana** (port 3000) — 4 pre-provisioned dashboards: Pipeline Overview, Stage/Job Breakdown, Logs Viewer, Trace Explorer
