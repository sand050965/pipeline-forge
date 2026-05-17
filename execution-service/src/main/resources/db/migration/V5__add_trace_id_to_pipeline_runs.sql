-- Add optional trace_id column to pipeline_runs.
-- Stores the 32-character hex W3C trace ID assigned when the run was created
-- so operators can correlate a report result with the corresponding trace in Tempo.
ALTER TABLE pipeline_runs
    ADD COLUMN trace_id VARCHAR(32);
