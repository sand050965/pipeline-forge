#!/usr/bin/env bats
#
# observability.bats — End-to-end observability integration tests.
#
# Verifies that after a pipeline run:
#   - Required Prometheus metrics exist with the correct labels
#   - Loki contains structured logs queryable by run_no
#   - Container stdout/stderr logs appear in Loki labeled source=container
#   - cicd report --run <n> includes a non-empty trace-id field
#
# Prerequisites:
#   - Full stack running: docker compose up -d
#   - CLI installed:      ./gradlew :cli:installCli
#
# Prometheus and Loki are queried via their HTTP APIs on localhost.
# Override the defaults with env vars PROMETHEUS_URL and LOKI_URL.

CICD_BIN="./cli/build/install/cicd/cicd"
PIPELINE="good-pipeline"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
LOKI_URL="${LOKI_URL:-http://localhost:3100}"

# ---------------------------------------------------------------------------
# Helper: poll cicd status until the run leaves RUNNING / PENDING state.
# Returns 0 when a terminal state is reached, 1 on timeout.
# ---------------------------------------------------------------------------
_wait_for_completion() {
  local pipeline="$1"
  local run_no="$2"
  local max_wait="${3:-90}"
  local elapsed=0

  while [ "$elapsed" -lt "$max_wait" ]; do
    local out
    out=$("$CICD_BIN" status --pipeline "$pipeline" --run "$run_no" 2>&1) || true
    if ! echo "$out" | grep -qiE "status: (RUNNING|PENDING)"; then
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  return 1
}

# ---------------------------------------------------------------------------
# setup_file: runs once before all tests in this file.
#
# 1. Trigger a pipeline run and capture the run number.
# 2. Poll until the pipeline reaches a terminal state (success / failed).
# 3. Sleep an extra 30 s to let the OTel Collector batch-flush telemetry and
#    allow Prometheus to complete at least one scrape cycle.
# ---------------------------------------------------------------------------
setup_file() {
  CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)

  local out
  out=$("$CICD_BIN" run --name "$PIPELINE" --branch "$CURRENT_BRANCH" 2>&1) || true
  local run_no
  run_no=$(echo "$out" | grep -oE '[0-9]+$' | tail -1)
  echo "$run_no" > "${BATS_FILE_TMPDIR}/obs_run_no"

  _wait_for_completion "$PIPELINE" "$run_no" 90 || true

  # OTel Collector batch timeout = 5 s, Prometheus scrape_interval = 15 s.
  # 30 s guarantees at least one complete scrape cycle after the run finishes.
  sleep 30
}

# ---------------------------------------------------------------------------
# setup: runs before each individual test.
# ---------------------------------------------------------------------------
setup() {
  if [ ! -x "$CICD_BIN" ]; then
    echo "CLI binary not found: $CICD_BIN" >&2
    exit 1
  fi
  OBS_RUN_NO=$(cat "${BATS_FILE_TMPDIR}/obs_run_no" 2>/dev/null || echo "1")
}

# ===========================================================================
# Prometheus tests
# ===========================================================================

@test "obs/prometheus: cicd_pipeline_runs_total is present with pipeline and status labels" {
  # Query Prometheus for the counter with the specific pipeline+status combination.
  # An empty "result":[] means the metric was never recorded — the test fails.
  run curl -sf "$PROMETHEUS_URL/api/v1/query" \
    --data-urlencode "query=cicd_pipeline_runs_total{pipeline=\"$PIPELINE\",status=\"success\"}"
  [ "$status" -eq 0 ]
  [[ "$output" != *'"result":[]'* ]]
  [[ "$output" == *'"pipeline":"good-pipeline"'* ]]
  [[ "$output" == *'"status":"success"'* ]]
}

@test "obs/prometheus: cicd_job_runs_total is present with pipeline, stage, job, and status labels" {
  # cicd_job_runs_total must carry all four required labels.
  # We query for successful jobs; the response must include stage and job label keys.
  run curl -sf "$PROMETHEUS_URL/api/v1/query" \
    --data-urlencode "query=cicd_job_runs_total{pipeline=\"$PIPELINE\",status=\"success\"}"
  [ "$status" -eq 0 ]
  [[ "$output" != *'"result":[]'* ]]
  [[ "$output" == *'"pipeline":"good-pipeline"'* ]]
  [[ "$output" == *'"stage"'* ]]
  [[ "$output" == *'"job_name"'* ]]
  [[ "$output" == *'"status":"success"'* ]]
}

# ===========================================================================
# Loki tests
# ===========================================================================

@test "obs/loki: logs for the run_no are queryable in Loki" {
  # LogQL: filter execution-service logs by the run_no structured metadata field.
  # query_range without start/end defaults to the last 1 h — enough for this run.
  run curl -sf "$LOKI_URL/loki/api/v1/query_range" \
    --get \
    --data-urlencode "query={service_name=\"execution-service\"} | run_no=\"$OBS_RUN_NO\"" \
    --data-urlencode "limit=5"
  [ "$status" -eq 0 ]
  [[ "$output" != *'"result":[]'* ]]
}

@test "obs/loki: container logs appear in Loki with source=container for the run" {
  # The DockerExecutor sets MDC source=container for all stdout/stderr it captures.
  # This test confirms those container logs were forwarded to Loki and are
  # distinguishable from CI/CD service logs via the source structured metadata field.
  run curl -sf "$LOKI_URL/loki/api/v1/query_range" \
    --get \
    --data-urlencode "query={service_name=\"execution-service\"} | source=\"container\" | run_no=\"$OBS_RUN_NO\"" \
    --data-urlencode "limit=5"
  [ "$status" -eq 0 ]
  [[ "$output" != *'"result":[]'* ]]
}

# ===========================================================================
# trace-id test
# ===========================================================================

@test "obs/report: cicd report --run includes a non-empty trace-id field" {
  # The report for a specific run must emit a trace-id line whose value is a
  # lowercase hex string (W3C trace ID format, 32 chars).
  run "$CICD_BIN" report --pipeline "$PIPELINE" --run "$OBS_RUN_NO"
  [ "$status" -eq 0 ]
  [[ "$output" == *"trace-id:"* ]]
  local trace_id
  trace_id=$(echo "$output" | grep "trace-id:" | sed 's/.*trace-id:[[:space:]]*//')
  [[ "$trace_id" =~ ^[0-9a-f]+$ ]]
  [ "${#trace_id}" -gt 0 ]
}
