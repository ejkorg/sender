#!/usr/bin/env bash
set -euo pipefail

# Containerized H2 seeder: runs without local Maven/JDK by using a Maven container
# Usage: ./scripts/seed-h2-container.sh --db-url jdbc:h2:./backend/external-h2-db

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
SQL_PATH="$REPO_ROOT/backend/src/main/resources/external_h2_seed.sql"

: ${DB_URL:=jdbc:h2:./backend/external-h2-db}

if [ ! -f "$SQL_PATH" ]; then
  echo "ERROR: SQL seed file not found: $SQL_PATH" >&2
  exit 2
fi

echo "Containerized seed: DB_URL=$DB_URL SQL=$SQL_PATH"

# Create a temporary docker volume to share artifacts between maven and java containers
TEMP_VOL="seed-deps-$(date +%s)"
cleanup() {
  echo "Cleaning up temporary volume $TEMP_VOL"
  docker volume rm -f "$TEMP_VOL" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "Creating temporary volume $TEMP_VOL"
docker volume create "$TEMP_VOL" >/dev/null

echo "Fetching dependencies into temporary volume using Maven image..."
docker run --rm -v "$REPO_ROOT":/workspace -v "$TEMP_VOL":/deps -w /workspace maven:3.9.4-eclipse-temurin-17 \
  mvn -q -f backend/pom.xml dependency:copy-dependencies -DoutputDirectory=/deps || { echo "Maven dependency fetch failed"; exit 3; }

H2_JAR_PATH=$(docker run --rm -v "$TEMP_VOL":/deps busybox sh -c 'ls /deps/h2-*.jar 2>/dev/null | head -n1' || true)
if [ -z "$H2_JAR_PATH" ]; then
  echo "ERROR: H2 jar not found in fetched dependencies" >&2
  exit 4
fi

echo "Found h2 jar inside temp volume: $H2_JAR_PATH"

# Copy the SQL file into a short-lived container and run RunScript using a Java image
echo "Running org.h2.tools.RunScript inside a Java container"
docker run --rm -v "$REPO_ROOT":/workspace -v "$TEMP_VOL":/deps -w /workspace eclipse-temurin:17 \
  java -cp /deps/$(basename "$H2_JAR_PATH") org.h2.tools.RunScript -url "$DB_URL" -script "$SQL_PATH" -user sa -password "" || { echo "RunScript failed"; exit 5; }

echo "Seed completed. H2 DB path: ${DB_URL#jdbc:h2:}"
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
