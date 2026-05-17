package edu.northeastern.cs7580.cicd.executionservice.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.northeastern.cs7580.cicd.executionservice.dto.GitMetadata;
import edu.northeastern.cs7580.cicd.executionservice.dto.PipelineExecutionMessage;
import edu.northeastern.cs7580.cicd.executionservice.service.ExecutionPersistenceService;
import edu.northeastern.cs7580.cicd.executionservice.service.ExecutionService;
import edu.northeastern.cs7580.cicd.executionservice.service.WorkspaceService;
import edu.northeastern.cs7580.cicd.pipelinelib.api.PipelineService;
import edu.northeastern.cs7580.cicd.pipelinelib.exception.ValidationException;
import edu.northeastern.cs7580.cicd.pipelinelib.model.ExecutionPlan;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PipelineExecutionConsumerTest {

  @Mock private ExecutionService executionService;
  @Mock private ExecutionPersistenceService persistenceService;
  @Mock private WorkspaceService workspaceService;
  @Mock private PipelineService pipelineService;
  @Mock private Tracer tracer;
  @Mock private Span span;
  @Mock private Tracer.SpanInScope spanInScope;
  @Mock private TraceContext traceContext;
  @Mock private ExecutionPlan plan;

  private static final String FAKE_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

  private PipelineExecutionConsumer consumer;

  private static final Path WORKSPACE = Path.of("/tmp/workspace");
  private static final Long RUN_ID = 42L;
  private static final int RUN_NO = 5;
  private static final String PIPELINE_FILE = ".pipelines/ci.yaml";

  private static final GitMetadata GIT = GitMetadata.builder()
      .repositoryUrl("https://github.com/org/repo.git")
      .branch("main")
      .commitHash("abc123")
      .build();

  private static final Map<String, Map<String, Long>> ID_MAP = Map.of(
      "build", Map.of("__stageRunId__", 1L, "compile", 2L)
  );

  private static final PipelineExecutionMessage MESSAGE = PipelineExecutionMessage.builder()
      .runId(RUN_ID)
      .runNo(RUN_NO)
      .pipelineName("CI")
      .pipelineFilePath(PIPELINE_FILE)
      .gitMetadata(GIT)
      .idMap(ID_MAP)
      .build();

  @BeforeEach
  void setUp() {
    consumer = new PipelineExecutionConsumer(
        executionService, persistenceService, workspaceService, pipelineService, tracer);

    // Default happy-path stubs
    when(persistenceService.markPipelineRunRunning(anyLong())).thenReturn(Mono.empty());
    when(persistenceService.markPipelineRunFailed(anyLong(), anyString())).thenReturn(Mono.empty());
    when(persistenceService.updateTraceId(anyLong(), anyString())).thenReturn(Mono.empty());
    when(workspaceService.prepareWorkspace(any())).thenReturn(WORKSPACE);
    when(pipelineService.createExecutionPlan(any())).thenReturn(plan);

    // Tracer stubs
    when(tracer.nextSpan()).thenReturn(span);
    when(span.name(anyString())).thenReturn(span);
    when(span.tag(anyString(), anyString())).thenReturn(span);
    when(span.start()).thenReturn(span);
    when(tracer.withSpan(span)).thenReturn(spanInScope);
    when(span.context()).thenReturn(traceContext);
    when(traceContext.traceId()).thenReturn(FAKE_TRACE_ID);
  }

  // -------------------------------------------------------------------------
  // Happy path
  // -------------------------------------------------------------------------

  @Test
  void consume_successfulRun_marksRunningThenExecutes() {
    consumer.consume(MESSAGE);

    verify(persistenceService).markPipelineRunRunning(RUN_ID);
    verify(executionService).executeFromMessage(plan, WORKSPACE, RUN_ID, ID_MAP, "CI", RUN_NO);
  }

  @Test
  void consume_successfulRun_cleansUpWorkspace() {
    consumer.consume(MESSAGE);

    verify(workspaceService).cleanupWorkspace(WORKSPACE);
  }

  @Test
  void consume_successfulRun_doesNotCallMarkFailed() {
    consumer.consume(MESSAGE);

    verify(persistenceService, never()).markPipelineRunFailed(anyLong(), anyString());
  }

  @Test
  void consume_successfulRun_parsesYamlAtCorrectPath() {
    consumer.consume(MESSAGE);

    verify(pipelineService).createExecutionPlan(WORKSPACE.resolve(PIPELINE_FILE));
  }

  // -------------------------------------------------------------------------
  // ValidationException on re-parse
  // -------------------------------------------------------------------------

  @Test
  void consume_yamlReParseFails_marksRunFailed() {
    when(pipelineService.createExecutionPlan(any()))
        .thenThrow(new ValidationException("missing field"));

    consumer.consume(MESSAGE);

    verify(persistenceService).markPipelineRunFailed(eq(RUN_ID), contains("Internal error"));
  }

  @Test
  void consume_yamlReParseFails_doesNotCallExecute() {
    when(pipelineService.createExecutionPlan(any()))
        .thenThrow(new ValidationException("missing field"));

    consumer.consume(MESSAGE);

    verify(executionService, never()).executeFromMessage(
        any(), any(), anyLong(), any(), anyString(), anyInt());
  }

  @Test
  void consume_yamlReParseFails_stillCleansUpWorkspace() {
    when(pipelineService.createExecutionPlan(any()))
        .thenThrow(new ValidationException("missing field"));

    consumer.consume(MESSAGE);

    verify(workspaceService).cleanupWorkspace(WORKSPACE);
  }

  // -------------------------------------------------------------------------
  // Unexpected exception during execution
  // -------------------------------------------------------------------------

  @Test
  void consume_executeFromMessageThrows_marksRunFailed() {
    doThrow(new RuntimeException("Docker daemon unreachable"))
        .when(executionService).executeFromMessage(
            any(), any(), anyLong(), any(), anyString(), anyInt());

    consumer.consume(MESSAGE);

    verify(persistenceService).markPipelineRunFailed(eq(RUN_ID), contains("Internal error"));
  }

  @Test
  void consume_executeFromMessageThrows_stillCleansUpWorkspace() {
    doThrow(new RuntimeException("OOM"))
        .when(executionService).executeFromMessage(
            any(), any(), anyLong(), any(), anyString(), anyInt());

    consumer.consume(MESSAGE);

    verify(workspaceService).cleanupWorkspace(WORKSPACE);
  }

  // -------------------------------------------------------------------------
  // Workspace clone fails
  // -------------------------------------------------------------------------

  @Test
  void consume_workspaceCloneFails_marksRunFailed() {
    when(workspaceService.prepareWorkspace(any()))
        .thenThrow(new RuntimeException("Git clone failed"));

    consumer.consume(MESSAGE);

    verify(persistenceService).markPipelineRunFailed(eq(RUN_ID), contains("Internal error"));
  }

  @Test
  void consume_workspaceCloneFails_doesNotCallCleanup() {
    when(workspaceService.prepareWorkspace(any()))
        .thenThrow(new RuntimeException("Git clone failed"));

    consumer.consume(MESSAGE);

    // workspacePath is null when clone fails — cleanup must not be called
    verify(workspaceService, never()).cleanupWorkspace(any());
  }

  @Test
  void consume_workspaceCloneFails_doesNotCallExecute() {
    when(workspaceService.prepareWorkspace(any()))
        .thenThrow(new RuntimeException("Git clone failed"));

    consumer.consume(MESSAGE);

    verify(executionService, never()).executeFromMessage(
        any(), any(), anyLong(), any(), anyString(), anyInt());
  }

  // -------------------------------------------------------------------------
  // Distributed tracing — root span
  // -------------------------------------------------------------------------

  @Test
  void consume_createsRootSpanWithCorrectName() {
    consumer.consume(MESSAGE);

    verify(span).name("pipeline.execute");
  }

  @Test
  void consume_rootSpan_hasPipelineNameAttribute() {
    consumer.consume(MESSAGE);

    verify(span).tag("pipeline.name", "CI");
  }

  @Test
  void consume_rootSpan_hasRunNumberAttribute() {
    consumer.consume(MESSAGE);

    verify(span).tag("run.number", String.valueOf(RUN_NO));
  }

  @Test
  void consume_persistsOtelTraceIdToDb() {
    consumer.consume(MESSAGE);

    verify(persistenceService).updateTraceId(RUN_ID, FAKE_TRACE_ID);
  }

  @Test
  void consume_endsRootSpanAfterExecution() {
    consumer.consume(MESSAGE);

    verify(span).end();
  }

  @Test
  void consume_endsRootSpanEvenOnExecutionException() {
    doThrow(new RuntimeException("Docker daemon unreachable"))
        .when(executionService).executeFromMessage(
            any(), any(), anyLong(), any(), anyString(), anyInt());

    consumer.consume(MESSAGE);

    verify(span).end();
  }
}
