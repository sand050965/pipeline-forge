# CI/CD Pipeline Library

A Java library for validating CI/CD pipeline configurations and generating execution plans with dependency resolution.

## Overview

The CI/CD Pipeline Library provides comprehensive validation and execution planning for pipeline configurations defined in YAML. It handles YAML parsing, structural validation, dependency analysis (including circular dependency detection), and execution plan generation with topological sorting.

## Features

### Validation
- ✅ YAML syntax and structure validation with precise error positions
- ✅ Required field validation (pipeline name, stages, jobs)
- ✅ Job dependency analysis and circular dependency detection
- ✅ Pipeline-wide duplicate job name detection
- ✅ Stage and job name uniqueness validation
- ✅ Type correctness for all configuration fields
- ✅ Multi-error collection (reports all issues, not just first)
- ✅ Directory-level validation with duplicate pipeline name detection

### Execution Planning
- ✅ Topological sorting of jobs by dependencies
- ✅ Stage grouping with dependency-aware ordering
- ✅ Job dependency map construction for parallel execution
- ✅ ExecutionPlan generation ready for sequential or parallel execution

### Error Reporting
- ✅ IDE-friendly error format: `filename:line:column: ERROR, message`
- ✅ Clean error messages (no internal stack traces)
- ✅ Multi-error aggregation for comprehensive feedback

## Installation

The library is published on Maven Central. No additional repository configuration is required.

**Maven Central:** https://central.sonatype.com/artifact/io.github.northeastern-cs7580-team-c/pipeline-lib

### Gradle
```gradle
dependencies {
    implementation 'io.github.northeastern-cs7580-team-c:pipeline-lib:1.1.0'
}
```

### Maven
```xml
<dependency>
    <groupId>io.github.northeastern-cs7580-team-c</groupId>
    <artifactId>pipeline-lib</artifactId>
    <version>1.1.0</version>
</dependency>
```

## Requirements

- Java 21 or higher
- Dependencies managed automatically:
  - SnakeYAML 2.2
  - SLF4J 2.0.17
  - Lombok 1.18.30 (compile-only)

## Quick Start

### Basic Validation

```java
import edu.northeastern.cs7580.cicd.pipelinelib.api.PipelineService;
import edu.northeastern.cs7580.cicd.pipelinelib.api.PipelineServiceFactory;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Pipeline;
import edu.northeastern.cs7580.cicd.pipelinelib.exception.ValidationException;

// Create service instance
PipelineService service = PipelineServiceFactory.create();

// Validate a single pipeline file
try {
    Pipeline pipeline = service.validatePipeline(Path.of(".pipelines/default.yaml"));
    System.out.println("✓ Pipeline '" + pipeline.getPipeline().getName() + "' is valid");
} catch (ValidationException e) {
    System.err.println("Validation failed:");
    System.err.println(e.getMessage());
    // Output:
    // .pipelines/default.yaml:15:3: ERROR, Duplicate job name 'compile'
    // .pipelines/default.yaml:22:5: ERROR, Job 'test' references undefined job 'build'
}
```

### Directory Validation
```java
PipelineService service = PipelineServiceFactory.create();

try {
    service.validateDirectory(Path.of(".pipelines"));
    System.out.println("✓ All pipelines are valid and names are unique");
} catch (ValidationException e) {
    System.err.println("Validation errors:");
    System.err.println(e.getMessage());
}
```

### Generate Execution Plan
```java
PipelineService service = PipelineServiceFactory.create();
ExecutionPlan plan = service.createExecutionPlan(Path.of(".pipelines/build.yaml"));

// Sequential execution pattern
List<Job> allJobs = plan.getStages().stream()
    .flatMap(stage -> stage.getJobs().stream())
    .collect(Collectors.toList());

for (Job job : allJobs) {
    System.out.println("Executing: " + job.getName());
    executeJob(job);
}
```

## API Reference

### Core Interface: PipelineService

The main entry point for all pipeline operations.
```java
public interface PipelineService {
    Pipeline validatePipeline(Path configFile) throws ValidationException;
    void validateDirectory(Path directory) throws ValidationException;
    ExecutionPlan createExecutionPlan(Path configFile) throws ValidationException;
}
```

### Factory: PipelineServiceFactory

Creates fully configured service instances.
```java
PipelineService service = PipelineServiceFactory.create();
```

### Models (DTOs)

**ExecutionPlan** - Execution plan with stages and dependency map
```java
public class ExecutionPlan {
    List<StageExecution> getStages();
    Map<String, List<String>> getJobDependencies();
}
```

**Pipeline** - Validated pipeline configuration
```java
public class Pipeline {
    PipelineMetadata getPipeline();
    List<String> getStages();
    Map<String, Job> getJobs();
}
```

