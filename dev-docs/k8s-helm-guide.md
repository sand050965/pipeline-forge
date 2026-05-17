# Kubernetes Installation & Setup Guide

This guide walks through deploying the CI/CD system on Kubernetes using the provided Helm chart.
It covers full in-cluster deployment and how to connect the CLI (the only non-K8s component) to the cluster.

---

## Component Classification

| Component | Type | K8s-Enabled | Notes |
|---|---|---|---|
| API Gateway | Stateless service | **Yes (required)** | REST entry point, routed via LoadBalancer |
| Execution Service | Stateless service | **Yes (required)** | Pipeline orchestration and job execution |
| Report Service | Stateless service | **Yes (required)** | Execution history queries |
| PostgreSQL | Stateful database | **Yes (optional)** | Can be replaced by an external managed DB |
| RabbitMQ | Stateful queue | **Yes (optional)** | Can be replaced by an external managed broker |
| CLI | Command-line tool | **No** | Runs on the developer's host machine; communicates with the cluster over HTTP |

**Why is the CLI excluded from K8s?**
The CLI is a developer workstation tool that reads local Git repositories and pipeline files
from the host filesystem. It sends requests to the API Gateway over HTTP and does not need to
run inside a cluster. Packaging it as a pod would require mounting the developer's workspace
into the container, which is impractical for day-to-day use.

---

## Prerequisites

| Tool | Minimum version | Purpose |
|---|---|---|
| `docker` | 20+ | Required as the minikube driver and for building images |
| `kubectl` | 1.28 | Cluster interaction |
| `helm` | 3.12 | Chart deployment |
| A running K8s cluster | — | minikube / kind for local; any cloud provider for production |

> **Docker is required.** minikube uses Docker as its default driver to create the local cluster node. Install Docker before proceeding — see the **[Docker Compose Guide — Install Docker](docker-guide.md#install-docker)** for instructions.

The cluster nodes must be able to reach Docker Hub to pull the `cs7580cteamcicd` images.

### Installing prerequisites (Ubuntu)

```bash
# Docker (required before minikube)
sudo apt install -y ca-certificates curl gnupg
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER && newgrp docker

# kubectl
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl

# Helm 3
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

# minikube (local clusters only)
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
sudo install minikube-linux-amd64 /usr/local/bin/minikube
```

---

## Full In-Cluster Deployment (Default)

This configuration deploys all components — stateless services plus PostgreSQL and RabbitMQ —
entirely inside the cluster. This is the recommended starting point.

### Step 1 — Download the Helm chart

Download the packaged Helm chart from the latest GitHub release:

```bash
curl -L \
  "https://github.com/CS7580-SEA-SP26/c-team/releases/latest/download/cicd-system-0.1.0.tgz" \
  -o cicd-system.tgz
```

---

### Step 2 — Start your cluster

**minikube (local):**
```bash
minikube start
```

**Cloud provider:** ensure `kubectl` is configured to point at your cluster:
```bash
kubectl cluster-info
```

---

### Step 3 — Deploy with Helm

```bash
helm install cicd-system cicd-system-0.1.0.tgz
```

That's it. The chart creates all Deployments, StatefulSets, Services, ConfigMaps, and Secrets
automatically using the default values baked into `values.yaml`.

To watch the rollout:
```bash
kubectl get pods -w
```

Wait until every pod shows `Running` and all containers are `Ready`.

---

### Step 4 — Connect the CLI

**minikube:**
```bash
export CICD_GATEWAY_BASE_URL=$(minikube service api-gateway --url)
```

**Or use the provided helper script (also sets the env var automatically):**
```bash
source k8s/port-forward-api-gateway.sh
```

**Cloud provider (LoadBalancer):**
```bash
export CICD_GATEWAY_BASE_URL=http://$(kubectl get svc api-gateway -o jsonpath='{.status.loadBalancer.ingress[0].ip}'):8080
```

To make the setting persistent across shell sessions:
```bash
echo "export CICD_GATEWAY_BASE_URL=http://<EXTERNAL-IP>:8080" >> ~/.bashrc
source ~/.bashrc
```

---

## End-to-End Verification

```bash
# 1. Confirm all pods are healthy
kubectl get pods

# 2. Check API Gateway health
curl http://<GATEWAY-URL>/api/v1/pipelines/health   # → "API Gateway is healthy"

# 3. Run a sample pipeline from a repo with a .pipelines/ directory
cd /path/to/your-repo
cicd run --name <pipeline-name>
```

Expected output:
```
Pipeline started. Execution ID: <uuid>
Status: SUCCESS
Pipeline completed successfully.
```

Then confirm the report is stored:
```bash
cicd report --pipeline <pipeline-name>
```

---

## Helm Values Reference

All available configuration options are documented in [`helm/cicd-system/values.yaml`](../helm/cicd-system/values.yaml).


