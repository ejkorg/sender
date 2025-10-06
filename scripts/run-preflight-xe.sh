#!/usr/bin/env bash
set -euo pipefail

# Helper: run the preflight duplicate-report SQL against a local oracle-xe container
# Usage: ./run-preflight-xe.sh [output-file]

OUT=${1:-scripts/artifacts/preflight-$(date +%Y%m%d-%H%M%S).txt}
DIR=$(dirname "$OUT")
mkdir -p "$DIR"

echo "Copying preflight SQL to container..."
docker cp scripts/preflight-report-duplicates-oracle.sql oracle-xe:/tmp/preflight-report-duplicates-oracle.sql

echo "Running preflight in container and saving to $OUT"
docker exec -i oracle-xe bash -lc "echo exit | sqlplus sys/Password123@//localhost:1521/XEPDB1 as sysdba @/tmp/preflight-report-duplicates-oracle.sql" > "$OUT" 2>&1

echo "Output written to $OUT"
