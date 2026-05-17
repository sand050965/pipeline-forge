# Week 3

# Completed tasks

| Task                                                                                                                                                         | Weight | Assignee | 
|--------------------------------------------------------------------------------------------------------------------------------------------------------------|--------|----------| 
| [[Verify] Add duplicate job name validation and suppress internal Java error messages](https://github.com/CS7580-SEA-SP26/c-team/issues/71#issue-3928635407) | M      | Lelin    | 
| [[DRYRUN] Enhance ExecutionPlan with Job Dependencies](https://github.com/CS7580-SEA-SP26/c-team/issues/72#issue-3928641365)                                 | S      | Lelin    | 
| [[DESIGN] Create shared pipeline library (verify and dryrun)](https://github.com/CS7580-SEA-SP26/c-team/issues/73#issue-3928653107)                          | XL     | Lelin    | 
| [[Refactor]Refactor CLI](https://github.com/CS7580-SEA-SP26/c-team/issues/83#issue-3934417442)                                                               | L      | Blake    | 
| [[Refactor] Refactor CLI and create microservices architecture using shared library](https://github.com/CS7580-SEA-SP26/c-team/issues/74#issue-3928655200)   | L      | Blake    | 
| [[Structure]Initialize Exec Service](https://github.com/CS7580-SEA-SP26/c-team/issues/85#issue-3934424428)                                                   | S      | Blake    | 
| [[Doc] Design Queue-Based Job Execution System](https://github.com/CS7580-SEA-SP26/c-team/issues/79#issue-3928740327)                                        | S      | Blake    | 
| [[CLI] Implement run Sub-command CLI Interface](https://github.com/CS7580-SEA-SP26/c-team/issues/67#issue-3928612473)                                        | L      | Jiachen  | 
| [[Structure]Initialize API Gateway Scaffolding](https://github.com/CS7580-SEA-SP26/c-team/issues/86#issue-3934432798)                                        | S      | Blake    | 
| [[Execution] Implement Sequential Job Executor](https://github.com/CS7580-SEA-SP26/c-team/issues/69#issue-3928617854)                                        | L      | Lelin    | 
| [[CLI] Implement Git Branch/Commit Validation](https://github.com/CS7580-SEA-SP26/c-team/issues/68#issue-3928615062)                                         | S      | Jiachen  | 
| [[Verify] Support directory input and validate all YAML files](https://github.com/CS7580-SEA-SP26/c-team/issues/65#issue-3923789477)                         | S      | Jiachen  | 
| [[Doc] Add README for Pipeline Library](https://github.com/CS7580-SEA-SP26/c-team/issues/99#issue-3944748506)                                                | S      | Lelin    | 
| [[Doc] Add README for CLI](https://github.com/CS7580-SEA-SP26/c-team/issues/100#issue-3944748820)                                                            | S      | Blake    | 
| [[Doc] Add README for API Gateway](https://github.com/CS7580-SEA-SP26/c-team/issues/98#issue-3944748401)                                                     | S      | Blake    | 
| [[Execution] Implement Docker Container Execution](https://github.com/CS7580-SEA-SP26/c-team/issues/70#issue-3928626204)                                     | L      | Jiachen  | 
| [[Execution]Implement Job Failure Handling](https://github.com/CS7580-SEA-SP26/c-team/issues/66#issue-3928605065)                                            | S      | Jiachen  | 
| [[Execution] Fix end-to-end RunCommand and add bats tests](https://github.com/CS7580-SEA-SP26/c-team/issues/76#issue-3928711037)                             | M      | Jiachen  | 
| [[Integration] Integrate Execution Service into API Gateway](https://github.com/CS7580-SEA-SP26/c-team/issues/94#issue-3942406795)                           | M      | Blake    | 
| [[Test] Add tests for execution-service](https://github.com/CS7580-SEA-SP26/c-team/issues/114#issue-3949313200)                                              | XS     | Jiachen  | 
| [[Doc] Add README for Execution Service](https://github.com/CS7580-SEA-SP26/c-team/issues/101#issue-3944750689)                                              | S      | Blake    | 
| [[Doc] Update Project Documentation](https://github.com/CS7580-SEA-SP26/c-team/issues/78#issue-3928719228)                                                   | S      | Lelin    | 
| [[TEST] End-to-End Integration Testing: CLI → API Gateway → Execution Service](https://github.com/CS7580-SEA-SP26/c-team/issues/118#issue-3949498806)        | L      | Lelin    | 
| [[Weekly Report] Create weekly report for sprint 3](https://github.com/CS7580-SEA-SP26/c-team/issues/116#issue-3949392108)                                   | S      | Jiachen  |

# Carry over tasks


| Task                                                                                                       | Weight | Assignee |
|------------------------------------------------------------------------------------------------------------|--------|----------|
| [[Refactor]Refactored Report Service](https://github.com/CS7580-SEA-SP26/c-team/issues/84#issue-3934421943) | S      | Blake    | 
| [[Config] Modify gradle config](https://github.com/CS7580-SEA-SP26/c-team/issues/113#issue-3949276292)     | S      | Blake    |
| [[Architecture] Dockerize each service](https://github.com/CS7580-SEA-SP26/c-team/issues/97#issue-3943102708)                       | M      | Blake    | 
| [[Refactor] Refactor DTO between API Gateway](https://github.com/CS7580-SEA-SP26/c-team/issues/111#issue-3949218162)                | M      | Lelin    | 
| [[Refactor] Added Git Validation to Execution Service](https://github.com/CS7580-SEA-SP26/c-team/issues/112#issue-3949229696)       | M      | Jiachen  | 


# What worked this week?

- After watching other teams’ demos, we revisited our overall architecture and refined several key design decisions. 
We had in-depth discussions about service boundaries and coordination between modules, which strengthened our alignment 
and improved the system’s readiness for future features and scalability.

- The refactoring from a more centralized structure into clearer microservices and modules went smoothly. 
The responsibilities of each component became more explicit, and the division of labor was effective, allowing 
parallel progress without major conflicts.

- This week involved substantial refactoring across multiple modules to make the run command fully functional while 
also ensuring long-term scalability for the entire project. To maintain strong coordination, we held additional 
spontaneous meetings, including one focused two-person session and two extra group meetings beyond our regular 
weekly meetings.

- We set internal deadlines one day before the class deadline to create buffer time for integration testing and final 
validation. This helped reduce last-minute risks and improved overall confidence in delivery.

# What did not work this week?

- During low-level design and implementation, we realized that some design choices could potentially be improved 
after seeing them in practice. For example, while refactoring the DTO between the API Gateway and Execution Service, 
we realized that passing the entire YAML file was unnecessary and inefficient. After reviewing the actual data needs, 
we had to redesign the DTO to transfer only the required fields. This led to partial rework and caused part of 
the task to carry over to the next sprint.

# Design updates

- We decided to remove the message queue between the Execution Service and the Report Service. Instead of generating 
execution results and sending them asynchronously to the Report Service, the Execution Service will now write
execution results directly into the database. This simplifies the architecture and reduces communication complexity 
at the current stage.

- We introduced a plan to add an internal asynchronous job queue for Docker workers (to be implemented 
in future sprints). This queue will allow jobs to be executed in parallel, improving scalability and preparing 
the system for higher execution throughput in later phases.

- Links to updates:
  - [Phase 1 Architecture](../../designs/design.md#phase-1-local-microservices)
  - [Phase 2 Architecture](../../designs/design.md#phase-2-remote-microservices)


