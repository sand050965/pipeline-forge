package edu.northeastern.cs7580.cicd.executionservice.dto;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Message DTO published to RabbitMQ when a pipeline execution is enqueued.
 *
 * <p>The HTTP endpoint creates a {@code pipeline_runs} record with
 * {@code status=PENDING}, pre-creates all {@code stage_runs} and
 * {@code job_runs} rows, and then publishes this message to
 * {@code pipeline.execution.queue}. The {@link
 * edu.northeastern.cs7580.cicd.executionservice.consumer.PipelineExecutionConsumer}
 * deserializes this message and performs the actual pipeline execution.</p>
 *
 * <h2>Field Notes</h2>
 * <ul>
 *   <li>{@code runId} — surrogate PK of the {@code pipeline_runs} row; used by
 *       the consumer to update run status as execution progresses.</li>
 *   <li>{@code runNo} — trigger-assigned per-pipeline sequence number (e.g. 5
 *       for the fifth run of this pipeline); returned to the CLI immediately in
 *       the 202 response so users can reference it with {@code cicd status}.</li>
 *   <li>{@code idMap} — pre-created stage/job DB ids, shaped as
 *       {@code stageName → { "__stageRunId__" → id, jobName → id, … }}.
 *       The consumer uses these ids to UPDATE existing rows rather than
 *       INSERT new ones, preventing duplicate records.</li>
 *   <li>{@code gitMetadata} — used by the consumer to re-clone the repository
 *       workspace, since the workspace is cleaned up before the 202 is returned.</li>
 *   <li>{@code pipelineFilePath} — relative path (e.g. {@code .pipelines/ci.yaml})
 *       used by the consumer to re-parse the pipeline YAML after cloning.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineExecutionMessage {

  /**
   * Surrogate primary key of the {@code pipeline_runs} row created with
   * {@code status=PENDING} before this message was published.
   *
   * <p>The consumer uses this id to transition the run through
   * {@code PENDING → RUNNING → SUCCESS/FAILED}.
   */
  private Long runId;

  /**
   * Per-pipeline sequential run number assigned by the PostgreSQL trigger on
   * INSERT (e.g. {@code 5} for the fifth run of this pipeline).
   *
   * <p>This value is included in the 202 response so the CLI can display
   * {@code "CI run: 5"} without waiting for execution to complete.
   */
  private int runNo;

  /**
   * Logical name of the pipeline (e.g. {@code "CI"}, {@code "release-prod"}).
   */
  private String pipelineName;

  /**
   * Relative path to the pipeline YAML file from the repository root
   * (e.g. {@code ".pipelines/ci.yaml"}).
   *
   * <p>The consumer resolves this path against the freshly-cloned workspace
   * to re-parse the {@link edu.northeastern.cs7580.cicd.pipelinelib.model.ExecutionPlan}.
   */
  private String pipelineFilePath;

  /**
   * Git repository metadata required to clone the workspace on the consumer side.
   *
   * <p>The HTTP endpoint cleans up its workspace before returning 202, so the
   * consumer must clone the repository again using this metadata.
   */
  private GitMetadata gitMetadata;

  /**
   * Pre-created stage and job DB ids, produced by
   * {@link edu.northeastern.cs7580.cicd.executionservice.service.ExecutionPersistenceService
   * #preCreateStagesAndJobs}.
   *
   * <p>Structure: {@code stageName → { "__stageRunId__" → stageId, jobName → jobId, … }}.
   * The consumer passes this map directly to the execution logic so it can UPDATE
   * existing {@code PENDING} rows instead of inserting new ones.
   */
  private Map<String, Map<String, Long>> idMap;
}
