# Week 2

# Completed tasks

| Task | Weight | Assignee | 
|------|--------|----------| 
| [[Design] Update Phase 1 and Phase 2 system diagrams](https://github.com/CS7580-SEA-SP26/c-team/issues/46#issue-3903188808) | S      | Blake    | 
| [[DESIGN] Design Analysis - Alternative Modular Architecture](https://github.com/CS7580-SEA-SP26/c-team/issues/51#issue-3903362494) | S      | Lelin    |
| [[DESIGN] Design Analysis - Monolithic Architecture Alternative](https://github.com/CS7580-SEA-SP26/c-team/issues/50#issue-3903360465) | S      | Lelin    |
| [[DRYRUN] Implement Execution Order Logic for Dryrun](https://github.com/CS7580-SEA-SP26/c-team/issues/48#issue-3903308863) | M      | Lelin    |
| [[CLI] Implement Dryrun Command Structure & Integration](https://github.com/CS7580-SEA-SP26/c-team/issues/47#issue-3903305772) | S      | Jiachen  |
| [[DRYRUN] Implement Output Formatter for Dryrun](https://github.com/CS7580-SEA-SP26/c-team/issues/49#issue-3903314292) | S      | Jiachen  |
| [[WEEKLY REPORT] Add Weekly Report for Sprint 2](https://github.com/CS7580-SEA-SP26/c-team/issues/62#issue-3913393738) | S      | Jiachen  |
| [[README] Add week2 developments in README](https://github.com/CS7580-SEA-SP26/c-team/issues/61#issue-3913389499) | S      | Lelin    |

# Carry over tasks

| Task                                                                                                                                               | Weight | Assignee |
|----------------------------------------------------------------------------------------------------------------------------------------------------|--------|----------|
| [[REFACTOR] Refactor Monolithic Application into Microservices Architecture](https://github.com/CS7580-SEA-SP26/c-team/issues/52#issue-3903383035) | M      | Jiachen  |

# What worked this week?

- We had in-depth discussions on the overall system design and ensured that all team members were aligned on the same
  architectural direction. In addition to our regular meeting, we held an ad-hoc meeting to resolve several design
  ambiguities, which helped the team reach consensus efficiently.

- The division of labor worked well this week. Each task had clear ownership and well-defined boundaries,
  which minimized overlap and allowed team members to work independently without blocking one another.

- Team members actively thought ahead during the design phase rather than focusing only on immediate implementation.
  In particular, we proactively sought feedback from the professor on transitioning from a monolithic design to
  a microservices-based structure, which helped us better plan future tasks and reduce potential rework.

# What did not work this week?

- While working on an issue, a team member started a new branch without fully pulling the latest changes from
  the main branch. This led to unnecessary rework and minor inconsistencies that had to be resolved later.
  As a result, we identified the need to double-check synchronization with main before starting new tasks.

# Design updates

- We revised the system architecture from an initial three-microservice design (Config, Execution, Report)
  to a two-microservice design. The Config service was refactored into a shared library component responsible for
  validation and dry-run logic, which is now reused by both the Execution service and the CLI.

- We explicitly added a database to the high-level architecture to support persistent storage of pipeline execution
  results and reports, making data flow and component responsibilities clearer.

- We changed the interaction model from creating data through multiple individual requests to batching related data
  into a single request, simplifying service communication and reducing overhead.

- We introduced a message queue between the Execution service and the Report service. The Execution service now
  produces execution messages asynchronously, while the Report service consumes these messages from the queue and
  persists them into the database, improving decoupling and scalability.