# Regenerate diagrams

This repo keeps Mermaid sources in `docs/diagrams/*.mmd` and generated images in the same folder.

Two ways to regenerate diagrams:

1) Using Docker (recommended, offline):

```bash
# from repository root
chmod +x docs/regen_diagrams.sh
docs/regen_diagrams.sh
```

This script will detect Docker and run a `mermaid-cli` container to generate both SVG and PNG files.

2) Fallback (remote rendering):

If Docker is not available, the script will POST the Mermaid sources to kroki.io to generate SVG and PNG. This requires network connectivity and sends the diagram text to kroki.

Notes
- If you prefer full offline build without Docker, install `@mermaid-js/mermaid-cli` and headless Chrome with required libs, then run `npx mmdc -i ... -o ...`.
- The Docker image used is `haydenbleasel/mermaid-cli:8.11.0`. Adjust the tag in `docs/regen_diagrams.sh` if you prefer a different mermaid-cli version.

Security
- Using kroki sends diagram text to a third-party service. Avoid sending secrets or private data in the Mermaid files if you use kroki.
