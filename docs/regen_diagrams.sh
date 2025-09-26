#!/usr/bin/env bash
set -euo pipefail

# Regenerate Mermaid diagrams in docs/diagrams
# Strategy:
# 1) If docker is available, use mermaid CLI in a docker container to render locally.
# 2) Otherwise fall back to kroki.io (remote rendering) using curl.

DIAGRAM_DIR="$(dirname "$0")/diagrams"
MERMAID_IMAGE="mermaid/mermaid-cli:10.4.0"

render_with_kroki() {
  echo "Falling back to kroki.io"
  for f in "$DIAGRAM_DIR"/*.mmd; do
    name=$(basename "$f" .mmd)
    echo "Rendering $name via kroki"
    curl -sS -X POST -H "Content-Type: text/plain" --data-binary @"$f" https://kroki.io/mermaid/svg -o "$DIAGRAM_DIR/${name}.svg"
    curl -sS -X POST -H "Content-Type: text/plain" --data-binary @"$f" https://kroki.io/mermaid/png -o "$DIAGRAM_DIR/${name}.png"
  done
  echo "Done (kroki)"
}

if command -v docker >/dev/null 2>&1; then
  echo "Docker available: attempting to render with $MERMAID_IMAGE"
  # Try pull first
  if docker pull "$MERMAID_IMAGE" >/dev/null 2>&1; then
    for f in "$DIAGRAM_DIR"/*.mmd; do
      name=$(basename "$f" .mmd)
      echo "Rendering $name with docker"
      if ! docker run --rm -v "$PWD":"/data" -w "/data/$DIAGRAM_DIR" "$MERMAID_IMAGE" -i "$name.mmd" -o "$name.svg"; then
        echo "docker run failed for $name, falling back to kroki"
        render_with_kroki
        exit 0
      fi
      if ! docker run --rm -v "$PWD":"/data" -w "/data/$DIAGRAM_DIR" "$MERMAID_IMAGE" -i "$name.mmd" -o "$name.png"; then
        echo "docker run failed for $name (png), falling back to kroki"
        render_with_kroki
        exit 0
      fi
    done
    echo "Done (docker)"
    exit 0
  else
    echo "docker pull failed for $MERMAID_IMAGE"
    render_with_kroki
    exit 0
  fi
else
  render_with_kroki
  exit 0
fi
