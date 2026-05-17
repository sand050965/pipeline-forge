# Week 7

# Completed tasks

| Task                                                                                                                                                           | Weight | Assignee | 
|----------------------------------------------------------------------------------------------------------------------------------------------------------------|--------|----------| 
| [[K8s] Write Installation & Setup Documentation](https://github.com/CS7580-SEA-SP26/c-team/issues/231#issue-4097024471)                                        | M      | Jiachen  | 
| [[Async] Update run command for async response](https://github.com/CS7580-SEA-SP26/c-team/issues/249#issue-4114709848)                                         | S      | Jiachen  | 
| [[Status] Track RUNNING status during pipeline execution](https://github.com/CS7580-SEA-SP26/c-team/issues/250#issue-4114712239)                               | S      | Jiachen  | 
| [[Status] Add status CLI sub-command](https://github.com/CS7580-SEA-SP26/c-team/issues/251#issue-4114717674)                                                   | L      | Jiachen  | 
| [[DOCS] Update All READMEs for allow-failures → failures Rename](https://github.com/CS7580-SEA-SP26/c-team/issues/238#issue-4097049977)                        | S      | Lelin    | 
| [[BUG] Rename allow-failures → failures Across All Components + Republish pipeline-lib](https://github.com/CS7580-SEA-SP26/c-team/issues/236#issue-4097044058) | M      | Lelin    | 
| [[BUG] Remove --remote Flag and Store git-repo as HTTP Remote URL](https://github.com/CS7580-SEA-SP26/c-team/issues/233#issue-4097033230)                      | M      | Blake    | 
| [[Doc-K8s] Classify Components as K8s-Enabled or Non-K8s-Enabled](https://github.com/CS7580-SEA-SP26/c-team/issues/232#issue-4097026629)                       | L      | Blake    | 
| [[K8s] Write Kubernetes Manifests for Stateless Services](https://github.com/CS7580-SEA-SP26/c-team/issues/235#issue-4097038305)                               | L      | Blake    | 
| [[K8s] Create Helm Chart for the Full System](https://github.com/CS7580-SEA-SP26/c-team/issues/234#issue-4097034271)                                           | L      | Lelin    | 
| [[ASYNC] Implement RabbitMQ Pipeline Execution Queue in Execution Service](https://github.com/CS7580-SEA-SP26/c-team/issues/230#issue-4097021058)              | L      | Jiachen  | 
| [[Demo] K8s, Helm, and Async Pipeline Execution](https://github.com/CS7580-SEA-SP26/c-team/issues/256#issue-4116718914)                                        | S      | Lelin    | 
| [[config] Modified the CI config](https://github.com/CS7580-SEA-SP26/c-team/issues/261#issue-4117550665)                                                       | M      | Blake    |
| [[ASYNC] BATS Integration Tests for Async run + status](https://github.com/CS7580-SEA-SP26/c-team/issues/255#issue-4116706694)                                 | M      | Lelin    |
| [[K8s] Write Kubernetes Manifests for Stateful Components](https://github.com/CS7580-SEA-SP26/c-team/issues/237#issue-4097046108)                              | L      | Lelin    |
| [[Doc] Update all Readmes for K8s + HELM + Async Client](https://github.com/CS7580-SEA-SP26/c-team/issues/258#issue-4116735092)                                                                                                                                                           | M      | Blake    |
| [[Weekly Report] Write weekly report for sprint 7](https://github.com/CS7580-SEA-SP26/c-team/issues/257#issue-4116732308)                                      | S      | Jiachen  |

# Carry over tasks
None

# What worked this week?
- During this week, we maintained a high level of productivity and implemented two new extensions. 
We introduced Kubernetes support by enabling deployment of all stateless and stateful components 
in a cluster, providing a Helm chart for deployment, and updating documentation to clearly define
Kubernetes-enabled components as well as their setup and communication. In addition, we made the 
run sub-command asynchronous so that the CLI returns immediately with a run number, and added 
a status sub-command to allow users to query pipeline execution states, including both ongoing 
and completed runs.

# What did not work this week?
- We encountered compatibility issues with our Docker images during local testing. Initially, the
  images were published only for the Linux ARM architecture, which caused failures when running them
  on macOS-based development environments. To resolve this issue, we rebuilt and republished the
  images as multi-architecture (multi-arch) images, ensuring compatibility across different platforms,
  including macOS and standard Linux environments.

# Design updates
- The `run` sub-command was redesigned to be asynchronous: the CLI now returns immediately with 
a run number rather than blocking until the pipeline completes. This required introducing a message 
queue (RabbitMQ) within the execution service to decouple the HTTP request handler from the actual pipeline execution worker.
  - Link to update: - [Phase 2 Architecture](../../designs/design.md#phase-2-remote-microservices)

- A new `status` sub-command was added to allow users to query the state of pipeline runs 
(both in-progress and completed). Stage-level status is derived from job statuses according to 
defined rules (Pending / Running / Success / Failed).
  - Link to update: - [CLI (Command Line Client)](../../designs/design.md#1-cli-command-line-client)