**Job** - Job definition with dependencies
```java
public class Job {
    String getName();
    String getStage();
    String getImage();
    Object getScript();       // String or List<String>
    List<String> getNeeds();
    boolean isAllowFailures(); // defaults to false
}
```

**StageExecution** - Stage with topologically sorted jobs
```java
public class StageExecution {
    String getStageName();
    List<Job> getJobs();  // In dependency order
}
```

### Exception: ValidationException
```java
public class ValidationException extends RuntimeException {
    ValidationException(String message);
    ValidationException(String message, Throwable cause);
}
```

## Usage Examples

### Example 1: Validate Before Execution
```java
PipelineService service = PipelineServiceFactory.create();

try {
    // Validate first
    Pipeline pipeline = service.validatePipeline(pipelineFile);
    
    // Generate execution plan
    ExecutionPlan plan = service.createExecutionPlan(pipelineFile);
    
    // Execute
    executePipeline(plan);
} catch (ValidationException e) {
    logger.error("Pipeline validation failed: {}", e.getMessage());
    System.exit(1);
}
```

### Example 2: Sequential Execution
```java
ExecutionPlan plan = service.createExecutionPlan(Path.of(".pipelines/build.yaml"));

// Flatten stages to get all jobs in topological order
List<Job> allJobs = plan.getStages().stream()
    .flatMap(stage -> stage.getJobs().stream())
    .collect(Collectors.toList());

// Execute jobs one by one
for (Job job : allJobs) {
    System.out.println("Running job: " + job.getName());
    JobResult result = executeJob(job);
    
    if (result.failed()) {
        System.err.println("Job failed, stopping pipeline");
        break;
    }
}
```

### Example 3: Parallel Execution with Dependencies
```java
ExecutionPlan plan = service.createExecutionPlan(configFile);
Map<String, List<String>> dependencies = plan.getJobDependencies();
Set<String> completed = new ConcurrentHashSet<>();
ExecutorService executor = Executors.newFixedThreadPool(4);

for (StageExecution stage : plan.getStages()) {
    for (Job job : stage.getJobs()) {
        List<String> jobDeps = dependencies.get(job.getName());
        
        // Wait until all dependencies are completed
        executor.submit(() -> {
            while (!completed.containsAll(jobDeps)) {
                Thread.sleep(100);
            }
            
            executeJob(job);
            completed.add(job.getName());
        });
    }
}

executor.shutdown();
executor.awaitTermination(1, TimeUnit.HOURS);
```

### Example 4: Inspect Dependencies
```java
ExecutionPlan plan = service.createExecutionPlan(configFile);
Map<String, List<String>> deps = plan.getJobDependencies();

// Display dependency information
for (Map.Entry<String, List<String>> entry : deps.entrySet()) {
    String jobName = entry.getKey();
    List<String> dependencies = entry.getValue();
    
    if (dependencies.isEmpty()) {
        System.out.println(jobName + " - no dependencies");
    } else {
        System.out.println(jobName + " depends on: " 
            + String.join(", ", dependencies));
    }
}

// Output:
// compile - no dependencies
// test depends on: compile
// deploy depends on: test
```

### Example 5: Integration with Dependency Injection

**Spring Framework:**
```java
@Configuration
public class PipelineConfig {
    @Bean
    public PipelineService pipelineService() {
        return PipelineServiceFactory.create();
    }
}

@Service
public class PipelineExecutor {
    private final PipelineService pipelineService;
    
    @Autowired
    public PipelineExecutor(PipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }
    
    public void execute(Path configFile) {
        ExecutionPlan plan = pipelineService.createExecutionPlan(configFile);
        // ... execution logic
    }
}
```

## YAML Configuration Format

The library validates YAML pipeline configurations with the following structure:
```yaml
pipeline:
  name: my-pipeline
  description: Optional description

stages:
  - build
  - test
  - deploy

compile:
  - stage: build
  - image: maven:3.8-jdk21
  - script: mvn clean compile

unit-tests:
  - stage: test
  - image: maven:3.8-jdk21
  - script:
      - mvn test
      - mvn jacoco:report
  - needs: [compile]

flaky-analysis:
  - stage: test
  - image: maven:3.8-jdk21
  - script: mvn verify -Pflakycheck
  - needs: [compile]
  - failures: true   # pipeline continues even if this job fails

deploy-app:
  - stage: deploy
  - image: kubectl:latest
  - script: kubectl apply -f deployment.yaml
  - needs: [unit-tests]
```

## Error Messages

