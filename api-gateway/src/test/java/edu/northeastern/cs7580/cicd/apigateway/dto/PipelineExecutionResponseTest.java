package edu.northeastern.cs7580.cicd.apigateway.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


class PipelineExecutionResponseTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
  }

  @Test
  void builder_CreatesValidObject() {
    PipelineExecutionResponse response = PipelineExecutionResponse.builder()
        .executionId("exec-123")
        .pipelineName("default")
        .runNumber(1)
        .status("QUEUED")
        .message("Pipeline queued for execution")
        .build();

    assertEquals("exec-123", response.getExecutionId());
    assertEquals("default", response.getPipelineName());
    assertEquals(1, response.getRunNumber());
    assertEquals("QUEUED", response.getStatus());
    assertEquals("Pipeline queued for execution", response.getMessage());
  }

  @Test
  void noArgsConstructor_CreatesEmptyObject() {
    PipelineExecutionResponse response = new PipelineExecutionResponse();

    assertNull(response.getExecutionId());
    assertNull(response.getPipelineName());
    assertNull(response.getRunNumber());
    assertNull(response.getStatus());
    assertNull(response.getMessage());
  }

  @Test
  void allArgsConstructor_SetsAllFields() {
    PipelineExecutionResponse response = new PipelineExecutionResponse(
        "exec-456",
        "build-pipeline",
        5,
        "RUNNING",
        "Pipeline is executing"
    );

    assertEquals("exec-456", response.getExecutionId());
    assertEquals("build-pipeline", response.getPipelineName());
    assertEquals(5, response.getRunNumber());
    assertEquals("RUNNING", response.getStatus());
    assertEquals("Pipeline is executing", response.getMessage());
  }

  @Test
  void serialization_ProducesCorrectJson() throws Exception {
    PipelineExecutionResponse response = createSuccessResponse();

    String json = objectMapper.writeValueAsString(response);

    assertTrue(json.contains("\"executionId\":\"exec-123\""));
    assertTrue(json.contains("\"pipelineName\":\"default\""));
    assertTrue(json.contains("\"runNumber\":1"));
    assertTrue(json.contains("\"status\":\"SUCCESS\""));
    assertTrue(json.contains("\"message\":\"Pipeline completed successfully\""));
  }

  @Test
  void deserialization_CreatesValidObject() throws Exception {
    String json = """
        {
          "executionId": "exec-789",
          "pipelineName": "test-pipeline",
          "runNumber": 10,
          "status": "FAILED",
          "message": "Pipeline execution failed"
        }
        """;

    PipelineExecutionResponse response = objectMapper.readValue(json,
        PipelineExecutionResponse.class);

    assertEquals("exec-789", response.getExecutionId());
    assertEquals("test-pipeline", response.getPipelineName());
    assertEquals(10, response.getRunNumber());
    assertEquals("FAILED", response.getStatus());
    assertEquals("Pipeline execution failed", response.getMessage());
  }

  @Test
  void serialization_WithNullFields_HandlesGracefully() throws Exception {
    PipelineExecutionResponse response = PipelineExecutionResponse.builder()
        .executionId("exec-999")
        .pipelineName("partial")
        .build();

    String json = objectMapper.writeValueAsString(response);

    assertTrue(json.contains("\"executionId\":\"exec-999\""));
    assertTrue(json.contains("\"pipelineName\":\"partial\""));
  }

  @Test
  void equals_SameValues_ReturnsTrue() {
    PipelineExecutionResponse response1 = createSuccessResponse();
    PipelineExecutionResponse response2 = createSuccessResponse();

    assertEquals(response1, response2);
    assertEquals(response1.hashCode(), response2.hashCode());
  }

  @Test
  void equals_DifferentExecutionId_ReturnsFalse() {
    PipelineExecutionResponse response1 = createSuccessResponse();
    PipelineExecutionResponse response2 = PipelineExecutionResponse.builder()
        .executionId("different-id")
        .pipelineName("default")
        .runNumber(1)
        .status("SUCCESS")
        .message("Pipeline completed successfully")
        .build();

    assertNotEquals(response1, response2);
  }

  @Test
  void equals_DifferentStatus_ReturnsFalse() {
    PipelineExecutionResponse response1 = createSuccessResponse();
    PipelineExecutionResponse response2 = PipelineExecutionResponse.builder()
        .executionId("exec-123")
        .pipelineName("default")
        .runNumber(1)
        .status("FAILED")
        .message("Pipeline completed successfully")
        .build();

    assertNotEquals(response1, response2);
  }

  @Test
  void toString_ContainsAllFields() {
    PipelineExecutionResponse response = createSuccessResponse();

    String toString = response.toString();

    assertTrue(toString.contains("executionId=exec-123"));
    assertTrue(toString.contains("pipelineName=default"));
    assertTrue(toString.contains("runNumber=1"));
    assertTrue(toString.contains("status=SUCCESS"));
    assertTrue(toString.contains("message=Pipeline completed successfully"));
  }

  @Test
  void setters_ModifyFields() {
    PipelineExecutionResponse response = new PipelineExecutionResponse();

    response.setExecutionId("new-exec-id");
    response.setPipelineName("new-pipeline");
    response.setRunNumber(99);
    response.setStatus("RUNNING");
    response.setMessage("Updated message");

    assertEquals("new-exec-id", response.getExecutionId());
    assertEquals("new-pipeline", response.getPipelineName());
    assertEquals(99, response.getRunNumber());
    assertEquals("RUNNING", response.getStatus());
    assertEquals("Updated message", response.getMessage());
  }

  @Test
  void builder_WithPartialFields_CreatesObject() {
    PipelineExecutionResponse response = PipelineExecutionResponse.builder()
        .executionId("exec-partial")
        .pipelineName("partial-pipeline")
        .status("QUEUED")
        .build();

    assertEquals("exec-partial", response.getExecutionId());
    assertEquals("partial-pipeline", response.getPipelineName());
    assertNull(response.getRunNumber());
    assertEquals("QUEUED", response.getStatus());
    assertNull(response.getMessage());
  }

  @Test
  void builder_AllStatusValues_Work() {
    String[] statuses = {"QUEUED", "RUNNING", "SUCCESS", "FAILED"};

    for (String status : statuses) {
      PipelineExecutionResponse response = PipelineExecutionResponse.builder()
          .executionId("exec-" + status)
          .pipelineName("test")
          .runNumber(1)
          .status(status)
          .build();

      assertEquals(status, response.getStatus());
    }
  }

  @Test
  void serialization_RoundTrip_PreservesData() throws Exception {
    PipelineExecutionResponse original = createSuccessResponse();

    String json = objectMapper.writeValueAsString(original);
    PipelineExecutionResponse deserialized = objectMapper.readValue(json,
        PipelineExecutionResponse.class);

    assertEquals(original, deserialized);
  }

  private PipelineExecutionResponse createSuccessResponse() {
    return PipelineExecutionResponse.builder()
        .executionId("exec-123")
        .pipelineName("default")
        .runNumber(1)
        .status("SUCCESS")
        .message("Pipeline completed successfully")
        .build();
  }

  private GitMetadata createValidGitMetadata() {
    return GitMetadata.builder()
        .repositoryUrl("git@github.com:user/repo.git")
        .branch("main")
        .commitHash("abc123def456")
        .build();
  }

  private PipelineExecutionRequest createValidRequest() {
    return PipelineExecutionRequest.builder()
        .pipelineName("default")
        .pipelineFilePath(".pipelines/default.yaml")
        .gitMetadata(createValidGitMetadata())
        .build();
  }
}