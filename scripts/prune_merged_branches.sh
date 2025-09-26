#!/usr/bin/env bash
set -euo pipefail

# Safe cleanup script to prune remote-tracking refs and remove branches
# that are already merged into the main branch.
#
# Usage examples:
#  Dry-run (safe):
#    ./scripts/prune_merged_branches.sh --dry-run
#  Actually delete remote branches merged into origin/main:
#    ./scripts/prune_merged_branches.sh --execute --delete-remote
#  Also delete local branches merged into main:
#    ./scripts/prune_merged_branches.sh --execute --delete-remote --delete-local

REMOTE=origin
MAIN_BRANCH=main
DRY_RUN=1
DELETE_REMOTE=0
DELETE_LOCAL=0
PROTECT_REGEX='^(main|master|HEAD|develop|release)$'

usage() {
  cat <<EOF
prune_merged_branches.sh - safely remove branches merged into ${REMOTE}/${MAIN_BRANCH}

Options:
  --remote <name>       Remote name (default: origin)
  --main <branch>       Main branch name (default: main)
  --execute             Perform deletions (default is dry-run)
  --delete-remote       Delete merged remote branches on the remote
  --delete-local        Delete merged local branches
  --protect <regex>     Regex of branch names to protect (default: ${PROTECT_REGEX})
  --help                Show this help

Examples:
  # Dry-run (safe):
  ./scripts/prune_merged_branches.sh --dry-run

  # Delete merged remote branches (be careful):
  ./scripts/prune_merged_branches.sh --execute --delete-remote

  # Delete remote and local merged branches:
  ./scripts/prune_merged_branches.sh --execute --delete-remote --delete-local
EOF
}

# Parse args
while [[ $# -gt 0 ]]; do
  case "$1" in
    --remote)
      REMOTE="$2"; shift 2;;
    --main)
      MAIN_BRANCH="$2"; shift 2;;
    --execute)
      DRY_RUN=0; shift;;
    --delete-remote)
      DELETE_REMOTE=1; shift;;
    --delete-local)
      DELETE_LOCAL=1; shift;;
    --protect)
      PROTECT_REGEX="$2"; shift 2;;
    --dry-run)
      DRY_RUN=1; shift;;
    --help|-h)
      usage; exit 0;;
    *)
      echo "Unknown arg: $1"; usage; exit 1;;
  esac
done

echo "Remote: ${REMOTE}"
echo "Main branch: ${MAIN_BRANCH}"
echo "Dry run: ${DRY_RUN}"
echo "Delete remote branches: ${DELETE_REMOTE}"
echo "Delete local branches: ${DELETE_LOCAL}"
echo "Protected branch regex: ${PROTECT_REGEX}"

echo "\nFetching ${REMOTE} and pruning remote-tracking refs..."
git fetch "${REMOTE}" --prune

# Find remote branches merged into REMOTE/MAIN_BRANCH
mapfile -t REMOTE_MERGED < <(git branch -r --merged "${REMOTE}/${MAIN_BRANCH}" \
  | sed 's/^\s*//' \
  | grep "^${REMOTE}/" \
  | sed "s#^${REMOTE}/##" \
  | grep -Ev "${PROTECT_REGEX}" || true)

# Find local branches merged into MAIN_BRANCH
mapfile -t LOCAL_MERGED < <(git branch --merged "${MAIN_BRANCH}" \
  | sed 's/^\s*//' \
  | sed 's/^\* //' \
  | grep -Ev "${PROTECT_REGEX}" || true)

if [[ ${#REMOTE_MERGED[@]} -eq 0 ]]; then
  echo "No remote branches merged into ${REMOTE}/${MAIN_BRANCH} (excluding protected branches)."
else
  echo "Remote branches merged into ${REMOTE}/${MAIN_BRANCH} (candidates):"
  for b in "${REMOTE_MERGED[@]}"; do
    echo "  - ${REMOTE}/${b}"
  done
fi

if [[ ${#LOCAL_MERGED[@]} -eq 0 ]]; then
  echo "No local branches merged into ${MAIN_BRANCH} (excluding protected branches)."
else
  echo "Local branches merged into ${MAIN_BRANCH} (candidates):"
  for b in "${LOCAL_MERGED[@]}"; do
    echo "  - ${b}"
  done
fi

# Confirm and act
if [[ ${DRY_RUN} -eq 1 ]]; then
  echo "\nDry-run mode: no deletions will be performed. Use --execute to actually delete branches."
else
  echo "\nExecute mode: performing deletions as requested..."

  if [[ ${DELETE_REMOTE} -eq 1 && ${#REMOTE_MERGED[@]} -gt 0 ]]; then
    for b in "${REMOTE_MERGED[@]}"; do
      echo "Deleting remote ${REMOTE}/${b}..."
      git push "${REMOTE}" --delete "${b}" || echo "Failed to delete ${REMOTE}/${b}; skipping"
    done
  fi

  if [[ ${DELETE_LOCAL} -eq 1 && ${#LOCAL_MERGED[@]} -gt 0 ]]; then
    for b in "${LOCAL_MERGED[@]}"; do
      # only delete if branch exists locally
      if git show-ref --verify --quiet refs/heads/"${b}"; then
        echo "Deleting local branch ${b}..."
        git branch -d "${b}" || git branch -D "${b}"
      fi
    done
  fi
fi

# Final prune to remove any now-stale remote-tracking refs
echo "\nFinal prune of ${REMOTE} remote-tracking refs..."
git remote prune "${REMOTE}"

echo "Done."
