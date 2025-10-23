# Frontend Migration Plan — Angular Material → Tailwind

Last updated: 2025-10-20
Branch: change-ui-to-material-to-tailwind

This document describes a small, safe, prioritized plan to finish migrating the frontend UI from Angular Material to Tailwind and the project's custom UI primitives (ModalHost, ToastService, TooltipDirective).

Scope & success criteria
- Replace Angular Material components, dialogs, and snackbars with Tailwind-based equivalents and the project's Modal/Toast/Tooltip primitives.
- Remove any direct dependency on `@angular/material` / `@angular/cdk` (if present).
- Keep the app build green and preserve accessibility (keyboard, screen reader hints, aria attributes).
- Provide small, reviewable commits and one PR per logical change when practical.

Contract (tiny)
- Inputs: existing `frontend/src` TypeScript + Angular templates.
- Outputs: templates/components updated to use Tailwind + ModalService/ToastService/TooltipDirective; updated docs and tests.
- Error modes: missing imports for standalone components, template binding errors, styling regressions; build should catch template errors.

High-level steps (ordered, small commits)
1. Repo scan & inventory (done)
   - Search for `@angular/material`, `mat-` selectors, `Mat` services/usages, `title=` attributes (we've completed much of this already).
2. Prepare migration plan (this file) — done.
3. Convert top-priority templates (current task)
   - Targets: dashboard templates (`dashboard.component.*`), dialog templates used by users (duplicate-warning dialog, sender lookup, dashboard detail), and any common UI elements (buttons, badges) that currently use Material classes/components.
   - Strategy per-file: create a small, focused patch that replaces Material tags/attributes with Tailwind markup or uses `ModalService.openComponent(...)` for dialogs.
   - Verification: run `npm run build` after each logical group and spot-check pages in dev server.
4. Replace MatSnackBar usages with `ToastService` (already done in many places). Verify notifications appear.
5. Convert remaining dynamic dialog call-sites to `ModalService.openComponent` (done for the major ones; finish any leftovers).
6. Convert `title` attributes to `[appTooltip]` where appropriate (done).
7. Accessibility pass (keyboard, aria, roles)
   - Use Axe / Lighthouse on the built app; fix any high severity issues (focus order, missing ARIA attributes for custom widgets, contrast issues).
8. Tests
   - Add small unit tests for `TooltipDirective` (hover/focus creates tooltip), and `ModalHost` focus restore.
   - Add a Playwright smoke test for opening a modal and ensuring focus is trapped.
9. Remove Angular Material packages (if any)
   - Inspect `frontend/package.json` and `package-lock.json` / `pnpm-lock.yaml` and remove `@angular/material` / `@angular/cdk` followed by `npm prune` / reinstall.
10. Final manual regression & deploy readiness
   - Run `npm run build` under CI, run e2e tests, and ship a PR for review.

Files to check/modify (examples)
- `frontend/src/app/dashboard/*` — Replace mat-tooling with Tailwind markup, ensure clickable tiles use `role="button"` and `tabindex` for keyboard.
- `frontend/src/app/stepper/*` — Verify dialogs and preview table; duplicate-warning dialog was converted but confirm visuals.
- `frontend/src/app/ui/*` — Confirm `ModalHost`, `ToastContainer`, `tooltip.directive.ts` are exported and used.
- `frontend/src/app/auth/*` — Replace any Material form fields with standard inputs styled with Tailwind.

Edge cases / gotchas
- Standalone components must import directives/components they use (NG8002 binding errors). Use the component's `imports` array to add `TooltipDirective`, `CommonModule`, or other standalone utilities.
- Template contexts change are brittle; make single-file patches and run `ng build` between changes.
- Avoid replacing native `title` attributes used for accessibility or fallback (only replace those used as UI tooltips shown to sighted users).

Testing & verification
- Local quick checks
  - Build: `cd frontend && npm run build`
  - Dev server: `cd frontend && npm start` then exercise pages in a browser.
- Accessibility
  - Install `axe-cli` or use Lighthouse via Chrome DevTools. Example (optional):

    # runs a headless Lighthouse report (needs Chrome available)
    npx lighthouse http://localhost:4200 --quiet --chrome-flags='--headless' --output=json --output-path=./lighthouse-report.json

- E2E
  - `cd frontend && npx playwright test` (there are existing playwright tests configured).

Rollback plan
- Changes are small, per-file commits — revert the specific commit if a regression is found.
- Keep `main` branch untouched; open PRs from `change-ui-to-material-to-tailwind` and iterate based on review feedback.

Timeline (suggested)
- Today: finish plan and begin converting dashboard + duplicate-warning dialog (1–2 hours).
- Next day: finish remaining dialogs and toasts, run accessibility checks (2–4 hours).
- Follow-ups: add tests and remove packages (1–2 days depending on review).

Notes
- `@angular/material` does not appear in `frontend/package.json` (already removed or never used). If you keep CDK overlay intent in the future, consider incremental adoption of `@angular/cdk/overlay` for complex popovers.

If you'd like, I can now start converting the next set of high-priority templates (dashboard and any remaining dialog markup). Reply with "Proceed with conversions" and I'll start making safe, single-file patches and run builds after each group.
