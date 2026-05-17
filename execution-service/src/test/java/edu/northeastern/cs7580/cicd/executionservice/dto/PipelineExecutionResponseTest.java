package edu.northeastern.cs7580.cicd.executionservice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import edu.northeastern.cs7580.cicd.executionservice.model.ExecutionStatus;
import org.junit.jupiter.api.Test;

class PipelineExecutionResponseTest {

  @Test
  void builder_shouldSetAllFields_successResponse() {
    PipelineExecutionResponse response = PipelineExecutionResponse.builder()
        .executionId("exec-123")
        .pipelineName("test-pipeline")
        .runNumber(42)
        .status(ExecutionStatus.SUCCESS)
        .message("All jobs passed")
        .build();

    assertThat(response.getExecutionId()).isEqualTo("exec-123");
    assertThat(response.getPipelineName()).isEqualTo("test-pipeline");
    assertThat(response.getRunNumber()).isEqualTo(42);
    assertThat(response.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(response.getMessage()).isEqualTo("All jobs passed");
  }

  @Test
  void builder_shouldSetAllFields_failureResponse() {
    PipelineExecutionResponse response = PipelineExecutionResponse.builder()
        .executionId("exec-456")
        .pipelineName("build-pipeline")
        .runNumber(10)
        .status(ExecutionStatus.FAILED)
        .message("Pipeline failed at job 'test'")
        .build();

    assertThat(response.getStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(response.getMessage()).contains("failed at job 'test'");
  }

  @Test
  void builder_shouldSetAllFields_validationFailedResponse() {
    PipelineExecutionResponse response = PipelineExecutionResponse.builder()
        .executionId("exec-789")
        .pipelineName("invalid-pipeline")
        .runNumber(0)
        .status(ExecutionStatus.VALIDATION_FAILED)
        .message("Pipeline validation failed: Circular dependency detected")
        .build();

    assertThat(response.getStatus()).isEqualTo(ExecutionStatus.VALIDATION_FAILED);
    assertThat(response.getMessage()).contains("Circular dependency");
  }

  @Test
  void noArgsConstructor_shouldCreateEmptyEntity() {
    PipelineExecutionResponse response = new PipelineExecutionResponse();

    assertThat(response.getExecutionId()).isNull();
    assertThat(response.getPipelineName()).isNull();
    assertThat(response.getRunNumber()).isNull();
    assertThat(response.getStatus()).isNull();
    assertThat(response.getMessage()).isNull();
  }

  @Test
  void allArgsConstructor_shouldSetAllFields() {
    PipelineExecutionResponse response = new PipelineExecutionResponse(
        "exec-999",
        "deploy-pipeline",
        5,
        ExecutionStatus.SUCCESS,
        "Deployment successful"
    );

    assertThat(response.getExecutionId()).isEqualTo("exec-999");
    assertThat(response.getPipelineName()).isEqualTo("deploy-pipeline");
    assertThat(response.getRunNumber()).isEqualTo(5);
    assertThat(response.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(response.getMessage()).isEqualTo("Deployment successful");
  }

  @Test
  void setter_shouldUpdateAllFields() {
    PipelineExecutionResponse response = new PipelineExecutionResponse();

    response.setExecutionId("new-exec-id");
    response.setPipelineName("new-pipeline");
    response.setRunNumber(99);
    response.setStatus(ExecutionStatus.FAILED);
    response.setMessage("In progress");

    assertThat(response.getExecutionId()).isEqualTo("new-exec-id");
    assertThat(response.getPipelineName()).isEqualTo("new-pipeline");
    assertThat(response.getRunNumber()).isEqualTo(99);
    assertThat(response.getStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(response.getMessage()).isEqualTo("In progress");
  }

  @Test
  void equals_shouldBeEqualForSameValues() {
    PipelineExecutionResponse response1 = PipelineExecutionResponse.builder()
        .executionId("exec-123").pipelineName("test").runNumber(1)
        .status(ExecutionStatus.SUCCESS).message("Done")
        .build();

    PipelineExecutionResponse response2 = PipelineExecutionResponse.builder()
        .executionId("exec-123").pipelineName("test").runNumber(1)
        .status(ExecutionStatus.SUCCESS).message("Done")
        .build();

    assertThat(response1).isEqualTo(response2);
    assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
  }

  @Test
  void equals_shouldNotBeEqualForDifferentStatus() {
    PipelineExecutionResponse response1 = PipelineExecutionResponse.builder()
        .executionId("exec-1").status(ExecutionStatus.SUCCESS).build();

    PipelineExecutionResponse response2 = PipelineExecutionResponse.builder()
        .executionId("exec-1").status(ExecutionStatus.FAILED).build();

    assertThat(response1).isNotEqualTo(response2);
  }

  @Test
  void toString_shouldContainKeyFields() {
    PipelineExecutionResponse response = PipelineExecutionResponse.builder()
        .executionId("exec-123")
        .pipelineName("test-pipeline")
        .runNumber(42)
        .status(ExecutionStatus.SUCCESS)
        .message("All done")
        .build();

    String result = response.toString();

    assertThat(result).contains("exec-123");
    assertThat(result).contains("test-pipeline");
    assertThat(result).contains("SUCCESS");
  }

  @Test
  void builder_nullRunNumber_shouldBeAllowed() {
    PipelineExecutionResponse response = PipelineExecutionResponse.builder()
        .executionId("exec-123")
        .pipelineName("test")
        .runNumber(null)
        .status(ExecutionStatus.VALIDATION_FAILED)
        .message("Validation error")
        .build();

    assertThat(response.getRunNumber()).isNull();
  }
}