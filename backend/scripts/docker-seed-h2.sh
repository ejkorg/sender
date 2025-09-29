#!/usr/bin/env bash
set -euo pipefail

# Seed a local H2 database file using the external_h2_seed.sql from resources
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
SEED_SQL="$REPO_ROOT/src/main/resources/external_h2_seed.sql"

: ${DB_URL:=jdbc:h2:./external-h2-db}
: ${H2_JAR:=""}

echo "Seed H2 DB: $DB_URL"

# Try to locate H2 jar in local maven repo first
if [ -z "$H2_JAR" ]; then
  MAVEN_REPO="$HOME/.m2/repository"
  H2_JAR_CANDIDATE=$(find "$MAVEN_REPO" -type f -name "h2-*.jar" | head -n 1 || true)
  if [ -n "$H2_JAR_CANDIDATE" ]; then
    H2_JAR="$H2_JAR_CANDIDATE"
  fi
fi

if [ -z "$H2_JAR" ]; then
  echo "H2 jar not found in local Maven repo. Running 'mvn -f backend/pom.xml dependency:copy-dependencies' to fetch dependencies..."
  (cd "$REPO_ROOT" && mvn -q -f backend/pom.xml dependency:copy-dependencies -DoutputDirectory=target/dependency)
  H2_JAR=$(ls -1 "$REPO_ROOT/target/dependency"/h2-*.jar 2>/dev/null | head -n 1 || true)
fi

if [ -z "$H2_JAR" ]; then
  echo "ERROR: Unable to locate h2 jar. Please install dependencies with Maven or set H2_JAR to the path of an h2 jar." >&2
  exit 2
fi

echo "Using H2 jar: $H2_JAR"

JAVA_CMD=${JAVA_CMD:-java}
echo "Running RunScript against $DB_URL using $SEED_SQL"

if [[ "$DB_URL" == jdbc:h2:./* ]]; then
  mkdir -p "$REPO_ROOT/external-h2-data"
fi

"$JAVA_CMD" -cp "$H2_JAR" org.h2.tools.RunScript -url "$DB_URL" -script "$SEED_SQL" -user sa -password "" \
  && echo "Seed completed. H2 DB at: ${DB_URL#jdbc:h2:}" || { echo "RunScript failed"; exit 3; }
