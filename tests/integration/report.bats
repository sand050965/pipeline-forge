#!/usr/bin/env bats

CICD_BIN="./cli/build/install/cicd/cicd"
PIPELINE="good-pipeline"
PIPELINE_AF="allow-failures-test"

setup() {
  if [ ! -x "$CICD_BIN" ]; then
    echo "cicd executable not found at $CICD_BIN"
    exit 1
  fi
}

setup_file() {
  # Seed the DB with a known run by executing the pipeline
  CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
  "$CICD_BIN" run --name good-pipeline --branch "$CURRENT_BRANCH" >/dev/null 2>&1 || true
  "$CICD_BIN" run --name allow-failures-test --branch "$CURRENT_BRANCH" >/dev/null 2>&1 || true
}

# ---------------------------------------------------------------------------
# Argument validation
# ---------------------------------------------------------------------------

@test "report: missing --pipeline exits with error (2)" {
  run "$CICD_BIN" report
  [ "$status" -eq 2 ]
}

@test "report: --stage without --run exits with INVALID_CLI_ARGUMENTS (10)" {
  run "$CICD_BIN" report --pipeline "$PIPELINE" --stage build
  [ "$status" -eq 10 ]
  [[ "$output" == *"--stage"* ]]
}

@test "report: --job without --stage exits with INVALID_CLI_ARGUMENTS (10)" {
  run "$CICD_BIN" report --pipeline "$PIPELINE" --run 1 --job compile
  [ "$status" -eq 10 ]
  [[ "$output" == *"--job"* ]]
}

# ---------------------------------------------------------------------------
# Not found errors
# ---------------------------------------------------------------------------

@test "report: nonexistent pipeline returns not found error" {
  run "$CICD_BIN" report --pipeline nonexistent-pipeline-xyz
  [ "$status" -ne 0 ]
  [[ "$output" == *"Not found:"* ]]
}

@test "report: nonexistent run number returns not found error" {
  run "$CICD_BIN" report --pipeline "$PIPELINE" --run 999999
  [ "$status" -ne 0 ]
  [[ "$output" == *"Not found:"* ]]
}

@test "report: nonexistent stage returns not found error" {
  run "$CICD_BIN" report --pipeline "$PIPELINE" --run 1 --stage nonexistent-stage
  [ "$status" -ne 0 ]
  [[ "$output" == *"Not found:"* ]]
}

@test "report: nonexistent job returns not found error" {
  run "$CICD_BIN" report --pipeline "$PIPELINE" --run 1 --stage build --job nonexistent-job
  [ "$status" -ne 0 ]
  [[ "$output" == *"Not found:"* ]]
}

# ---------------------------------------------------------------------------
# cicd report --pipeline <name>
# ---------------------------------------------------------------------------

@test "report --pipeline: exits with 0" {
  run "$CICD_BIN" report --pipeline "$PIPELINE"
  [ "$status" -eq 0 ]
}

@test "report --pipeline: output contains pipeline name" {
  run "$CICD_BIN" report --pipeline "$PIPELINE"
  [ "$status" -eq 0 ]
  [[ "$output" == *"name: $PIPELINE"* ]]
}

@test "report --pipeline: output contains runs list" {
  run "$CICD_BIN" report --pipeline "$PIPELINE"
  [ "$status" -eq 0 ]
  [[ "$output" == *"runs:"* ]]
}

@test "report --pipeline: output contains run-no field" {
  run "$CICD_BIN" report --pipeline "$PIPELINE"
  [ "$status" -eq 0 ]
  [[ "$output" == *"run-no:"* ]]
}

@test "report --pipeline: output contains status field" {
  run "$CICD_BIN" report --pipeline "$PIPELINE"
  [ "$status" -eq 0 ]
  [[ "$output" == *"status:"* ]]
}

@test "report --pipeline: output contains git-branch field" {
  run "$CICD_BIN" report --pipeline "$PIPELINE"
  [ "$status" -eq 0 ]
  [[ "$output" == *"git-branch:"* ]]
}

