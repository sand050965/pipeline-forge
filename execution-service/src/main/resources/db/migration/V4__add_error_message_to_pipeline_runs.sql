-- Add optional error_message column to pipeline_runs.
-- Populated only when a run fails due to an internal consumer error
-- (i.e. not a job-level failure) so that the status sub-command can
-- surface a human-readable explanation to the user.
ALTER TABLE pipeline_runs
    ADD COLUMN error_message TEXT;
