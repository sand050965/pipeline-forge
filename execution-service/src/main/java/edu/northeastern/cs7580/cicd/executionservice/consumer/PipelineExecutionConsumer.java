package edu.northeastern.cs7580.cicd.executionservice.consumer;

import edu.northeastern.cs7580.cicd.executionservice.config.RabbitMqConfig;
import edu.northeastern.cs7580.cicd.executionservice.dto.PipelineExecutionMessage;
import edu.northeastern.cs7580.cicd.executionservice.service.ExecutionPersistenceService;
import edu.northeastern.cs7580.cicd.executionservice.service.ExecutionService;
import edu.northeastern.cs7580.cicd.executionservice.service.WorkspaceService;
import edu.northeastern.cs7580.cicd.pipelinelib.api.PipelineService;
import edu.northeastern.cs7580.cicd.pipelinelib.api.PipelineServiceFactory;
import edu.northeastern.cs7580.cicd.pipelinelib.exception.ValidationException;
import edu.northeastern.cs7580.cicd.pipelinelib.model.ExecutionPlan;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.nio.file.Path;
import org.apache.logging.log4j.internal.annotation.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ consumer that executes pipeline runs asynchronously.
 *
 * <p>Listens on {@link RabbitMqConfig#QUEUE_NAME} for {@link PipelineExecutionMessage}
 * objects published by the HTTP endpoint after validating and enqueuing a run request.
 * Each message represents one complete pipeline run to execute.</p>
 *
 * <h2>Execution Flow</h2>
 * <ol>
 *   <li>Mark the {@code pipeline_runs} row as {@code RUNNING}.</li>
 *   <li>Clone the repository workspace using the git metadata in the message.</li>
 *   <li>Re-parse the pipeline YAML to reconstruct the
 *       {@link ExecutionPlan}.</li>
 *   <li>Execute all stages and jobs sequentially, writing per-job statuses to
 *       the DB as each job completes (via
 *       {@link ExecutionService#executeFromMessage}).</li>
 *   <li>Clean up the workspace regardless of outcome.</li>
 * </ol>
 *
 * <h2>Error Handling</h2>
 *
 * <p>If an unexpected exception occurs at any point, the run is marked
 * {@code FAILED} with an internal error message so users can query the
 * status via {@code cicd status} and know to file an issue. The run is
 * never left permanently {@code PENDING} or {@code RUNNING}.</p>
 *
 * <h2>Threading</h2>
 *
 * <p>This listener runs on a RabbitMQ listener thread (concurrency configured
 * in {@code application.yml}). Blocking calls are safe here — unlike the
 * WebFlux event loop, listener threads are not shared reactor threads.</p>
 *
 * @see RabbitMqConfig
 * @see ExecutionService#executeFromMessage
 */
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "ExecutionService, ExecutionPersistenceService, WorkspaceService, and Tracer "
        + "are Spring-managed singleton beans; storing the references is intentional and safe."
)
@Component
public class PipelineExecutionConsumer {

  private static final Logger log = LoggerFactory.getLogger(PipelineExecutionConsumer.class);

  private static final String INTERNAL_ERROR_MESSAGE =
      "Internal error: your request was valid but the system encountered an unexpected error. "
          + "Please file an issue.";

  private final ExecutionService executionService;
  private final ExecutionPersistenceService persistenceService;
  private final WorkspaceService workspaceService;
  private final PipelineService pipelineService;
  private final Tracer tracer;

  /**
   * Constructs a {@code PipelineExecutionConsumer} with the required collaborators.
   *
   * @param executionService   orchestrates sequential pipeline execution
   * @param persistenceService writes and updates execution records in PostgreSQL
   * @param workspaceService   clones and cleans up Git workspaces
   * @param tracer             Micrometer tracer for creating the root pipeline span
   */
  @Autowired
  public PipelineExecutionConsumer(ExecutionService executionService,
                                   ExecutionPersistenceService persistenceService,
                                   WorkspaceService workspaceService,
                                   Tracer tracer) {
    this.executionService = executionService;
    this.persistenceService = persistenceService;
    this.workspaceService = workspaceService;
    this.pipelineService = PipelineServiceFactory.create();
    this.tracer = tracer;
  }

  /**
   * Package-protected constructor for unit testing, allowing mock collaborators.
   *
   * @param executionService   orchestrates sequential pipeline execution
   * @param persistenceService writes and updates execution records in PostgreSQL
   * @param workspaceService   clones and cleans up Git workspaces
   * @param pipelineService    pipeline parsing service (typically a test double)
   * @param tracer             Micrometer tracer (typically a test double)
   */
  PipelineExecutionConsumer(ExecutionService executionService,
                            ExecutionPersistenceService persistenceService,
                            WorkspaceService workspaceService,
                            PipelineService pipelineService,
                            Tracer tracer) {
    this.executionService = executionService;
    this.persistenceService = persistenceService;
    this.workspaceService = workspaceService;
    this.pipelineService = pipelineService;
    this.tracer = tracer;
  }

  /**
   * Picks up a pipeline execution message from the queue and runs the pipeline.
   *
   * <p>The workspace is always cleaned up in a {@code finally} block, regardless
   * of whether execution succeeds or fails. If an unexpected exception escapes
   * the execution logic, the run is marked {@code FAILED} before the method
   * returns so no run is left permanently {@code PENDING} or {@code RUNNING}.</p>
   *
   * @param message the pipeline execution message published by the HTTP endpoint
   */
  @RabbitListener(queues = RabbitMqConfig.QUEUE_NAME)
  public void consume(PipelineExecutionMessage message) {
    log.info("Picked up pipeline run: runId={}, runNo={}, pipeline={}",
        message.getRunId(), message.getRunNo(), message.getPipelineName());

    persistenceService.markPipelineRunRunning(message.getRunId()).block();

    Span rootSpan = tracer.nextSpan()
        .name("pipeline.execute")
        .tag("pipeline.name", message.getPipelineName())
        .tag("run.number", String.valueOf(message.getRunNo()));

    try (Tracer.SpanInScope scope = tracer.withSpan(rootSpan.start())) {
      persistenceService.updateTraceId(message.getRunId(), rootSpan.context().traceId()).block();

      MDC.put("pipeline", message.getPipelineName());
      MDC.put("run_no", String.valueOf(message.getRunNo()));
      MDC.put("branch", message.getGitMetadata().getBranch());
      MDC.put("commit", message.getGitMetadata().getCommitHash());

      Path workspacePath = null;
      try {
        workspacePath = workspaceService.prepareWorkspace(message.getGitMetadata());
        log.debug("Cloned workspace for runId={}: {}", message.getRunId(), workspacePath);

        ExecutionPlan plan = pipelineService.createExecutionPlan(
            workspacePath.resolve(message.getPipelineFilePath()));

        executionService.executeFromMessage(
            plan, workspacePath, message.getRunId(), message.getIdMap(),
            message.getPipelineName(), message.getRunNo());

        log.info("Completed pipeline run: runId={}, pipeline={}",
            message.getRunId(), message.getPipelineName());

      } catch (ValidationException e) {
        // YAML re-parse failed — should not happen since the HTTP endpoint already
        // validated, but guard against workspace/file corruption edge cases.
        log.error("Pipeline YAML re-parse failed for runId={}: {}",
            message.getRunId(), e.getMessage());
        persistenceService.markPipelineRunFailed(message.getRunId(), INTERNAL_ERROR_MESSAGE)
            .block();

      } catch (Exception e) {
        log.error("Unexpected error executing pipeline runId={}: {}",
            message.getRunId(), e.getMessage(), e);
        persistenceService.markPipelineRunFailed(message.getRunId(), INTERNAL_ERROR_MESSAGE)
            .block();

      } finally {
        MDC.remove("pipeline");
        MDC.remove("run_no");
        MDC.remove("branch");
        MDC.remove("commit");
        if (workspacePath != null) {
          workspaceService.cleanupWorkspace(workspacePath);
        }
      }
    } finally {
      rootSpan.end();
    }
  }
}
