# reloader-app

This folder contains a Spring Boot backend (`backend/`) and an Angular frontend (`frontend/`).

Note: a legacy Python script previously provided similar functionality. The Python utility has been deprecated and moved to `archive/update_sender_queue.py` for reference — the Java backend now implements discovery, list-file generation, queue enqueueing, and email notifications. See `backend/README.md` for configuration and how to trigger discovery (scheduled or POST /api/senders/{id}/discover).

Quick start

Prerequisites
- Java 17+ and Maven
- Node 20+ and npm
- Chrome/Chromium for headless tests

Backend

Build:

```bash
mvn -f backend clean package
```

Windows developers
------------------

If you're developing on Windows, the backend includes PowerShell helper scripts and a Windows usage section in `backend/README-DEV.md` (seed and predeploy helpers: `backend/scripts/seed_external_h2.ps1`, `backend/scripts/predeploy_check.ps1`). See that file for copy-paste examples to run the H2 seed helper and predeploy checks in PowerShell.

Developer docs and runbooks for the backend are in `backend/docs/`. See `backend/docs/external-db-runbook.md` for details about external DB opt-in flags and testing guidance.

Frontend

Install deps and run unit tests (Karma/Jasmine):

```bash
cd frontend
npm ci
# On CI or headless containers point CHROME_BIN to a Chrome binary
CHROME_BIN=/usr/bin/google-chrome-stable npm run test -- --watch=false --browsers=ChromeHeadless
```

Notes
- Playwright was present in the workspace but the Angular project uses Karma/Jasmine for unit tests. If you want E2E, add a separate Playwright e2e/ folder and tests.
- If ChromeHeadless cannot start because your environment uses the snap-wrapped chromium, either install google-chrome-stable.deb or configure CHROME_BIN appropriately.

E2E (Playwright)

To run Playwright end-to-end tests locally:

```bash
cd frontend
npm install
npm run e2e
```

In CI the workflow will build the backend, start it in background, and execute Playwright tests against http://localhost:8080.
