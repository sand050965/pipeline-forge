package edu.northeastern.cs7580.cicd.reportservice.service;

import edu.northeastern.cs7580.cicd.reportservice.dto.JobDetailResponse;
import edu.northeastern.cs7580.cicd.reportservice.dto.JobSummary;
import edu.northeastern.cs7580.cicd.reportservice.dto.PipelineReportResponse;
import edu.northeastern.cs7580.cicd.reportservice.dto.RunDetailResponse;
import edu.northeastern.cs7580.cicd.reportservice.dto.RunSummary;
import edu.northeastern.cs7580.cicd.reportservice.dto.StageDetailResponse;
import edu.northeastern.cs7580.cicd.reportservice.dto.StageSummary;
import edu.northeastern.cs7580.cicd.reportservice.dto.StatusJobDto;
import edu.northeastern.cs7580.cicd.reportservice.dto.StatusResponse;
import edu.northeastern.cs7580.cicd.reportservice.dto.StatusStageDto;
import edu.northeastern.cs7580.cicd.reportservice.entity.JobRun;
import edu.northeastern.cs7580.cicd.reportservice.entity.Pipeline;
import edu.northeastern.cs7580.cicd.reportservice.entity.PipelineRun;
import edu.northeastern.cs7580.cicd.reportservice.entity.StageRun;
import edu.northeastern.cs7580.cicd.reportservice.exception.ResourceNotFoundException;
import edu.northeastern.cs7580.cicd.reportservice.repository.JobRunRepository;
import edu.northeastern.cs7580.cicd.reportservice.repository.PipelineRepository;
import edu.northeastern.cs7580.cicd.reportservice.repository.PipelineRunRepository;
import edu.northeastern.cs7580.cicd.reportservice.repository.StageRunRepository;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Service layer for assembling pipeline execution report data from the
 * database into structured response DTOs.
 *
 * <p>This service is the core business logic component of the Report Service.
 * It queries the four database tables ({@code pipelines},
 * {@code pipeline_runs}, {@code stage_runs}, {@code job_runs}) via their
 * respective reactive repositories, and assembles the results into nested
 * DTO objects that match the project specification's report output format.
 *
 * <p>Each public method corresponds to one of the four report endpoints:
 * <ul>
 *   <li>{@link #getPipelineReport(String)} — all runs for a pipeline</li>
 *   <li>{@link #getRunReport(String, int)} — details of a specific run</li>
 *   <li>{@link #getStageReport(String, int, String)} — details of a
 *       specific stage in a run</li>
 *   <li>{@link #getJobReport(String, int, String, String)} — details of
 *       a specific job in a stage</li>
 * </ul>
 *
 * <p>All methods return {@link Mono} for non-blocking reactive execution.
 * When a requested resource is not found, a
 * {@link ResourceNotFoundException} is emitted, which the
 * {@link edu.northeastern.cs7580.cicd.reportservice.exception.GlobalExceptionHandler}
 * translates into an HTTP 404 response.
 */
@Service
public class ReportService {

  private static final Logger log =
      LoggerFactory.getLogger(ReportService.class);

  private final PipelineRepository pipelineRepository;
  private final PipelineRunRepository pipelineRunRepository;
  private final StageRunRepository stageRunRepository;
  private final JobRunRepository jobRunRepository;

  /**
   * Creates a new report service with the required repositories.
   *
   * @param pipelineRepository    repository for pipeline definition queries
   * @param pipelineRunRepository repository for pipeline run queries
   * @param stageRunRepository    repository for stage run queries
   * @param jobRunRepository      repository for job run queries
   */
  public ReportService(PipelineRepository pipelineRepository,
      PipelineRunRepository pipelineRunRepository,
      StageRunRepository stageRunRepository,
      JobRunRepository jobRunRepository) {
    this.pipelineRepository = pipelineRepository;
    this.pipelineRunRepository = pipelineRunRepository;
    this.stageRunRepository = stageRunRepository;
    this.jobRunRepository = jobRunRepository;
  }

  /**
   * Retrieves a report of all runs for the given pipeline.
   *
   * <p>Queries the {@code pipelines} table for the pipeline definition,
   * then fetches all runs from {@code pipeline_runs} and assembles them
   * into a {@link PipelineReportResponse}.
   *
   * @param pipelineName the pipeline name to query
   * @return a {@link Mono} containing the pipeline report response
   * @throws ResourceNotFoundException if no pipeline with the given name exists
   */
  public Mono<PipelineReportResponse> getPipelineReport(String pipelineName) {
    log.debug("Fetching report for pipeline: {}", pipelineName);

    return findPipeline(pipelineName)
        .flatMap(pipeline -> pipelineRunRepository
            .findByPipelineId(pipeline.getId())
            .map(run -> toRunSummary(run, pipeline))
            .collectList()
            .map(runs -> PipelineReportResponse.builder()
                .pipeline(PipelineReportResponse.PipelineData.builder()
                    .name(pipeline.getPipelineName())
                    .runs(runs)
                    .build())
                .build()));
  }

  /**
   * Retrieves a detailed report for a specific run of a pipeline,
   * including all stages in that run.
   *
   * @param pipelineName the pipeline name
   * @param runNo        the run number
   * @return a {@link Mono} containing the run detail response
   * @throws ResourceNotFoundException if the pipeline or run is not found
   */
  public Mono<RunDetailResponse> getRunReport(String pipelineName, int runNo) {
    log.debug("Fetching report for pipeline: {}, run: {}", pipelineName, runNo);

    return findPipeline(pipelineName)
        .flatMap(pipeline -> findPipelineRun(pipeline.getId(), runNo,
            pipelineName)
            .flatMap(run -> stageRunRepository
                .findByPipelineRunId(run.getId())
                .map(this::toStageSummary)
                .collectList()
                .map(stages -> RunDetailResponse.builder()
                    .pipeline(RunDetailResponse.PipelineRunData.builder()
                        .name(pipelineName)
                        .runNo(run.getRunNo())
                        .status(run.getStatus())
                        .traceId(run.getTraceId())
                        .start(formatTimestamp(run.getStartTime()))
                        .end(formatTimestamp(run.getEndTime()))
                        .stages(stages)
                        .build())
                    .build())));
  }

  /**
   * Retrieves a detailed report for a specific stage within a pipeline run,
   * including all jobs in that stage.
   *
   * @param pipelineName the pipeline name
   * @param runNo        the run number
   * @param stageName    the stage name
   * @return a {@link Mono} containing the stage detail response
   * @throws ResourceNotFoundException if the pipeline, run, or stage is
   *     not found
   */
  public Mono<StageDetailResponse> getStageReport(
      String pipelineName, int runNo, String stageName) {
    log.debug("Fetching report for pipeline: {}, run: {}, stage: {}",
        pipelineName, runNo, stageName);

    return findPipeline(pipelineName)
        .flatMap(pipeline -> findPipelineRun(pipeline.getId(), runNo,
            pipelineName)
            .flatMap(run -> findStageRun(run.getId(), stageName,
                pipelineName, runNo)
                .flatMap(stage -> jobRunRepository
                    .findByStageRunId(stage.getId())
                    .map(this::toJobSummary)
                    .collectList()
                    .map(jobs -> StageDetailResponse.builder()
                        .pipeline(StageDetailResponse.PipelineStageData
                            .builder()
                            .name(pipelineName)
                            .runNo(run.getRunNo())
                            .status(run.getStatus())
                            .start(formatTimestamp(run.getStartTime()))
                            .end(formatTimestamp(run.getEndTime()))
                            .stage(StageDetailResponse.StageWithJobs
                                .builder()
                                .name(stage.getStageName())
                                .status(stage.getStatus())
                                .start(formatTimestamp(
                                    stage.getStartTime()))
                                .end(formatTimestamp(
                                    stage.getEndTime()))
                                .jobs(jobs)
                                .build())
                            .build())
                        .build()))));
  }

  /**
   * Retrieves a detailed report for a specific job within a stage of a
   * pipeline run.
   *
   * @param pipelineName the pipeline name
   * @param runNo        the run number
   * @param stageName    the stage name
   * @param jobName      the job name
   * @return a {@link Mono} containing the job detail response
   * @throws ResourceNotFoundException if the pipeline, run, stage, or job
   *     is not found
   */
  public Mono<JobDetailResponse> getJobReport(
      String pipelineName, int runNo, String stageName, String jobName) {
    log.debug("Fetching report for pipeline: {}, run: {}, stage: {}, job: {}",
        pipelineName, runNo, stageName, jobName);

    return findPipeline(pipelineName)
        .flatMap(pipeline -> findPipelineRun(pipeline.getId(), runNo,
            pipelineName)
            .flatMap(run -> findStageRun(run.getId(), stageName,
                pipelineName, runNo)
                .flatMap(stage -> findJobRun(stage.getId(), jobName,
                    stageName, runNo, pipelineName)
                    .map(job -> JobDetailResponse.builder()
                        .pipeline(JobDetailResponse.PipelineJobData
                            .builder()
                            .name(pipelineName)
                            .runNo(run.getRunNo())
                            .status(run.getStatus())
                            .start(formatTimestamp(run.getStartTime()))
                            .end(formatTimestamp(run.getEndTime()))
                            .stage(JobDetailResponse.StageWithJob
                                .builder()
                                .name(stage.getStageName())
                                .status(stage.getStatus())
                                .start(formatTimestamp(
                                    stage.getStartTime()))
                                .end(formatTimestamp(
                                    stage.getEndTime()))
                                .job(toJobSummary(job))
                                .build())
                            .build())
                        .build()))));
  }

  /**
   * Returns the status of the active (RUNNING) or most recently started run
   * for any pipeline associated with the given repository URL.
   *
   * <p>If multiple pipelines share the same repo, the RUNNING run is preferred.
   * When no run is active, the run with the latest start time is returned.
   *
   * @param gitRepo the repository URL stored in the {@code git_repo} column
   * @return a {@link Mono} containing the status response
   * @throws ResourceNotFoundException if no pipeline or run is found for the repo
   */
  public Mono<StatusResponse> getStatusByRepo(String gitRepo) {
    return pipelineRepository.findByGitRepo(gitRepo)
        .switchIfEmpty(Mono.error(new ResourceNotFoundException(
            "No pipeline found for repo: " + gitRepo)))
        .flatMap(pipeline ->
            pipelineRunRepository
                .findFirstByPipelineIdOrderByRunNoDesc(pipeline.getId())
                .map(run -> Map.entry(pipeline, run))
        )
        .collectList()
        .flatMap(entries -> {
          if (entries.isEmpty()) {
            return Mono.error(new ResourceNotFoundException(
                "No runs found for repo: " + gitRepo));
          }
          Map.Entry<Pipeline, PipelineRun> chosen = entries.stream()
              .filter(e -> "RUNNING".equalsIgnoreCase(e.getValue().getStatus()))
              .findFirst()
              .orElseGet(() -> entries.stream()
                  .max(Comparator.comparing(
                      e -> e.getValue().getStartTime(),
                      Comparator.nullsFirst(Comparator.naturalOrder())))
                  .orElseThrow());
          return buildStatusHierarchy(chosen.getKey(), chosen.getValue());
        });
  }

  /**
   * Returns the status of a specific run of the named pipeline, including
   * the full stage and job hierarchy.
   *
   * @param pipelineName the pipeline name
   * @param runNo        the run number
   * @return a {@link Mono} containing the status response
   * @throws ResourceNotFoundException if the pipeline or run is not found
   */
  public Mono<StatusResponse> getStatusByRun(String pipelineName, int runNo) {
    return findPipeline(pipelineName)
        .flatMap(pipeline -> findPipelineRun(pipeline.getId(), runNo, pipelineName)
            .flatMap(run -> buildStatusHierarchy(pipeline, run)));
  }

  private Mono<StatusResponse> buildStatusHierarchy(Pipeline pipeline, PipelineRun run) {
    return stageRunRepository.findByPipelineRunId(run.getId())
        .flatMap(stage ->
            jobRunRepository.findByStageRunId(stage.getId())
                .map(job -> StatusJobDto.builder()
                    .name(job.getJobName())
                    .status(job.getStatus())
                    .build())
                .collectList()
                .map(jobs -> StatusStageDto.builder()
                    .name(stage.getStageName())
                    .status(stage.getStatus())
                    .jobs(jobs)
                    .build())
        )
        .collectList()
        .map(stages -> StatusResponse.builder()
            .pipelineName(pipeline.getPipelineName())
            .runNo(run.getRunNo())
            .status(run.getStatus())
            .stages(stages)
            .build());
  }

  private Mono<Pipeline> findPipeline(String pipelineName) {
    return pipelineRepository.findByPipelineName(pipelineName)
        .next()
        .switchIfEmpty(Mono.error(new ResourceNotFoundException(
            "Pipeline '" + pipelineName + "' not found")));
  }

  private Mono<PipelineRun> findPipelineRun(
      Long pipelineId, int runNo, String pipelineName) {
    return pipelineRunRepository.findByPipelineIdAndRunNo(pipelineId, runNo)
        .switchIfEmpty(Mono.error(new ResourceNotFoundException(
            "Run " + runNo + " not found for pipeline '"
                + pipelineName + "'")));
  }

  private Mono<StageRun> findStageRun(
      Long pipelineRunId, String stageName,
      String pipelineName, int runNo) {
    return stageRunRepository
        .findByPipelineRunIdAndStageName(pipelineRunId, stageName)
        .switchIfEmpty(Mono.error(new ResourceNotFoundException(
            "Stage '" + stageName + "' not found in run " + runNo
                + " of pipeline '" + pipelineName + "'")));
  }

  private Mono<JobRun> findJobRun(
      Long stageRunId, String jobName,
      String stageName, int runNo, String pipelineName) {
    return jobRunRepository
        .findByStageRunIdAndJobName(stageRunId, jobName)
        .switchIfEmpty(Mono.error(new ResourceNotFoundException(
            "Job '" + jobName + "' not found in stage '" + stageName
                + "' of run " + runNo + " of pipeline '"
                + pipelineName + "'")));
  }

  private RunSummary toRunSummary(PipelineRun run, Pipeline pipeline) {
    return RunSummary.builder()
        .runNo(run.getRunNo())
        .status(run.getStatus())
        .gitRepo(pipeline.getGitRepo())
        .gitBranch(run.getGitBranch())
        .gitHash(run.getGitHash())
        .start(formatTimestamp(run.getStartTime()))
        .end(formatTimestamp(run.getEndTime()))
        .build();
  }

  private StageSummary toStageSummary(StageRun stage) {
    return StageSummary.builder()
        .name(stage.getStageName())
        .status(stage.getStatus())
        .start(formatTimestamp(stage.getStartTime()))
        .end(formatTimestamp(stage.getEndTime()))
        .build();
  }

  private JobSummary toJobSummary(JobRun job) {
    return JobSummary.builder()
        .name(job.getJobName())
        .status(job.getStatus())
        .failures(job.isFailures())
        .start(formatTimestamp(job.getStartTime()))
        .end(formatTimestamp(job.getEndTime()))
        .build();
  }

  private String formatTimestamp(OffsetDateTime timestamp) {
    if (timestamp == null) {
      return null;
    }
    return timestamp.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }
}
