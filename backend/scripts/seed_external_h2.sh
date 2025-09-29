#!/usr/bin/env bash
# Seed a local H2 database with the external queue table/sequence used by tests
# Usage:
#   ./seed_external_h2.sh           # creates ./external-h2-db.mv.db and seeds it with backend/src/main/resources/external_h2_seed.sql
#   DB_URL=jdbc:h2:./mydb ./seed_external_h2.sh
#   DB_FILE=./mydb ./seed_external_h2.sh
# This script will attempt to locate the H2 jar in the local Maven repository; if not found it will download dependencies.

set -euo pipefail
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
SQL_FILE="$REPO_ROOT/src/main/resources/external_h2_seed.sql"
DB_URL=${DB_URL:-jdbc:h2:./external-h2-db}

# Try to locate h2 jar in local maven repo
# Try to find H2 jar in the local maven repo first (~/.m2/repository)
H2_JAR_FILE=$(ls -1 ~/.m2/repository/com/h2database/h2/*/h2-*.jar 2>/dev/null | sort -V | tail -n1 || true)

if [ -z "${H2_JAR_FILE}" ]; then
  echo "H2 jar not found in ~/.m2/repository; fetching dependency to $REPO_ROOT/target/dependency..."
  (cd "$REPO_ROOT" && mvn dependency:copy-dependencies -DincludeGroupIds=com.h2database -DoutputDirectory=target/dependency)
  # pick the first matching jar in the copied dependencies
  H2_JAR_FILE=$(ls -1 "$REPO_ROOT/target/dependency"/h2-*.jar 2>/dev/null | sort -V | tail -n1 || true)
fi

if [ ! -f "$H2_JAR_FILE" ]; then
  echo "Failed to locate h2 jar; looked for: $H2_JAR_FILE"
  exit 1
fi

if [ ! -f "$SQL_FILE" ]; then
  echo "SQL seed file not found: $SQL_FILE"
  exit 1
fi

echo "Seeding H2 DB at $DB_URL using SQL $SQL_FILE"
java -cp "$H2_JAR_FILE" org.h2.tools.RunScript -url "$DB_URL" -script "$SQL_FILE" -user SA -password ""

echo "Seed complete. DB file(s) created in current directory (or in H2's storage)."

echo "You can inspect it with the H2 console: java -cp $H2_JAR_FILE org.h2.tools.Console -url $DB_URL -user SA -password ''"
