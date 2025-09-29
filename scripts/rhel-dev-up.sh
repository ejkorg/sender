#!/usr/bin/env bash
# One-liner helper for RHEL8/Podman developers to build dependencies, seed H2 and bring the stack up.
# Usage: ./scripts/rhel-dev-up.sh
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

echo "1) Ensure podman and mvn are available"
if ! command -v podman >/dev/null 2>&1; then
  echo "podman not found. Install with: sudo dnf install -y podman" >&2
  exit 1
fi

# Attempt to fetch dependencies for H2 into backend/target/dependency
echo "2) Fetch backend dependencies (for h2 jar)"
pushd backend >/dev/null
mvn -B -DskipTests dependency:copy-dependencies -DoutputDirectory=target/dependency
popd >/dev/null

# Seed H2 using the repo helper
echo "3) Seed H2 external DB"
./backend/scripts/docker-seed-h2.sh

# Bring up the stack with podman compose
echo "4) Start the stack with podman compose"
podman compose up --build
