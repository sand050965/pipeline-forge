-- ---------------------------------------------------------------------------
-- Add allow_failures flag to job_runs
-- ---------------------------------------------------------------------------
ALTER TABLE job_runs
    ADD COLUMN allow_failures BOOLEAN NOT NULL DEFAULT FALSE;
