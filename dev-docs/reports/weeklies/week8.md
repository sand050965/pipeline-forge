# Week 8

# Completed tasks

| Task                                                                                                                                       | Weight | Assignee | 
|--------------------------------------------------------------------------------------------------------------------------------------------|--------|----------| 
| [[OBS] Build Required Grafana Dashboards](https://github.com/CS7580-SEA-SP26/c-team/issues/267#issue-4140170816)                           | L      | Jiachen  | 
| [[OBS] BATS Integration Tests for Observability](https://github.com/CS7580-SEA-SP26/c-team/issues/269#issue-4140176117)                    | M      | Jiachen  | 
| [Services setup during CI/Cd](https://github.com/CS7580-SEA-SP26/c-team/issues/278#issue-4149959322)                                       | S      | Lelin    | 
| [Static Analysis does not need to depend on Compile](https://github.com/CS7580-SEA-SP26/c-team/issues/279#issue-4149968754)                | S      | Lelin    | 
| [[Doc] Update README](https://github.com/CS7580-SEA-SP26/c-team/issues/286#issue-4165410624)                                               | S      | Blake    | 
| [[OBS] Instrument CI/CD Services — Metrics](https://github.com/CS7580-SEA-SP26/c-team/issues/268#issue-4140174539)                         | M      | Lelin    | 
| [[OBS] Instrument CI/CD Services — Structured Logs](https://github.com/CS7580-SEA-SP26/c-team/issues/271#issue-4140179546)                 | M      | Lelin    | 
| [[OBS] Add trace-id to cicd report Output](https://github.com/CS7580-SEA-SP26/c-team/issues/270#issue-4140176153)                          | M      | Blake    | 
| [[OBS] Collect Pipeline Job Container Logs](https://github.com/CS7580-SEA-SP26/c-team/issues/272#issue-4140180791)                         | M      | Blake    | 
| [[OBS] Instrument CI/CD Services — Distributed Traces](https://github.com/CS7580-SEA-SP26/c-team/issues/265#issue-4140167589)              | M      | Blake    | 
| [[OBS] Deploy and Configure the Observability Stack](https://github.com/CS7580-SEA-SP26/c-team/issues/266#issue-4140170632)                | M      | Lelin    | 
| [[Obs] Fix Grafana dashboard display](https://github.com/CS7580-SEA-SP26/c-team/issues/290#issue-4166916115)                               | M      | Jiachen  |
| [[OBS] Fix dashboard rendering issues found during live validation](https://github.com/CS7580-SEA-SP26/c-team/issues/292#issue-4167603569) | M      | Jiachen  |
| [[Demo] Sprint 8 Demo Grafana and Update helm chart for observability services](https://github.com/CS7580-SEA-SP26/c-team/issues/289#issue-4166900767)                                                          | L      | Lelin    |
| [[Weekly Report] Create weekly report for sprint 8](https://github.com/CS7580-SEA-SP26/c-team/issues/285#issue-4165403251)                 | S      | Jiachen  |


# Carry over tasks

None

# What worked this week?

- During this week, we maintained a high level of productivity and successfully configured observability for our system. 
This allows us to better monitor system behavior, track pipeline execution, and improve debugging and performance 
analysis across our CI/CD workflows.

# What did not work this week?

- Following Professor Skoteiniotis’s feedback, we identified that our CI/CD pipelines were using more resources than 
necessary, leading to increased GitHub Actions usage and higher consumption of Action Credits. We optimized our 
workflows by removing unused services from both PR and main pipelines. In addition, we eliminated unnecessary 
dependencies between jobs (for example, static analysis depending on compilation), which previously caused unnecessary 
sequential execution. We also refined pipeline triggers by adding paths-ignore rules (for example, 
ignoring *.md changes) in both pr.yaml and main.yaml, reducing redundant pipeline runs and improving overall efficiency.

# Design updates

- This week we added full observability to the CI/CD system. The execution service now emits metrics, structured logs, 
and distributed traces to a central OpenTelemetry Collector, which routes them to Prometheus, Loki, and 
Tempo respectively. Four Grafana dashboards are provisioned automatically on startup, covering pipeline health, 
stage/job duration breakdowns, a unified log viewer, and a trace explorer. 
  - Link to update: - [Observability](../../../README.md#observability)