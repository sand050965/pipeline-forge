#!/usr/bin/env bats

@test "dryrun exits 10 for missing file" {
  run ./cli/build/install/cicd/cicd dryrun .pipelines/missing.yaml
  [ "$status" -eq 10 ]
  [[ "$output" == *"Error"* ]]
}

@test "dryrun exits 0 for valid pipeline and prints execution plan" {
  run ./cli/build/install/cicd/cicd dryrun .pipelines/valid-pipeline.yaml

  [ "$status" -eq 0 ]
  [[ "$output" == *"OK: dryrun validation succeeded"* ]]

  [[ "$output" == *"build:"* ]]
  [[ "$output" == *"test:"* ]]
  [[ "$output" == *"deploy:"* ]]

  [[ "$output" == *"needs:"* ]]
  [[ "$output" == *"failures:"* ]]
}

@test "dryrun includes failures: true for job with failures: true" {
  run ./cli/build/install/cicd/cicd dryrun .pipelines/allow-failures-test.yaml

  [ "$status" -eq 0 ]
  [[ "$output" == *"failures: true"* ]]
}

@test "dryrun includes failures: false for job without failures key" {
  run ./cli/build/install/cicd/cicd dryrun .pipelines/allow-failures-test.yaml

  [ "$status" -eq 0 ]
  [[ "$output" == *"failures: false"* ]]
}
