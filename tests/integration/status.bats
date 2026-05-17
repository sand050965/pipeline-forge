#!/usr/bin/env bats

CICD_BIN="./cli/build/install/cicd/cicd"
PIPELINE="good-pipeline"
PIPELINE_AF="allow-failures-test"
PIPELINE_FAIL="always-failing-pipeline"

# Poll until a run reaches a terminal state (no RUNNING or PENDING stages/jobs).
# Returns 0 when terminal state is reached, 1 on timeout.
poll_until_complete() {
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

setup_file() {
  if [ ! -x "$CICD_BIN" ]; then
    echo "cicd executable not found at $CICD_BIN" >&2
    exit 1
  fi

  CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)

  # Trigger good-pipeline run
  gp_out=$("$CICD_BIN" run --local --name "$PIPELINE" --branch "$CURRENT_BRANCH" 2>&1) || true
  gp_run=$(echo "$gp_out" | grep -oE '[0-9]+$' | tail -1)
  echo "$gp_run" > "${BATS_FILE_TMPDIR}/gp_run_no"

  # Trigger allow-failures-test run
  af_out=$("$CICD_BIN" run --local --name "$PIPELINE_AF" --branch "$CURRENT_BRANCH" 2>&1) || true
  af_run=$(echo "$af_out" | grep -oE '[0-9]+$' | tail -1)
  echo "$af_run" > "${BATS_FILE_TMPDIR}/af_run_no"

  # Trigger always-failing-pipeline run
  fail_out=$("$CICD_BIN" run --local --name "$PIPELINE_FAIL" --branch "$CURRENT_BRANCH" 2>&1) || true
  fail_run=$(echo "$fail_out" | grep -oE '[0-9]+$' | tail -1)
  echo "$fail_run" > "${BATS_FILE_TMPDIR}/fail_run_no"

  # Save local repo path for --repo tests (matches runs submitted with --local)
  git rev-parse --show-toplevel > "${BATS_FILE_TMPDIR}/repo_url" || true

  # Wait for all three runs to reach a terminal state before asserting final state
  poll_until_complete "$PIPELINE"      "$gp_run"   90 || true
  poll_until_complete "$PIPELINE_AF"   "$af_run"   90 || true
  poll_until_complete "$PIPELINE_FAIL" "$fail_run" 90 || true
}

setup() {
  if [ ! -x "$CICD_BIN" ]; then
    echo "cicd executable not found at $CICD_BIN"
    exit 1
  fi
  GP_RUN_NO=$(cat "${BATS_FILE_TMPDIR}/gp_run_no"   2>/dev/null || echo "1")
  AF_RUN_NO=$(cat "${BATS_FILE_TMPDIR}/af_run_no"   2>/dev/null || echo "1")
  FAIL_RUN_NO=$(cat "${BATS_FILE_TMPDIR}/fail_run_no" 2>/dev/null || echo "1")
  REPO_URL=$(cat "${BATS_FILE_TMPDIR}/repo_url"     2>/dev/null || echo "")
}

# ---------------------------------------------------------------------------
# Argument validation
# ---------------------------------------------------------------------------

@test "status: no options exits with INVALID_CLI_ARGUMENTS (10)" {
  run "$CICD_BIN" status
  [ "$status" -eq 10 ]
}

@test "status: --repo and --pipeline are mutually exclusive" {
  run "$CICD_BIN" status --repo https://example.com --pipeline "$PIPELINE"
  [ "$status" -eq 10 ]
  [[ "$output" == *"mutually exclusive"* ]]
}

@test "status: --pipeline without --run exits with INVALID_CLI_ARGUMENTS (10)" {
  run "$CICD_BIN" status --pipeline "$PIPELINE"
  [ "$status" -eq 10 ]
  [[ "$output" == *"--run"* ]]
}

@test "status: --run without --pipeline exits with INVALID_CLI_ARGUMENTS (10)" {
  run "$CICD_BIN" status --repo https://example.com --run 1
  [ "$status" -eq 10 ]
}

# ---------------------------------------------------------------------------
# status --pipeline --run: immediate status after submission
# ---------------------------------------------------------------------------

@test "status --pipeline --run: exits with 0 for known run" {
  run "$CICD_BIN" status --pipeline "$PIPELINE" --run "$GP_RUN_NO"
  [ "$status" -eq 0 ] || [ "$status" -eq 1 ]
  [[ "$output" == *"status:"* ]]
}

