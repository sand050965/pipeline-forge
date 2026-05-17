# Beta Release

## Features Implemented

- Pipeline YAML validation (`cicd verify`) with multi-error collection
- Structured validation error messages with file name, line, and column positions
- Execution plan preview (`cicd dryrun`) showing job ordering respecting `needs` dependencies
- Pipeline execution in Docker containers (`cicd run`)
- Fail-fast behavior on job failure — remaining jobs are skipped
- Branch and commit enforcement for `cicd run`
- Execution history reporting (`cicd report`) at pipeline, run, stage, and job granularity
- API Gateway routing to Execution Service and Report Service
- PostgreSQL-backed execution persistence

---

## Implementation Limitations

**Sequential-only execution.** Jobs within a stage execute one at a time. Parallel job execution is not supported.

**Phase 1 deployment only.** All services run on localhost on fixed ports. There is no cloud or remote deployment support in this release.

**Branch/commit switching not supported.** The `--branch` and `--commit` flags validate that the current local Git state matches the requested values. The CLI does not switch branches or check out commits automatically — the correct branch must already be checked out before running.

**No authentication or access control.** All API endpoints are open. There is no user authentication, API key validation, or role-based access control.

**No pipeline cancellation or retry.** There is no mechanism to cancel a running pipeline or automatically retry failed jobs.

**No pipeline triggers.** Pipelines can only be started manually via the CLI. Webhook-based triggers, scheduled runs, and event-driven execution are not supported.

**Report output is YAML format only.** No other output formats (JSON, table, HTML) are supported.

**Docker must be pre-installed.** The system executes jobs inside Docker containers but does not manage Docker installation or configuration. Docker must be installed and running on the host before using `cicd run`.

**YAML validation limitations.** Colons followed by spaces inside unquoted scalar values (e.g. `key: value: extra`) are not detected by the validator and will cause silent parse failures. Invalid shell syntax inside `script` fields is also not validated at parse time and will only surface as a runtime error during execution.

**Git validation is CLI-only.** Branch and commit enforcement is performed by the CLI before the request is forwarded to the API Gateway. If the API Gateway is called directly, bypassing the CLI, no Git state validation occurs and the request will be processed regardless of the actual repository state.

---

## Installation and Usage

Follow the **[Non-Developer Guide](dev-docs/non-developer-guide.md)** or **[Developer Guide](dev-docs/developer-guide.md)** 
to install Docker, download the release files (`docker-compose.yml` and the CLI), install the CLI, and start the services.
Follow the **[Evaluator Guide](../../README.md)** to run examples after installing the system.
