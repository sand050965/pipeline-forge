# Tech Stack

This document describes the technology stack chosen for the Custom CI/CD System project.  
The system is composed of 4 major components: **CLI**, **API Gateway**, **REST Service**, and **Data Store**.

---

## 1. Programming Languages

### CLI

- **Java 21**
- Reason:
    - Strong ecosystem and tooling
    - Easy integration with YAML parsing, Git, and HTTP clients
    - Consistent language across the entire system

### API Gateway

- **Java 21**
- Reason:
    - Aligns with the Java-based technology stack used across the system
    - Integrates naturally with Spring-based services

### REST Service

- **Java 21**
- Reason:
    - Matches CLI language to reduce cognitive overhead
    - Well-supported for REST APIs and microservices

### Data Store

- **PostgreSQL**
- Reason:
    - Mature, reliable relational database
    - Strong support for transactional data and reporting queries

---

## 2. Build Tools

### Build Tool

- **Gradle (9.1+)**
- Used for:
    - Compiling source code
    - Running tests
    - Generating reports and documentation
    - Packaging artifacts

### Gradle Plugins

| Purpose         | Tool                           |
|-----------------|--------------------------------|
| Testing         | JUnit 5                        |
| Test Coverage   | JaCoCo                         |
| Documentation   | Javadoc                        |
| Code Style      | Checkstyle (Google Java Style) |
| Static Analysis | SpotBugs                       |

---

## 3. Libraries and Dependencies

### CLI Dependencies

- **Picocli** – CLI argument parsing and subcommand support
- **SnakeYAML** – YAML v1.2 parsing and validation
- **Jackson** – Object mapping (if needed for REST communication)
- **JGit** – Interaction with Git repositories
- **JUnit 5** – Unit testing

### REST Service Dependencies

- **Spring Boot 4.0.2**
- **Spring WebFlux** – Reactive REST API support
- **Spring Cloud Gateway** – API Gateway routing (API Gateway service)
- **Spring Validation** – Input validation
- **Spring Data R2DBC** – Reactive database access
- **PostgreSQL R2DBC Driver** – Reactive PostgreSQL connectivity
- **RabbitMQ (AMQP)** – Async pipeline execution messaging
- **Resilience4j** – Circuit breaker and retry (API Gateway)
- **Flyway** – Database schema migrations
- **JUnit 5** – Unit and integration tests

---

## 4. Frameworks

### CLI

- No application framework
- Plain Java application using Picocli

### API Gateway

- **Spring Cloud Gateway (Java-based)**
- Reason:
    - Seamless integration with the Spring Boot ecosystem used by the REST services
    - Non-blocking and lightweight, suitable for routing CLI requests

### REST Service

- **Spring Boot 4.0.2**
- Reason:
    - Rapid development of REST APIs
    - Built-in dependency injection
    - Easy configuration and testing support

---

## 5. Data Store

### Primary Database

- **PostgreSQL**
- Usage:
    - Store pipeline execution metadata
    - Store stage and job execution records
    - Support reporting queries

### Message Queue

- **RabbitMQ** – Decouples pipeline submission from execution; durable exchange and queue

### Local Development

- PostgreSQL + RabbitMQ running via Docker Compose

---

## 6. Deployment

### Options

| Method | Tool | Notes |
|--------|------|-------|
| Local development | Docker Compose | All services + PostgreSQL + RabbitMQ |
| Kubernetes | `kubectl apply -R -f k8s/` | Raw manifests (Deployments, StatefulSets) |
| Kubernetes (recommended) | Helm 3 — `helm install cicd ./helm/cicd-system` | Full chart with configurable `values.yaml` |

---

## 8. CI/CD Configuration

Our repository will use GitHub Actions for CI/CD.

### Pipelines

We will configure three **CI/CD pipelines** for the repository:

#### 1. Pull Request Pipeline

Triggered on pull requests.

- Build
- Run tests
- Checkstyle
- SpotBugs
- Test coverage check (minimum 70%)

#### 2. Main Branch Pipeline

Triggered on merge to `main`.

- Full build
- All tests
- Coverage report
- Static analysis reports

#### 3. Release Pipeline

Triggered on release or tag.

- Build artifacts
- Package JAR files
- Publish reports

---

## 9. Repositories

We plan to use a single Git repository for this project.

The repository contains:

- CLI implementation
- API Gateway configuration
- REST service implementation
- Design documents and weekly reports

All CI/CD pipelines described above (PR, main branch, and release pipelines) will be configured and executed within this
single repository using GitHub Actions
