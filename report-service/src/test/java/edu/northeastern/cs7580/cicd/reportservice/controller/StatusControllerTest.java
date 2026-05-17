package edu.northeastern.cs7580.cicd.reportservice.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import edu.northeastern.cs7580.cicd.reportservice.dto.StatusJobDto;
import edu.northeastern.cs7580.cicd.reportservice.dto.StatusResponse;
import edu.northeastern.cs7580.cicd.reportservice.dto.StatusStageDto;
import edu.northeastern.cs7580.cicd.reportservice.exception.ResourceNotFoundException;
import edu.northeastern.cs7580.cicd.reportservice.service.ReportService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for {@link StatusController}.
 *
 * <p>Verifies that the controller delegates correctly to {@link ReportService}
 * and maps responses to the expected HTTP status codes.
 */
@ExtendWith(MockitoExtension.class)
class StatusControllerTest {

  @Mock
  private ReportService reportService;

  @InjectMocks
  private StatusController statusController;

  private StatusResponse statusResponse;

  @BeforeEach
  void setUp() {
    statusResponse = StatusResponse.builder()
        .pipelineName("default")
        .runNo(1)
        .status("SUCCESS")
        .stages(List.of(
            StatusStageDto.builder()
                .name("build")
                .status("SUCCESS")
                .jobs(List.of(
                    StatusJobDto.builder()
                        .name("compile")
                        .status("SUCCESS")
                        .build()))
                .build()))
        .build();
  }

  @Test
  void getStatusByRepo_success_returns200() {
    when(reportService.getStatusByRepo("https://github.com/org/repo"))
        .thenReturn(Mono.just(statusResponse));

    StepVerifier.create(
        statusController.getStatusByRepo("https://github.com/org/repo"))
        .expectNextMatches(entity ->
            entity.getStatusCode() == HttpStatus.OK
                && "default".equals(entity.getBody().getPipelineName())
                && entity.getBody().getRunNo() == 1
                && entity.getBody().getStages().size() == 1)
        .verifyComplete();
  }

  @Test
  void getStatusByRepo_notFound_propagatesError() {
    when(reportService.getStatusByRepo(anyString()))
        .thenReturn(Mono.error(
            new ResourceNotFoundException("No pipeline found for repo")));

    StepVerifier.create(
        statusController.getStatusByRepo("https://github.com/org/repo"))
        .expectError(ResourceNotFoundException.class)
        .verify();
  }

  @Test
  void getStatusByRun_success_returns200() {
    when(reportService.getStatusByRun("default", 1))
        .thenReturn(Mono.just(statusResponse));

    StepVerifier.create(statusController.getStatusByRun("default", 1))
        .expectNextMatches(entity ->
            entity.getStatusCode() == HttpStatus.OK
                && "default".equals(entity.getBody().getPipelineName())
                && entity.getBody().getRunNo() == 1
                && entity.getBody().getStages().size() == 1
                && "build".equals(
                    entity.getBody().getStages().get(0).getName()))
        .verifyComplete();
  }

  @Test
  void getStatusByRun_notFound_propagatesError() {
    when(reportService.getStatusByRun(anyString(), anyInt()))
        .thenReturn(Mono.error(
            new ResourceNotFoundException("Run 99 not found")));

    StepVerifier.create(statusController.getStatusByRun("default", 99))
        .expectError(ResourceNotFoundException.class)
        .verify();
  }
}
