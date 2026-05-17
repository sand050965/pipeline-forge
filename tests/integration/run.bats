#!/usr/bin/env bats

CICD_BIN="./cli/build/install/cicd/cicd"
PIPELINE_AF="allow-failures-test"

setup_file() {
  CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
  out=$("./cli/build/install/cicd/cicd" run --local --name good-pipeline --branch "$CURRENT_BRANCH" 2>&1) || true
  echo "$out" | grep -oE '[0-9]+$' | tail -1 > "${BATS_FILE_TMPDIR}/gp_run_no"
  out=$("./cli/build/install/cicd/cicd" run --local --name allow-failures-test --branch "$CURRENT_BRANCH" 2>&1) || true
  echo "$out" | grep -oE '[0-9]+$' | tail -1 > "${BATS_FILE_TMPDIR}/af_run_no"
}

setup() {
  if [ ! -x "$CICD_BIN" ]; then
    echo "cicd executable not found at $CICD_BIN"
    exit 1
  fi
}

# --- Argument validation ---

@test "run: missing --name and --file exits with INVALID_CLI_ARGUMENTS (10)" {
  run "$CICD_BIN" run
  [ "$status" -eq 10 ]
  [[ "$output" == *"Missing required option"* ]]
}

@test "run: both --name and --file exits with INVALID_CLI_ARGUMENTS (10)" {
  run "$CICD_BIN" run --name default --file .pipelines/valid-pipeline.yaml
  [ "$status" -eq 10 ]
  [[ "$output" == *"mutually exclusive"* ]]
}

# --- Pipeline resolution ---

@test "run: --name with nonexistent pipeline name exits with error" {
  CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
  run "$CICD_BIN" run --local --name nonexistent-pipeline-xyz --branch "$CURRENT_BRANCH"
  [ "$status" -eq 2 ]
  [[ "$output" == *"No pipeline configuration found"* ]]
}

@test "run: --file with nonexistent file exits with error" {
  CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
  run "$CICD_BIN" run --local --file .pipelines/does-not-exist.yaml --branch "$CURRENT_BRANCH"
  [ "$status" -eq 2 ]
  [[ "$output" == *"Failed to read pipeline name from file"* ]]
}

# --- Branch/commit validation ---

@test "run: --branch with wrong branch exits with GIT_REPOSITORY_ERROR (3)" {
  run "$CICD_BIN" run --local --name good-pipeline --branch nonexistent-branch-xyz
  [ "$status" -eq 3 ]
  [[ "$output" == *"Branch mismatch"* ]]
}

@test "run: --commit with wrong commit hash exits with GIT_REPOSITORY_ERROR (3)" {
  CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
  run "$CICD_BIN" run --local --name good-pipeline --branch "$CURRENT_BRANCH" \
      --commit 0000000000000000000000000000000000000000
  [ "$status" -eq 3 ]
  [[ "$output" == *"Commit mismatch"* ]]
}

# --- Execution path ---

@test "run: --name with valid pipeline succeeds" {
  CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
  run "$CICD_BIN" run --local --name good-pipeline --branch "$CURRENT_BRANCH"
  [ "$status" -eq 0 ]
  [[ "$output" =~ run:[[:space:]][0-9]+ ]]
}

@test "run: --file with valid pipeline succeeds" {
  CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
  run "$CICD_BIN" run --local --file .pipelines/valid-pipeline.yaml --branch "$CURRENT_BRANCH"
  [ "$status" -eq 0 ]
  [[ "$output" =~ run:[[:space:]][0-9]+ ]]
}

# --- failures ---

@test "run: pipeline with failures: true job that fails exits 0" {
  CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
  run "$CICD_BIN" run --local --name "$PIPELINE_AF" --branch "$CURRENT_BRANCH"
  [ "$status" -eq 0 ]
  [[ "$output" =~ run:[[:space:]][0-9]+ ]]
}

# --- Async ---

@test "run: exits immediately without waiting for pipeline to complete (async)" {
  CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
  run "$CICD_BIN" run --local --name good-pipeline --branch "$CURRENT_BRANCH"
  [ "$status" -eq 0 ]
  # A run number is returned immediately — pipeline execution continues asynchronously
  [[ "$output" =~ run:[[:space:]][0-9]+ ]]
}

@test "run: invalid pipeline YAML exits 1 with validation error; no run number emitted" {
  CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
  run "$CICD_BIN" run --local --file .pipelines/cycle-detection.yaml --branch "$CURRENT_BRANCH"
  [ "$status" -eq 1 ]
  [[ "$output" != *"run: "* ]]
}

# --- Help ---

@test "run: --help exits with 0 and shows usage" {
  run "$CICD_BIN" run --help
  [ "$status" -eq 0 ]
  [[ "$output" == *"--name"* ]]
  [[ "$output" == *"--file"* ]]
  [[ "$output" == *"--branch"* ]]
  [[ "$output" == *"--commit"* ]]
}
