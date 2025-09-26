#!/usr/bin/env bash
set -euo pipefail

# predeploy_check.sh
# Usage: predeploy_check.sh --dbtype <oracle|postgres> --user <user> --host <host> --db <db> [--dedupe]
# For Oracle, provide connection as user/password@//host:port/SID in the --connect param instead of separate user/host/db.

DBTYPE=""
USER=""
HOST=""
DB=""
CONNECT=""
DEDUP=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dbtype) DBTYPE="$2"; shift 2;;
    --user) USER="$2"; shift 2;;
    --host) HOST="$2"; shift 2;;
    --db) DB="$2"; shift 2;;
    --connect) CONNECT="$2"; shift 2;;
    --dedupe) DEDUP=true; shift 1;;
    *) echo "Unknown arg $1"; exit 2;;
  esac
done

if [[ -z "$DBTYPE" ]]; then
  echo "--dbtype is required (oracle|postgres)" >&2
  exit 2
fi

SCRIPTDIR="$(dirname "$0")"

if [[ "$DBTYPE" == "postgres" ]]; then
  FIND_SQL="$SCRIPTDIR/find_duplicate_sender_queue.sql"
  DEDUPE_SQL="$SCRIPTDIR/dedupe_sender_queue_keep_lowest_id.sql"
  if [[ -z "$USER" || -z "$HOST" || -z "$DB" ]]; then
    echo "For postgres provide --user, --host and --db" >&2
    exit 2
  fi
  echo "Running duplicate check (postgres)..."
  psql -h "$HOST" -U "$USER" -d "$DB" -f "$FIND_SQL"
  if [[ "$DEDUP" == "true" ]]; then
    echo "Running dedupe (postgres) — ensure you have a backup and run in maintenance window..."
    read -p "Type YES to continue: " CONFIRM
    if [[ "$CONFIRM" == "YES" ]]; then
      psql -h "$HOST" -U "$USER" -d "$DB" -c "BEGIN;" -f "$DEDUPE_SQL" -c "COMMIT;"
      echo "Dedupe completed."
    else
      echo "Aborted by user."; exit 1
    fi
  fi

elif [[ "$DBTYPE" == "oracle" ]]; then
  FIND_SQL="$SCRIPTDIR/find_duplicate_sender_queue_oracle.sql"
  DEDUPE_SQL="$SCRIPTDIR/dedupe_sender_queue_oracle.sql"
  if [[ -z "$CONNECT" ]]; then
    echo "For oracle provide --connect user/password@//host:port/SID via --connect" >&2
    exit 2
  fi
  echo "Running duplicate check (oracle)..."
  sqlplus -s "$CONNECT" "@${FIND_SQL}"
  if [[ "$DEDUP" == "true" ]]; then
    echo "Running dedupe (oracle) — ensure you have a backup and run in maintenance window..."
    read -p "Type YES to continue: " CONFIRM
    if [[ "$CONFIRM" == "YES" ]]; then
      sqlplus -s "$CONNECT" "@${DEDUPE_SQL}"
      echo "Dedupe completed."
    else
      echo "Aborted by user."; exit 1
    fi
  fi
else
  echo "Unsupported dbtype: $DBTYPE" >&2
  exit 2
fi

echo "Pre-deploy check completed."
