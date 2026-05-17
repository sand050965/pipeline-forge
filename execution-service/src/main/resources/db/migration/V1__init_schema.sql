-- =============================================================================
-- Pipeline Execution Data Schema
-- Migration: V1__init_schema.sql
--
-- Four-level hierarchy:
--   pipelines (definition) → pipeline_runs → stage_runs → job_runs
--
-- Notes:
--   - All timestamps use TIMESTAMPTZ (TIMESTAMP WITH TIME ZONE)
--   - status is stored as VARCHAR(50); validation is enforced at the application layer
--   - repo_id is SHA-256 hash of the git_repo URL (computed by application)
--   - run_no is scoped per pipeline (not globally)
-- =============================================================================


-- ---------------------------------------------------------------------------
-- TABLE: pipelines
-- Defines a unique pipeline identity: one row per (repo + pipeline name).
-- The application hashes the git_repo URL into repo_id before inserting.
--
-- Columns:
--   id            – surrogate PK
--   repo_id       – SHA-256 hash of git_repo URL (64 hex chars)
--   pipeline_name – logical name of the pipeline (e.g. "deploy-prod")
--   git_repo      – original repo URL, stored for human readability
--   created_at    – record insertion time
--   updated_at    – last update time
-- ---------------------------------------------------------------------------
CREATE TABLE pipelines
(
    id            BIGSERIAL PRIMARY KEY,
    repo_id       VARCHAR(64)   NOT NULL,
    pipeline_name VARCHAR(255)  NOT NULL,
    git_repo      VARCHAR(1024) NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    -- repo + pipeline name uniquely identifies a pipeline across all users/repos
    CONSTRAINT uq_repo_pipeline UNIQUE (repo_id, pipeline_name)
);

CREATE INDEX idx_pipelines_repo_id ON pipelines (repo_id);
CREATE INDEX idx_pipelines_pipeline_name ON pipelines (pipeline_name);


-- ---------------------------------------------------------------------------
-- TABLE: pipeline_runs
-- One row per execution of a pipeline.
--
-- Columns:
--   id            – surrogate PK
--   pipeline_id   – FK → pipelines.id
--   run_no        – monotonically increasing per pipeline (see trigger)
--   status        – current lifecycle state as a VARCHAR(50) string
--                   (e.g. 'PENDING', 'RUNNING', 'SUCCESS', 'FAILED');
--                   valid values are enforced by the application layer
--   git_branch    – branch that triggered the run
--   git_hash      – full 40-char commit SHA
--   start_time    – when the pipeline started
--   end_time      – when the pipeline finished (NULL while in-progress)
--   created_at    – record insertion time
--   updated_at    – last update time
-- ---------------------------------------------------------------------------
CREATE TABLE pipeline_runs
(
    id          BIGSERIAL PRIMARY KEY,
    pipeline_id BIGINT       NOT NULL
        REFERENCES pipelines (id) ON DELETE CASCADE,
    run_no      INTEGER      NOT NULL,
    status      VARCHAR(50)  NOT NULL DEFAULT 'RUNNING',
    git_branch  VARCHAR(255) NOT NULL,
    git_hash    VARCHAR(40)  NOT NULL,
    start_time  TIMESTAMPTZ  NOT NULL,
    end_time    TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_pipeline_run_no UNIQUE (pipeline_id, run_no),

    CONSTRAINT chk_pipeline_end_after_start
        CHECK (end_time IS NULL OR end_time >= start_time)
);

CREATE INDEX idx_pipeline_runs_pipeline_id ON pipeline_runs (pipeline_id);
CREATE INDEX idx_pipeline_runs_status ON pipeline_runs (status);
CREATE INDEX idx_pipeline_runs_start_time ON pipeline_runs (start_time DESC);
CREATE INDEX idx_pipeline_runs_git_hash ON pipeline_runs (git_hash);


-- ---------------------------------------------------------------------------
-- TRIGGER: auto-increment run_no per pipeline_id
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION trg_pipeline_runs_set_run_no()
    RETURNS TRIGGER
    LANGUAGE plpgsql AS
