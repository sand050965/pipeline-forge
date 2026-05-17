-- ---------------------------------------------------------------------------
-- Test-compatible schema matching V1__init_schema.sql
-- Used by R2DBC tests with H2; omits PostgreSQL-specific features
-- (ENUM types, triggers, indexes).
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pipelines (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    repo_id       VARCHAR(64)   NOT NULL,
    pipeline_name VARCHAR(255)  NOT NULL,
    git_repo      VARCHAR(1024) NOT NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(repo_id, pipeline_name)
);

CREATE TABLE IF NOT EXISTS pipeline_runs (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    pipeline_id   BIGINT        NOT NULL REFERENCES pipelines(id),
    run_no        INTEGER       NOT NULL,
    status        VARCHAR(50)   NOT NULL,
    git_branch    VARCHAR(255)  NOT NULL,
    git_hash      CHAR(40)      NOT NULL,
    start_time    TIMESTAMP,
    end_time      TIMESTAMP,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(pipeline_id, run_no)
);

CREATE TABLE IF NOT EXISTS stage_runs (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    pipeline_run_id  BIGINT       NOT NULL REFERENCES pipeline_runs(id),
    stage_name       VARCHAR(255) NOT NULL,
    status           VARCHAR(50)  NOT NULL,
    start_time       TIMESTAMP,
    end_time         TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS job_runs (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    stage_run_id   BIGINT       NOT NULL REFERENCES stage_runs(id),
    job_name       VARCHAR(255) NOT NULL,
    status         VARCHAR(50)  NOT NULL,
    allow_failures BOOLEAN      NOT NULL DEFAULT FALSE,
    start_time     TIMESTAMP,
    end_time       TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
