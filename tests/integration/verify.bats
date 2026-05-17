#!/usr/bin/env bats

CICD_BIN="./cli/build/install/cicd/cicd"
PIPELINE_DIR=".pipelines"

setup() {
  if [ ! -x "$CICD_BIN" ]; then
    echo "cicd executable not found at $CICD_BIN"
    exit 1
  fi
}

@test "verify: valid-pipeline.yaml passes validation" {
  run "$CICD_BIN" verify "$PIPELINE_DIR/valid-pipeline.yaml"
  [ "$status" -eq 0 ]
  [[ "$output" =~ "valid-pipeline.yaml" ]] || true
}

@test "verify: verifying .pipelines directory fails due to invalid files" {
  run "$CICD_BIN" verify "$PIPELINE_DIR"
  [ "$status" -ne 0 ]
}

@test "verify: cycle-detection.yaml fails due to cycle dependency" {
  run "$CICD_BIN" verify "$PIPELINE_DIR/cycle-detection.yaml"
  [ "$status" -ne 0 ]
  [[ "$output" =~ "cycle-detection.yaml" ]]
  [[ "$output" =~ "Cycle detected" ]]
  [[ "$output" =~ "J1" ]]
  [[ "$output" =~ "J2" ]]
}

@test "verify: empty-file.yaml fails because file is empty" {
  run "$CICD_BIN" verify "$PIPELINE_DIR/empty-file.yaml"
  [ "$status" -ne 0 ]
  [[ "$output" =~ "empty-file.yaml" ]]
  [[ "$output" =~ "YAML file is empty or invalid" ]]
}

@test "verify: error output references the filename" {
  run "$CICD_BIN" verify "$PIPELINE_DIR/cycle-detection.yaml"
  [ "$status" -ne 0 ]
  [[ "$output" =~ "cycle-detection.yaml" ]]
  [[ "$output" =~ "ERROR" ]]
}

@test "verify: cross-stage needs are rejected" {
  run "$CICD_BIN" verify "$PIPELINE_DIR/cross-stage-needs.yaml"

  [ "$status" -ne 0 ]
  [[ "$output" =~ "cannot depend" ]]
}

@test "verify .pipelines aggregates multiple file errors" {
  run "$CICD_BIN" verify "$PIPELINE_DIR"

  [ "$status" -ne 0 ]

  # Check errors from different files (proves aggregation)
  [[ "$output" =~ "cycle-detection.yaml" ]]
  [[ "$output" =~ "duplicate-stages.yaml" ]]
  [[ "$output" =~ "empty-file.yaml" ]]

  [[ "$output" =~ "Cycle detected" ]]
  [[ "$output" =~ "Duplicate stage name" ]]
  [[ "$output" =~ "YAML file is empty or invalid" ]]
}