@test "report --pipeline: output contains start and end timestamps" {
  run "$CICD_BIN" report --pipeline "$PIPELINE"
  [ "$status" -eq 0 ]
  [[ "$output" == *"start:"* ]]
  [[ "$output" == *"end:"* ]]
}

@test "report --pipeline: seeded run shows success status" {
  run "$CICD_BIN" report --pipeline "$PIPELINE"
  [ "$status" -eq 0 ]
  [[ "$output" == *"status: success"* ]] || [[ "$output" == *"status: SUCCESS"* ]]
}

# ---------------------------------------------------------------------------
# cicd report --pipeline <name> --run 1
# ---------------------------------------------------------------------------

@test "report --pipeline --run: exits with 0" {
  run "$CICD_BIN" report --pipeline "$PIPELINE" --run 1
  [ "$status" -eq 0 ]
}

@test "report --pipeline --run: output contains pipeline name" {
  run "$CICD_BIN" report --pipeline "$PIPELINE" --run 1
  [ "$status" -eq 0 ]
  [[ "$output" == *"name: $PIPELINE"* ]]
}

@test "report --pipeline --run: output contains run-no" {
  run "$CICD_BIN" report --pipeline "$PIPELINE" --run 1
  [ "$status" -eq 0 ]
  [[ "$output" == *"run-no: 1"* ]]
}

@test "report --pipeline --run: output contains stages list" {
  run "$CICD_BIN" report --pipeline "$PIPELINE" --run 1
  [ "$status" -eq 0 ]
  [[ "$output" == *"stages:"* ]]
}

@test "report --pipeline --run: stages include build, test, deploy" {
  run "$CICD_BIN" report --pipeline "$PIPELINE" --run 1
  [ "$status" -eq 0 ]
  [[ "$output" == *"name: build"* ]]
  [[ "$output" == *"name: test"* ]]
  [[ "$output" == *"name: deploy"* ]]
}

@test "report --pipeline --run: output contains start and end timestamps" {
  run "$CICD_BIN" report --pipeline "$PIPELINE" --run 1
  [ "$status" -eq 0 ]
  [[ "$output" == *"start:"* ]]
  [[ "$output" == *"end:"* ]]
}

@test "report --pipeline --run: run shows success status" {
  run "$CICD_BIN" report --pipeline "$PIPELINE" --run 1
  [ "$status" -eq 0 ]
  [[ "$output" == *"status: success"* ]] || [[ "$output" == *"status: SUCCESS"* ]]
}

# ---------------------------------------------------------------------------
# cicd report --pipeline <name> --run 1 --stage build
# ---------------------------------------------------------------------------

@test "report --pipeline --run --stage: exits with 0" {
  run "$CICD_BIN" report --pipeline "$PIPELINE" --run 1 --stage build
  [ "$status" -eq 0 ]
}

@test "report --pipeline --run --stage: output contains stage name" {
  run "$CICD_BIN" report --pipeline "$PIPELINE" --run 1 --stage build
  [ "$status" -eq 0 ]
  [[ "$output" == *"name: build"* ]]
}

@test "report --pipeline --run --stage: output contains jobs list" {
  run "$CICD_BIN" report --pipeline "$PIPELINE" --run 1 --stage build
  [ "$status" -eq 0 ]
  [[ "$output" == *"jobs:"* ]]
}

@test "report --pipeline --run --stage: jobs include compile" {
  run "$CICD_BIN" report --pipeline "$PIPELINE" --run 1 --stage build
  [ "$status" -eq 0 ]
  [[ "$output" == *"name: compile"* ]]
}

@test "report --pipeline --run --stage: output contains start and end timestamps" {
  run "$CICD_BIN" report --pipeline "$PIPELINE" --run 1 --stage build
  [ "$status" -eq 0 ]
  [[ "$output" == *"start:"* ]]
  [[ "$output" == *"end:"* ]]
}

@test "report --pipeline --run --stage: stage shows success status" {
  run "$CICD_BIN" report --pipeline "$PIPELINE" --run 1 --stage build
  [ "$status" -eq 0 ]
  [[ "$output" == *"status: success"* ]] || [[ "$output" == *"status: SUCCESS"* ]]
}

