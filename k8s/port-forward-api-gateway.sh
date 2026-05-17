#!/usr/bin/env bash
set -euo pipefail

PORT="${PORT:-8080}"
SERVICE="${SERVICE:-api-gateway}"

if ! command -v kubectl >/dev/null 2>&1; then
  echo "kubectl not found in PATH." >&2
  exit 1
fi

if ! kubectl get svc "$SERVICE" >/dev/null 2>&1; then
  echo "Service '$SERVICE' not found in the current namespace." >&2
  exit 1
fi

# Detect if the script is sourced; if not, warn that env export won't persist.
if (return 0 2>/dev/null); then
  sourced=1
else
  sourced=0
fi

if [ "$sourced" -ne 1 ]; then
  echo "Tip: source this script to export CICD_GATEWAY_BASE_URL in your current shell." >&2
  echo "Example: source k8s/port-forward-api-gateway.sh" >&2
fi

export CICD_GATEWAY_BASE_URL="http://localhost:${PORT}"
echo "CICD_GATEWAY_BASE_URL=${CICD_GATEWAY_BASE_URL}"
echo "Forwarding svc/${SERVICE} to localhost:${PORT}. Press Ctrl+C to stop."

kubectl port-forward "svc/${SERVICE}" "${PORT}:8080"
