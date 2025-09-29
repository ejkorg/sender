#!/usr/bin/env bash
# One-liner helper for RHEL8/Podman developers to build dependencies, seed H2 and bring the stack up.
# Usage: ./scripts/rhel-dev-up.sh [-d|--detach] [-b|--backend-port PORT] [-f|--frontend-port PORT]
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

DETACH=0
BACKEND_PORT=8005
FRONTEND_PORT=80

usage() {
  echo "Usage: $0 [-d|--detach] [-b|--backend-port PORT] [-f|--frontend-port PORT]"
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -d|--detach)
      DETACH=1; shift ;;
    -b|--backend-port)
      BACKEND_PORT="$2"; shift 2 ;;
    -f|--frontend-port)
      FRONTEND_PORT="$2"; shift 2 ;;
    -h|--help)
      usage ;;
    *)
      echo "Unknown arg: $1"; usage ;;
  esac
done

echo "1) Ensure podman and mvn are available"
if ! command -v podman >/dev/null 2>&1; then
  echo "podman not found. Install with: sudo dnf install -y podman" >&2
  exit 1
fi

echo "2) Fetch backend dependencies (for h2 jar)"
pushd backend >/dev/null
mvn -B -DskipTests dependency:copy-dependencies -DoutputDirectory=target/dependency
popd >/dev/null

echo "3) Seed H2 external DB"
./backend/scripts/docker-seed-h2.sh

echo "4) Start the stack with podman compose (backend port=$BACKEND_PORT frontend port=$FRONTEND_PORT)"
export BACKEND_HOST_PORT="$BACKEND_PORT"
export FRONTEND_HOST_PORT="$FRONTEND_PORT"

if [[ "$DETACH" -eq 1 ]]; then
  podman compose up --build -d
else
  podman compose up --build
fi
