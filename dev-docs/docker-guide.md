# Docker Compose Guide

This guide covers everything needed to run the CI/CD system using Docker Compose — from installing Docker to starting, verifying, and stopping the services.

---

## Install Docker

> **Note:** These instructions are for Ubuntu 24.04. On macOS or Windows, install [Docker Desktop](https://docs.docker.com/desktop/) instead.

```bash
sudo apt install -y ca-certificates curl gnupg
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER && newgrp docker
```

Verify Docker is working:

```bash
docker --version
docker compose version
```

You should see something like `Docker version 26.1.3` and `Docker Compose version v2.x`.

---

## Download the System Files

Download `docker-compose.yml` and the CLI binary from the latest GitHub release.

```bash
curl -L \
  "https://github.com/CS7580-SEA-SP26/c-team/releases/latest/download/docker-compose.yml" \
  -o docker-compose.yml
```

## Start All Services

Start all services (API Gateway, Execution Service, Report Service, PostgreSQL, RabbitMQ) using the `docker-compose.yml`:

```bash
docker compose up -d
```

Docker will automatically pull all required images and start them in the background.
Wait 30 seconds for everything to initialise, then check that every service is running:

```bash
sleep 30
docker compose ps
```

Every service listed should show a status of `running` or `healthy`.

---

## Start Infrastructure Only (Developer Use)

If you are running the Java services from source and only need the backing infrastructure (PostgreSQL + RabbitMQ):

```bash
docker compose up -d postgres rabbitmq
```

Wait for both to be healthy before starting any services:

```bash
docker compose ps
# postgres and rabbitmq containers should show "healthy"
```

Alternatively, start PostgreSQL as a standalone container:

```bash
docker run -d \
  --name cicd-postgres \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=cicd \
  -e POSTGRES_DB=cicd \
  -p 5432:5432 \
  postgres:17
```

---

## Verify the Installation

Run these commands to confirm all services are reachable:

```bash
curl http://localhost:8080/api/v1/pipelines/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/api/v1/report/health
```

Each command should return a response containing `"status":"UP"` or `200 OK`.

```bash
cicd --help
docker compose ps
```

If all of the above succeed, your installation is complete.

---

## Stop the System

When you are done, stop all services with:

```bash
docker compose down
```

This shuts down all containers. Your past pipeline run history will be lost when the system is stopped.

---

## Troubleshooting

### Docker Daemon Not Running

The Execution Service needs access to the Docker socket to run pipeline jobs in containers.

```bash
# Linux — start Docker daemon
sudo systemctl start docker

# macOS / Windows — start Docker Desktop from the application menu

# Verify
docker ps
```

On Linux, if you see "permission denied" accessing the socket, add your user to the `docker` group and log out/in:

```bash
sudo usermod -aG docker $USER
newgrp docker
```

### Service Fails to Connect to PostgreSQL

Ensure PostgreSQL is running and healthy before starting any service:

```bash
docker compose ps postgres
# Should show "healthy"

# Or check directly
pg_isready -h localhost -p 5432 -U postgres
```

Default credentials are `postgres` / `cicd` on database `cicd`.

### Service Fails to Connect to RabbitMQ

Ensure RabbitMQ is running and healthy before starting the Execution Service:

```bash
docker compose ps rabbitmq
# Should show "healthy"
```

You can verify connectivity via the RabbitMQ Management UI at http://localhost:15672 (default credentials: `guest` / `guest`).

If the container is not running, start it:

```bash
docker compose up -d rabbitmq
```

### Port Already in Use

Each service has a fixed default port:

| Service | Default Port |
|---------|-------------|
| PostgreSQL | 5432 |
| RabbitMQ (AMQP) | 5672 |
| RabbitMQ (Management UI) | 15672 |
| Execution Service | 8081 |
| Report Service | 8082 |
| API Gateway | 8080 |

Find and stop the conflicting process:

```bash
lsof -i :8080          # macOS / Linux
ss -tlnp | grep 8080   # Linux only
kill <PID>
```