The library provides clear, actionable error messages with file positions:
```
.pipelines/build.yaml:15:3: ERROR, Duplicate job name 'compile'
.pipelines/build.yaml:22:5: ERROR, Job 'test' references undefined job 'build'
.pipelines/build.yaml:30:7: ERROR, Circular dependency detected: job1 -> job2 -> job1
.pipelines/test.yaml:0:0: ERROR, Duplicate pipeline name 'default'
```

## Architecture

### Package Structure
```
edu.northeastern.cs7580.cicd.pipelinelib
├── api/                    (PUBLIC - exported)
│   ├── PipelineService
│   └── PipelineServiceFactory
├── model/                  (PUBLIC - exported)
│   ├── ExecutionPlan
│   ├── Pipeline
│   ├── Job
│   ├── StageExecution
│   └── PipelineMetadata
├── exception/              (PUBLIC - exported)
│   └── ValidationException
└── internal/               (INTERNAL - not exported)
    ├── service/
    ├── validator/
    ├── builder/
    └── parser/
```

### Module System Encapsulation

The library uses Java's module system to enforce API boundaries. Only packages explicitly exported in `module-info.java` are accessible to consumers:

**Exported (accessible):**
- `edu.northeastern.cs7580.cicd.pipelinelib.api`
- `edu.northeastern.cs7580.cicd.pipelinelib.model`
- `edu.northeastern.cs7580.cicd.pipelinelib.exception`

**Internal (hidden):**
- `edu.northeastern.cs7580.cicd.pipelinelib.internal.*`

Attempting to import internal classes will result in a compile-time error:
```
error: package edu.northeastern.cs7580.cicd.pipelinelib.internal.service is not visible
```

## Development

### Prerequisites
- Java 21+
- Gradle (wrapper included)

### Build
```bash
./gradlew :pipeline-lib:build
```

### Run Tests
```bash
./gradlew :pipeline-lib:test
```

### Generate Coverage Report
```bash
./gradlew :pipeline-lib:jacocoTestReport
open pipeline-lib/build/reports/jacoco/test/html/index.html
```

### Generate Javadoc
```bash
./gradlew :pipeline-lib:javadoc
open pipeline-lib/build/docs/javadoc/index.html
```

### Run All Quality Checks
```bash
./gradlew :pipeline-lib:check
```

### Publishing a New Version
1. Bump `version` in `pipeline-lib/build.gradle`
2. Set credentials as environment variables:
```bash
export ORG_GRADLE_PROJECT_signingInMemoryKey=$(cat /path/to/key.asc)
export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=<passphrase>
export ORG_GRADLE_PROJECT_mavenCentralUsername=<sonatype-token-username>
export ORG_GRADLE_PROJECT_mavenCentralPassword=<sonatype-token-password>
```
3. Publish:
```bash
./gradlew :pipeline-lib:publishToMavenCentral
```
4. Go to [central.sonatype.com/publishing/deployments](https://central.sonatype.com/publishing/deployments) and click **Publish**
5. Update the version in all dependent services' `build.gradle` files

## Code Quality

- **Checkstyle:** Google Java Style Guide — `./gradlew :pipeline-lib:checkstyleMain`
- **SpotBugs:** Static analysis — `./gradlew :pipeline-lib:spotbugsMain`
- **JaCoCo:** >70% code coverage required

## Validation Rules

1. **Pipeline Section:** Required, must be a map with `name` field
2. **Stages:** At least one stage must have jobs assigned. Default stages are `build`, `test`, `docs` if not specified
3. **Jobs:** Must have `stage`, `image`, and `script` fields
4. **Job Names:** Must be unique across entire pipeline
5. **Stage References:** Jobs must reference defined stages
6. **Dependencies:** Referenced jobs must exist and be in same stage
7. **Circular Dependencies:** Not allowed (detected via topological sort)
8. **Pipeline Names:** Must be unique within a directory
9. **failures:** Optional boolean field on jobs. Must be `true` or `false`; any other type is a validation error. Defaults to `false` when omitted.

## Version History

### 1.2.0 (Current)
- Rename YAML key `allow-failures` → `failures`
- `Job.isFailures()` replaces `Job.isAllowFailures()`
- Dryrun formatter always emits `failures:` for every job (not only when `true`)

### 1.1.0
- Add `failures` optional boolean field to `Job`
- YAML key `failures: true/false` maps to `Job.isFailures()`
- Defaults to `false` when the key is absent — fully backward compatible
- Validation rejects non-boolean values with a precise error message

### 1.0.0
- Initial release
- Comprehensive validation with position-aware error reporting
- Execution plan generation with dependency map
- Java module system encapsulation
- Duplicate job name detection
- Clean exception handling with user-friendly messages

## License

This library is part of the CS7580 DevOps course project at Northeastern University.