# ---------------------------------------------------------------------------
# cicd report --pipeline <name> --run 1 --stage build --job compile
# ---------------------------------------------------------------------------

@test "report --pipeline --run --stage --job: exits with 0" {
  run "$CICD_BIN" report --pipeline "$PIPELINE" --run 1 --stage build --job compile
  [ "$status" -eq 0 ]
}

@test "report --pipeline --run --stage --job: output contains job name" {
  run "$CICD_BIN" report --pipeline "$PIPELINE" --run 1 --stage build --job compile
  [ "$status" -eq 0 ]
  [[ "$output" == *"name: compile"* ]]
}

@test "report --pipeline --run --stage --job: output contains job key" {
  run "$CICD_BIN" report --pipeline "$PIPELINE" --run 1 --stage build --job compile
  [ "$status" -eq 0 ]
  [[ "$output" == *"job:"* ]]
}

@test "report --pipeline --run --stage --job: output contains start and end timestamps" {
  run "$CICD_BIN" report --pipeline "$PIPELINE" --run 1 --stage build --job compile
  [ "$status" -eq 0 ]
  [[ "$output" == *"start:"* ]]
  [[ "$output" == *"end:"* ]]
}

@test "report --pipeline --run --stage --job: job shows success status" {
  run "$CICD_BIN" report --pipeline "$PIPELINE" --run 1 --stage build --job compile
  [ "$status" -eq 0 ]
  [[ "$output" == *"status: success"* ]] || [[ "$output" == *"status: SUCCESS"* ]]
}

# ---------------------------------------------------------------------------
# failures: end-to-end
# ---------------------------------------------------------------------------

@test "report: failures job reports failures: true" {
  run "$CICD_BIN" report --pipeline "$PIPELINE_AF" --run 1 --stage build --job always-failing-job
  [ "$status" -eq 0 ]
  [[ "$output" == *"failures: true"* ]]
}

@test "report: job without failures key reports failures: false" {
  run "$CICD_BIN" report --pipeline "$PIPELINE_AF" --run 1 --stage build --job always-passing-job
  [ "$status" -eq 0 ]
  [[ "$output" == *"failures: false"* ]]
}

@test "report: every job in stage report includes failures field" {
  run "$CICD_BIN" report --pipeline "$PIPELINE" --run 1 --stage build --job compile
  [ "$status" -eq 0 ]
  [[ "$output" == *"failures:"* ]]
}

# ---------------------------------------------------------------------------
# trace-id: present in --run output, absent in all-runs output
# ---------------------------------------------------------------------------

@test "report --pipeline --run: output contains trace-id field" {
  run "$CICD_BIN" report --pipeline "$PIPELINE" --run 1
  [ "$status" -eq 0 ]
  [[ "$output" == *"trace-id:"* ]]
}

@test "report --pipeline --run: trace-id is a non-empty hex string" {
  run "$CICD_BIN" report --pipeline "$PIPELINE" --run 1
  [ "$status" -eq 0 ]
  trace_id_line=$(echo "$output" | grep "trace-id:")
  trace_id_value=$(echo "$trace_id_line" | sed 's/.*trace-id: *//')
  [[ "$trace_id_value" =~ ^[0-9a-f]+$ ]]
  [ "${#trace_id_value}" -gt 0 ]
}

@test "report --pipeline (all runs): output does not contain trace-id field" {
  run "$CICD_BIN" report --pipeline "$PIPELINE"
  [ "$status" -eq 0 ]
  [[ "$output" != *"trace-id:"* ]]
}

# ---------------------------------------------------------------------------
# Help
# ---------------------------------------------------------------------------

@test "report: --help exits with 0 and shows usage" {
  run "$CICD_BIN" report --help
  [ "$status" -eq 0 ]
  [[ "$output" == *"--pipeline"* ]]
  [[ "$output" == *"--run"* ]]
  [[ "$output" == *"--stage"* ]]
  [[ "$output" == *"--job"* ]]
}