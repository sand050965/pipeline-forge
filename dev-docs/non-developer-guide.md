# User Guide — Non-Developer Installation and Usage

This guide is for evaluators who want to run the CI/CD system on Ubuntu 24.04.
You do **not** need to know how to program. No Java, Python, or other language runtimes are required.

The only software you need to install is **Git** and **Docker**.

---

## Step 1 — Install Git

Open a terminal and run:

```bash
sudo apt update
sudo apt install -y git
git --version
```

You should see a version number printed, for example `git version 2.43.0`.

---

## Step 2 — Download the CLI

Download the CLI binary from the latest GitHub release.

```bash
curl -L \
  "https://github.com/CS7580-SEA-SP26/c-team/releases/latest/download/cicd-linux-x64.tar.gz" \
  -o cicd-linux-x64.tar.gz
```

---

## Step 3 — Install Docker and Start the System

Follow the **[Docker Compose Guide](docker-guide.md)** for complete instructions covering:

- Installing Docker
- Downloading `docker-compose.yml` and the CLI from the latest release
- Installing the CLI
- Starting all services
- Verifying the installation
- Stopping the system

---

## Step 4 — Run Examples

Follow the **[Evaluator Guide](../README.md#evaluator-guide)** to run examples after installing
the system.

---

## Kubernetes Usage

If you plan to run the system on Kubernetes instead of Docker Compose, see the **[Kubernetes & Helm Guide](k8s-helm-guide.md)** for installation and deployment instructions.
