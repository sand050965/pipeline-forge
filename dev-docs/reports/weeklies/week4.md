# Week 4

# Completed tasks

| Task | Weight | Assignee | 
|------|--------|----------| 
| [[Config] Modify gradle config](https://github.com/CS7580-SEA-SP26/c-team/issues/113#issue-3949276292) | S      | Blake    | 
| [[Refactor] Display Detailed Validation Error for Run Command at CLI](https://github.com/CS7580-SEA-SP26/c-team/issues/122#issue-3953937070) | S      | Lelin    | 
| [[FEAT] CLI: Implement cicd report Sub-Command](https://github.com/CS7580-SEA-SP26/c-team/issues/127#issue-3959407017) | M      | Lelin    | 
| [[FEAT] Report Service: Implement REST API for Pipeline Execution Queries](https://github.com/CS7580-SEA-SP26/c-team/issues/125#issue-3959391860) | L      | Jiachen  | 
| [[DESIGN] Update Route Table](https://github.com/CS7580-SEA-SP26/c-team/issues/133#issue-3966021672) | XS     | Lelin    | 
| [[INFRA] Set Up PostgreSQL Schema for Pipeline Execution Records](https://github.com/CS7580-SEA-SP26/c-team/issues/123#issue-3959378745) | S      | Blake    | 
| [[Config] Modified workflows configuration](https://github.com/CS7580-SEA-SP26/c-team/issues/136#issue-3966490201) | S      |  Blake         |
| [[FEAT] API Gateway: Add Routes for Report Endpoints](https://github.com/CS7580-SEA-SP26/c-team/issues/126#issue-3959397090) | S      | Jiachen  | 
| [[FEAT] Execution Service: Persist Pipeline/Stage/Job Execution Data to PostgreSQL](https://github.com/CS7580-SEA-SP26/c-team/issues/124#issue-3959383813) | M      | Blake    | 
| [[TEST] End-to-End Integration Test for cicd report](https://github.com/CS7580-SEA-SP26/c-team/issues/128#issue-3959411192) | S      | Lelin    | 
| [[Refactor] Refactor DTO between API Gateway](https://github.com/CS7580-SEA-SP26/c-team/issues/111#issue-3949218162) | S      | Jiachen  | 
| [[Doc] Updated README for execution-service](https://github.com/CS7580-SEA-SP26/c-team/issues/144#issue-3975290213) | S      | Blake    | 
| [[Doc] Updated root project README](https://github.com/CS7580-SEA-SP26/c-team/issues/146#issue-3975291529) | S      | Blake    |
| [[Doc] Update README for CLI](https://github.com/CS7580-SEA-SP26/c-team/issues/143#issue-3975290125) | S      | Lelin    |
| [[Doc] Create README for report-service](https://github.com/CS7580-SEA-SP26/c-team/issues/145#issue-3975291209) | S      | Jiachen  |
| [[Doc] Updated design doc and API Gateway Javadoc](https://github.com/CS7580-SEA-SP26/c-team/issues/154#issue-3975666964) | S      | Blake    |
| [[Doc] Update README for api-gateway](https://github.com/CS7580-SEA-SP26/c-team/issues/147#issue-3975292520) | S      | Jiachen  |
| [[Weekly Report] Create weekly report for sprint 4](https://github.com/CS7580-SEA-SP26/c-team/issues/148#issue-3975294001) | S      | Jiachen  |

# Carry over tasks

| Task | Weight | Assignee |
|------|--------|----------|
| [[Architecture] Dockerize each service](https://github.com/CS7580-SEA-SP26/c-team/issues/97#issue-3943102708) | M      | Blake    |

# What worked this week?

- While different team members were working on different parts of the report workflow, we identified inconsistencies 
in the GET route design across modules. We discussed and standardized the route details, aligning them with our 
overall system design. We also fixed inconsistent package and file naming issues, which improved clarity.

- We revisited the pipeline table design. Initially, we used the pipeline name as the primary key. However, we realized 
that different repositories might have pipelines sharing the same name. After discussing with Professor Skoteiniotis, 
we redesigned the table to use a database-generated auto-increment ID as the primary key, with a composite unique 
constraint on repository ID and pipeline name to prevent naming conflicts across repositories.

- While refactoring the DTO between the API Gateway and Execution Service, we realized that passing the entire YAML 
file was unnecessary and inefficient. We redesigned the DTO to include only the required fields based on actual data 
needs, improving system efficiency and clarity of service boundaries.

# What did not work this week?

- We noticed that the project build process was taking longer than expected. After investigation, we found that all 
tests were being executed twice (once at the root project level and once at the module level). We updated the Gradle 
configuration to ensure tests run only once, which improved build efficiency.

# Design updates

- We updated the pipeline table design to prevent naming conflicts across repositories. Previously, only the pipeline 
name was used as a unique identifier. We redesigned it so that uniqueness is enforced by a composite key of (repository 
ID, pipeline name), while using a database-generated surrogate ID as the actual primary key.
  - Link to updates: [Database Schema](../../designs/design.md#database-schema)

- We updated the GET route design by replacing query parameters with path variables to better align with RESTful 
principles and improve API clarity.
  - Links to updates: 
    - [API Gateway](../../designs/design.md#2-api-gateway)
    - [Report Service API Endpoints](../../designs/design.md#4-report-service)