$$
BEGIN
    -- Lock the latest row for this pipeline to prevent race conditions
    PERFORM id
    FROM pipeline_runs
    WHERE pipeline_id = NEW.pipeline_id
    ORDER BY run_no DESC
    LIMIT 1 FOR UPDATE;

    SELECT COALESCE(MAX(run_no), 0) + 1
    INTO NEW.run_no
    FROM pipeline_runs
    WHERE pipeline_id = NEW.pipeline_id;

    RETURN NEW;
END;
$$;

CREATE TRIGGER set_pipeline_run_no
    BEFORE INSERT
    ON pipeline_runs
    FOR EACH ROW
EXECUTE FUNCTION trg_pipeline_runs_set_run_no();


-- ---------------------------------------------------------------------------
-- TABLE: stage_runs
-- One row per stage within a pipeline run.
--
-- Columns:
--   id               – surrogate PK
--   pipeline_run_id  – FK → pipeline_runs.id
--   stage_name       – name of the stage as defined in the pipeline file
--   status           – current lifecycle state as a VARCHAR(50) string;
--                      valid values are enforced by the application layer
--   start_time       – when the stage started
--   end_time         – when the stage finished (NULL while in-progress)
--   created_at       – record insertion time
--   updated_at       – last update time
-- ---------------------------------------------------------------------------
CREATE TABLE stage_runs
(
    id              BIGSERIAL PRIMARY KEY,
    pipeline_run_id BIGINT       NOT NULL
        REFERENCES pipeline_runs (id) ON DELETE CASCADE,
    stage_name      VARCHAR(255) NOT NULL,
    status          VARCHAR(50)  NOT NULL DEFAULT 'RUNNING',
    start_time      TIMESTAMPTZ  NOT NULL,
    end_time        TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_stage_end_after_start
        CHECK (end_time IS NULL OR end_time >= start_time)
);

CREATE INDEX idx_stage_runs_pipeline_run_id ON stage_runs (pipeline_run_id);
CREATE INDEX idx_stage_runs_status ON stage_runs (status);
CREATE INDEX idx_stage_runs_name ON stage_runs (stage_name);


-- ---------------------------------------------------------------------------
-- TABLE: job_runs
-- One row per job within a stage run.
--
-- Columns:
--   id            – surrogate PK
--   stage_run_id  – FK → stage_runs.id
--   job_name      – name of the job as defined in the pipeline file
--   status        – current lifecycle state as a VARCHAR(50) string;
--                   valid values are enforced by the application layer
--   start_time    – when the job started
--   end_time      – when the job finished (NULL while in-progress)
--   created_at    – record insertion time
--   updated_at    – last update time
-- ---------------------------------------------------------------------------
CREATE TABLE job_runs
(
    id           BIGSERIAL PRIMARY KEY,
    stage_run_id BIGINT       NOT NULL
        REFERENCES stage_runs (id) ON DELETE CASCADE,
    job_name     VARCHAR(255) NOT NULL,
    status       VARCHAR(50)  NOT NULL DEFAULT 'RUNNING',
    start_time   TIMESTAMPTZ  NOT NULL,
    end_time     TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_job_end_after_start
        CHECK (end_time IS NULL OR end_time >= start_time)
);

CREATE INDEX idx_job_runs_stage_run_id ON job_runs (stage_run_id);
CREATE INDEX idx_job_runs_status ON job_runs (status);
CREATE INDEX idx_job_runs_name ON job_runs (job_name);


-- ---------------------------------------------------------------------------
-- TRIGGER: auto-update updated_at on every row UPDATE
-- A single shared function reused across all tables.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION trg_set_updated_at()
    RETURNS TRIGGER
    LANGUAGE plpgsql AS
$$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;

CREATE TRIGGER set_updated_at_pipelines
    BEFORE UPDATE
    ON pipelines
    FOR EACH ROW
EXECUTE FUNCTION trg_set_updated_at();

CREATE TRIGGER set_updated_at_pipeline_runs
    BEFORE UPDATE
    ON pipeline_runs
    FOR EACH ROW
EXECUTE FUNCTION trg_set_updated_at();

CREATE TRIGGER set_updated_at_stage_runs
    BEFORE UPDATE
    ON stage_runs
    FOR EACH ROW
EXECUTE FUNCTION trg_set_updated_at();

CREATE TRIGGER set_updated_at_job_runs
    BEFORE UPDATE
    ON job_runs
    FOR EACH ROW
EXECUTE FUNCTION trg_set_updated_at();