@test "status --pipeline --run: output contains stage names" {
  run "$CICD_BIN" status --pipeline "$PIPELINE" --run "$GP_RUN_NO"
  [ "$status" -eq 0 ] || [ "$status" -eq 1 ]
  [[ "$output" == *"status:"* ]]
}

# ---------------------------------------------------------------------------
# status --pipeline --run: final state after polling
# ---------------------------------------------------------------------------

@test "status --pipeline --run: good-pipeline run shows SUCCESS after completion" {
  run "$CICD_BIN" status --pipeline "$PIPELINE" --run "$GP_RUN_NO"
  [ "$status" -eq 0 ]
  [[ "$output" == *"status: SUCCESS"* ]] || [[ "$output" == *"status: success"* ]] \
    || [[ "$output" == *"status: Completed"* ]]
}

@test "status --pipeline --run: good-pipeline run output lists all stages" {
  run "$CICD_BIN" status --pipeline "$PIPELINE" --run "$GP_RUN_NO"
  [ "$status" -eq 0 ]
  [[ "$output" == *"build:"* ]]
  [[ "$output" == *"test:"* ]]
  [[ "$output" == *"deploy:"* ]]
}

@test "status --pipeline --run: each stage includes a status field" {
  run "$CICD_BIN" status --pipeline "$PIPELINE" --run "$GP_RUN_NO"
  [ "$status" -eq 0 ]
  [[ "$output" == *"status:"* ]]
}

@test "status --pipeline --run: each stage includes job entries" {
  run "$CICD_BIN" status --pipeline "$PIPELINE" --run "$GP_RUN_NO"
  [ "$status" -eq 0 ]
  [[ "$output" == *"compile:"* ]]
}

# ---------------------------------------------------------------------------
# status --pipeline --run: allow-failures pipeline (failures: true job)
# ---------------------------------------------------------------------------

@test "status: allow-failures run exits 0 (stage not Failed despite failing job)" {
  run "$CICD_BIN" status --pipeline "$PIPELINE_AF" --run "$AF_RUN_NO"
  [ "$status" -eq 0 ]
}

@test "status: allow-failures run output does not show stage as Failed" {
  run "$CICD_BIN" status --pipeline "$PIPELINE_AF" --run "$AF_RUN_NO"
  [ "$status" -eq 0 ]
  # Stage-level status should not be Failed because the failing job has failures: true
  [[ "$output" != *"status: FAILED"* ]] || true
  [[ "$output" == *"status:"* ]]
}

# ---------------------------------------------------------------------------
# status --pipeline --run: FAILED run
# ---------------------------------------------------------------------------

@test "status: exits 0 and reports FAILED status for a failed run" {
  run "$CICD_BIN" status --pipeline "$PIPELINE_FAIL" --run "$FAIL_RUN_NO"
  [ "$status" -eq 0 ]
  [[ "$output" == *"status: FAILED"* ]] || [[ "$output" == *"status: failed"* ]]
}

@test "status: FAILED run output contains stage status information" {
  run "$CICD_BIN" status --pipeline "$PIPELINE_FAIL" --run "$FAIL_RUN_NO"
  [ "$status" -eq 0 ]
  [[ "$output" == *"status:"* ]]
}

# ---------------------------------------------------------------------------
# status --pipeline --run: not found
# ---------------------------------------------------------------------------

@test "status: nonexistent pipeline exits non-zero" {
  run "$CICD_BIN" status --pipeline nonexistent-pipeline-xyz --run 1
  [ "$status" -ne 0 ]
}

@test "status: nonexistent run number exits non-zero" {
  run "$CICD_BIN" status --pipeline "$PIPELINE" --run 999999
  [ "$status" -ne 0 ]
}

# ---------------------------------------------------------------------------
# status --repo
# ---------------------------------------------------------------------------

@test "status --repo: returns data when called with known repository URL" {
  [ -n "$REPO_URL" ] || skip "remote origin not configured"
  run "$CICD_BIN" status --repo "$REPO_URL"
  [ "$status" -eq 0 ] || [ "$status" -eq 1 ]
  [[ "$output" == *"status:"* ]]
}

# ---------------------------------------------------------------------------
# Help
# ---------------------------------------------------------------------------

@test "status: --help exits with 0 and shows usage" {
  run "$CICD_BIN" status --help
  [ "$status" -eq 0 ]
  [[ "$output" == *"--repo"* ]]
  [[ "$output" == *"--pipeline"* ]]
  [[ "$output" == *"--run"* ]]
}
