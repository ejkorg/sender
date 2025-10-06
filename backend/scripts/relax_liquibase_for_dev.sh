#!/usr/bin/env bash
# relax_liquibase_for_dev.sh
# Dev helper: temporarily replace onFail="HALT" with onFail="MARK_RAN" in changelogs,
# optionally run Liquibase update, and restore files.
# WARNING: This is for LOCAL DEVELOPMENT ONLY. Do NOT run in production.

set -euo pipefail
REPO_ROOT=$(cd "$(dirname "$0")/.." && pwd)
CHANGELOG_DIR="$REPO_ROOT/src/main/resources/db/changelog"
FILES=(
  "db.changelog-1.0.xml"
  "db.changelog-1.1-add-load-session-payload-remote-fields.xml"
  "db.changelog-2.0-add-unique-session-payload.xml"
)
BACKUP_DIR="$REPO_ROOT/target/liquibase_changelog_backup_$(date +%s)"
RUN_LIQUIBASE=${RUN_LIQUIBASE:-false}

if [ "${CI:-}" = "true" ]; then
  echo "This script is for local development only. Aborting in CI environment." >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"

echo "Backing up changelogs to $BACKUP_DIR"
for f in "${FILES[@]}"; do
  cp "$CHANGELOG_DIR/$f" "$BACKUP_DIR/"
done

echo "Patching changelogs: HALT -> MARK_RAN (dev only)"
for f in "${FILES[@]}"; do
  sed 's/onFail="HALT"/onFail="MARK_RAN"/g' "$CHANGELOG_DIR/$f" > "$CHANGELOG_DIR/$f.tmp" && mv "$CHANGELOG_DIR/$f.tmp" "$CHANGELOG_DIR/$f"
done

if [ "$RUN_LIQUIBASE" = "true" ]; then
  echo "Running Liquibase update (development)"
  mvn -f "$REPO_ROOT/pom.xml" -pl :reloader-backend liquibase:update
  echo "Liquibase update finished"
else
  echo "Skipped running Liquibase. To run, set RUN_LIQUIBASE=true in the environment."
fi

read -p "Press Enter to restore original changelogs (or Ctrl-C to abort)"

echo "Restoring original changelogs from backup"
for f in "${FILES[@]}"; do
  mv "$BACKUP_DIR/$f" "$CHANGELOG_DIR/$f"
done

echo "Cleanup: removing backup dir $BACKUP_DIR"
rm -rf "$BACKUP_DIR"

echo "Done. Remember: this helper is only for quick local iteration. For production use keep onFail=HALT."
