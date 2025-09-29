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

Local dev (Docker)
------------------

You can run the full stack (backend + frontend) locally with Docker Compose. The backend is published on host port 8005 by default in the compose file.

```bash
docker compose up --build
```

After the stack is up the backend dev endpoints are available at:

http://localhost:8005/api/


Windows developers
------------------

If you're developing on Windows, the backend includes PowerShell helper scripts and a Windows usage section in `backend/README-DEV.md` (seed and predeploy helpers: `backend/scripts/seed_external_h2.ps1`, `backend/scripts/predeploy_check.ps1`). See that file for copy-paste examples to run the H2 seed helper and predeploy checks in PowerShell.

Developer docs and runbooks for the backend are in `backend/docs/`. See `backend/docs/external-db-runbook.md` for details about external DB opt-in flags and testing guidance.

RHEL 8 / Podman
---------------

If you're running on RHEL 8 (or a similar RHEL/CentOS/Fedora environment) you may prefer `podman` (rootless) instead of Docker. The repo's compose file is compatible with `podman compose` in most cases. Follow these steps:

1. Install Podman and the compose helper (if `podman compose` is not available):

```bash
sudo dnf install -y podman podman-docker
# optionally: pip install podman-compose
```

2. Build and start the stack:

```bash
podman compose up --build
```

3. Seed the H2 external DB (run from the repo root):

```bash
./backend/scripts/docker-seed-h2.sh
```

SELinux tips
------------

- RHEL 8 usually runs with SELinux enabled. If the backend container cannot read or write the host-mounted H2 directory (`./backend/external-h2-data`), add the `:Z` mount option in `docker-compose.yml` for that volume. Example:

```yaml
volumes:
	- ./backend/external-h2-data:/workspace/external-h2-db:Z
```

- Alternatively, change the SELinux context on the host directory:

```bash
sudo chcon -R -t container_file_t ./backend/external-h2-data
```

Port binding and rootless caveats
--------------------------------

- Rootless Podman may have restrictions on binding low-numbered host ports (<1024) without additional privileges. If binding fails for port 80, change the host-side port in `docker-compose.yml` (for example, `8081:80` for the frontend).

- To inspect running containers and ports:

```bash
podman compose ps
```

If you'd like, I can add a short, separate 'Local dev (Docker - Windows)' and 'Local dev (Podman - RHEL8)' header to the top-level README with copyable commands (PowerShell and Bash). Just say the word and I will commit that change.

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
