# reloader-app

This folder contains a Spring Boot backend (`backend/`) and an Angular frontend (`frontend/`).

Note: a legacy Python script previously provided similar functionality. The Python utility has been deprecated and removed from this repository — the Java backend now implements discovery, list-file generation, queue enqueueing, and email notifications. See `backend/README.md` for configuration and how to trigger discovery (scheduled or POST /api/senders/{id}/discover).

Quick start

Prerequisites
- Java 17+ and Maven
- Node 20+ and npm
- Chrome/Chromium for headless tests

Backend

Build:

```bash
mvn -f reloader-app/backend clean package
```

Frontend

Install deps and run unit tests (Karma/Jasmine):

```bash
cd reloader-app/frontend
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
cd reloader-app/frontend
npm install
npm run e2e
```

In CI the workflow will build the backend, start it in background, and execute Playwright tests against http://localhost:8080.
