#!/usr/bin/env bash
# Containerized H2 seed helper. Useful when you don't have Maven/Java installed locally.
# Usage: ./scripts/seed-h2-container.sh [--db-url JDBC_URL] [--maven-image maven:3.9-eclipse-temurin-17] [--java-image eclipse-temurin:17-jre]
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DB_URL="jdbc:h2:./backend/external-h2-db"
MAVEN_IMAGE="maven:3.9-eclipse-temurin-17"
JAVA_IMAGE="eclipse-temurin:17-jre"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --db-url) DB_URL="$2"; shift 2 ;;
    --maven-image) MAVEN_IMAGE="$2"; shift 2 ;;
    --java-image) JAVA_IMAGE="$2"; shift 2 ;;
    -h|--help) echo "Usage: $0 [--db-url JDBC_URL]"; exit 0 ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

echo "Using DB_URL=$DB_URL"

TMP_VOL="sender-h2-dep-$(date +%s)"
echo "Creating temporary volume $TMP_VOL"
docker volume create "$TMP_VOL"

echo "1) Run Maven container to copy dependencies into volume"
docker run --rm -v "$REPO_ROOT":/workspace -v "$TMP_VOL":/deps -w /workspace/backend "$MAVEN_IMAGE" \
  mvn -B -DskipTests dependency:copy-dependencies -DoutputDirectory=/deps

echo "2) Run Java container to execute RunScript using the H2 jar in the volume"
H2_JAR_PATH=$(docker run --rm -v "$TMP_VOL":/deps "$JAVA_IMAGE" sh -c 'ls /deps | grep h2- | head -n1' )
if [[ -z "$H2_JAR_PATH" ]]; then
  echo "H2 jar not found in dependencies. Check Maven output." >&2
  docker volume rm "$TMP_VOL" || true
  exit 2
fi

echo "Found H2 jar: $H2_JAR_PATH"
docker run --rm -v "$REPO_ROOT":/workspace -v "$TMP_VOL":/deps -w /workspace "$JAVA_IMAGE" \
  java -cp /deps/$H2_JAR_PATH org.h2.tools.RunScript -url "$DB_URL" -script /workspace/backend/src/main/resources/external_h2_seed.sql -user sa -password ''

echo "Cleaning up temporary volume $TMP_VOL"
docker volume rm "$TMP_VOL"

echo "Seed completed. H2 DB at: ${DB_URL#jdbc:h2:}"
