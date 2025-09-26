# Changelog

All notable changes to this project will be documented in this file.

## Unreleased (2025-09-26)

### Added
- docs: new sequence diagram `docs/diagrams/full_app_flow.mmd` illustrating full UI → backend → DB → external service flow (discovery/enqueue, background processing, monitoring/feedback, and notifications).
- docs: updated architecture notes in `docs/ARCHITECTURE.md` and `backend/README.md` documenting authentication endpoints and refresh cookie rotation behavior.
- docs: regenerated diagram renders (PNG/SVG) for `sequence` and `system_flow`.

### Changed
- docs: consolidated and aligned endpoint paths in diagrams to use `/api/senders/{id}/enqueue` and `/api/senders/{id}/queue`.

### Notes
- No runtime code behavior changed; this PR contains documentation and artifact updates only.
