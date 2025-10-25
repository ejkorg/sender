# reloader-app

This folder contains a Spring Boot backend (`backend/`) and an Angular frontend (`frontend/`).

Note: a legacy Python script previously provided similar functionality. The Python utility has been deprecated and moved to `archive/update_sender_queue.py` for reference — the Java backend now implements discovery, list-file generation, queue enqueueing, and email notifications. See `backend/README.md` for configuration and how to trigger discovery (scheduled or POST /api/senders/{id}/discover).

Quick start

Local quick-start (copy/paste)
------------------------------

Local dev (Docker - Windows)
```powershell
# from repo root (PowerShell)
docker compose up --build
.\backend\scripts\docker-seed-h2.ps1
```

Local dev (Podman - RHEL8)
```bash
# from repo root (RHEL 8)
podman compose up --build
./backend/scripts/docker-seed-h2.sh
```

Helper scripts
--------------

We provide small helpers to automate the common dev flow (fetch deps, seed H2, start compose):

- `scripts/rhel-dev-up.sh` — RHEL/Podman helper. Flags:
	- `-d|--detach` : run `podman compose up` in detached mode
	- `-b|--backend-port PORT` : override backend host port (default 8005)
	- `-f|--frontend-port PORT` : override frontend host port (default 80)

- `scripts/win-dev-up.ps1` — Windows PowerShell helper. Parameters:
	- `-Detach` : run Docker Compose in detached mode
	- `-BackendPort <port>` : override backend host port (default 8005)
	- `-FrontendPort <port>` : override frontend host port (default 80)

Examples:

```bash
# RHEL detached on different ports
./scripts/rhel-dev-up.sh -d -b 9005 -f 8081
```

```powershell
# Windows (PowerShell)
.\scripts\win-dev-up.ps1 -Detach -BackendPort 9005 -FrontendPort 8081
```

Containerized seed (no local Maven/Java)
---------------------------------------

If you don't have Maven or a JDK locally, use the containerized helper which runs a Maven container to fetch dependencies and a Java container to execute the H2 RunScript. From the repository root:

```bash
./scripts/seed-h2-container.sh --db-url jdbc:h2:./backend/external-h2-db
```

This works with Docker or Podman. The script creates a temporary container volume to hold the copied dependencies, runs RunScript against `backend/src/main/resources/external_h2_seed.sql`, then cleans up the temporary volume. The default target file for a file-backed H2 DB is `./backend/external-h2-db`.


Prerequisites
- Java 17+ and Maven
- Node 20+ and npm
- Chrome/Chromium for headless tests

Local run (embedded Tomcat + H2 app DB)
--------------------------------------

The backend includes a "local" Maven profile that runs with embedded Tomcat and uses H2 for the application database. You can also provide external DTP (Oracle) connection entries via YAML or JSON to power the UI dropdown of instances.

Build and run:

```bash
mvn -f backend/pom.xml -Plocal -DskipTests package
java -jar backend/target/reloader-backend-0.0.1-SNAPSHOT.jar
```

Provide connection definitions (YAML preferred for readability):

```yaml
# /path/to/dbconnections.yml
EXTERNAL-qa:
	host: gyqsq-db.onsemi.com:1545:GYQSQ
	user: DATAPORT_USER
	password: "<SECRET>"
	dbType: oracle

EXTERNAL-prod:
	host: prod-db.onsemi.com:1545:GYPSQ
	user: DATAPORT_USER
	password: "<SECRET>"
	dbType: oracle
```

Point the app at the YAML/JSON file:

```bash
# YAML (recommended)
java -Dspring.profiles.active=local \
		 -Dreloader.dbconn.yaml.path=/path/to/dbconnections.yml \
		 -jar backend/target/reloader-backend-0.0.1-SNAPSHOT.jar

# JSON (also supported)
java -Dspring.profiles.active=local \
		 -Dreloader.dbconn.path=/path/to/dbconnections.json \
		 -jar backend/target/reloader-backend-0.0.1-SNAPSHOT.jar
```

Environment suffix conventions for instances

- Use baseKey with an environment suffix to define per-env entries. Supported suffix styles: `baseKey-qa`, `baseKey_qa`, `baseKey.qa` (same for `prod`).
- The backend instances endpoint groups by base key per environment and returns the base key (e.g., `EXTERNAL`) for the UI dropdown.

API endpoints used by the UI

- `GET /api/external/instances?environment=qa|prod` — list available DTP instance base keys for the selected environment.
- `POST /api/senders/{id}/discover` — trigger discovery. The UI passes `environment=qa|prod` and `site=<baseKey>`; the backend resolves the full connection.
- Optional lookups when using a direct instance key:
	- `GET /api/senders/external/locations?connectionKey=<baseKey>&environment=qa`
	- `GET /api/senders/external/dataTypes?connectionKey=<baseKey>&environment=qa`
	- `GET /api/senders/external/testerTypes?connectionKey=<baseKey>&environment=qa`
	- `GET /api/senders/external/testPhases?connectionKey=<baseKey>&environment=qa`

Security

- Do not commit real credentials to the repository. Prefer supplying an external YAML/JSON file via `reloader.dbconn.yaml.path` or `reloader.dbconn.path`.
- For local-only experimentation you can keep a dev-only `dbconnections.yml` under `backend/src/main/resources/` but ensure secrets are not real.

## Authentication: token handling summary

Recommended setup implemented by the frontend/back-end in this repo:

- Access tokens (JWT) are stored in `sessionStorage` on the frontend. This gives persistence across reloads but clears when the browser/tab is closed. The frontend also keeps an in-memory copy while the app is running to reduce repeated storage reads.
- The frontend sends the access token in the Authorization header: `Authorization: Bearer <token>` for API requests. An Angular HTTP interceptor adds this header automatically for requests that require authentication.
- Long-lived sessions should use a refresh token stored in an `httpOnly`, `Secure` cookie (backend responsibility). The frontend calls the `/api/auth/refresh` endpoint with `withCredentials: true` to obtain new access tokens when they expire. The frontend does NOT store refresh tokens in JavaScript-accessible storage.
- On 401 responses the frontend attempts a silent refresh and, if unsuccessful, logs the user out. Logout clears the access token from `sessionStorage` and requests backend logout to invalidate refresh cookies.

Backend CORS configuration is now explicit: allowed origins are read from the `security.allowed-origins` property (comma-separated). This avoids overly permissive wildcard origin patterns and ensures `allowCredentials` works safely. For development, set `security.allowed-origins=http://localhost:4200` (or an appropriate origin) in your application properties or environment.

Backend

Build:

```bash
mvn -f backend clean package
```

Local dev (Docker)
------------------

You can run the full stack (backend + frontend) locally with Docker Compose. The backend is published on host port 8005 by default in the compose file.

```

Local dev (Docker - Windows)
---------------------------

If you're on Windows with Docker Desktop (WSL2 backend) follow the same `docker compose up --build` flow. Use PowerShell to run the included seed helper when you need to populate the H2 external DB:

```powershell
.\backend\scripts\docker-seed-h2.ps1
```

Tomcat / WAR deployment
------------------------

If you plan to deploy the backend as a WAR in Tomcat, see `backend/README-DEV.md` → "Tomcat WAR deployment" for build commands, a `setenv.sh` template, and runtime notes.

Quick Windows examples
----------------------

Open PowerShell (run as your regular dev user; with Docker Desktop/WSL2 running) and from the repository root:

```powershell
# start the stack (foreground)
docker compose up --build

# or start detached and override host ports (backend default 8005, frontend default 80)
$env:BACKEND_HOST_PORT = 9005; $env:FRONTEND_HOST_PORT = 8081
docker compose up --build -d

# seed the H2 external DB (PowerShell helper)
.\backend\scripts\docker-seed-h2.ps1

# use the containerized seeder if you don't have Maven/Java locally
.\scripts\seed-h2-container.sh --db-url jdbc:h2:./backend/external-h2-db
```

Notes
-----
- If you change host ports, the backend API will be available at http://localhost:<BACKEND_HOST_PORT>/api/.
- `seed-h2-container.sh` works with Docker Desktop (and Podman) and requires no local Maven/JDK — it creates a temporary container volume to copy dependencies and runs org.h2.tools.RunScript inside a Java container.

Local dev (Podman - RHEL8)
--------------------------

On RHEL 8 use `podman` (rootless) and `podman compose` to run the stack. The compose file works with Podman in most setups — see the Podman troubleshooting notes below for SELinux and ports.

```bash
podman compose up --build
```

Seed the H2 external DB from the repo root:

```bash
./backend/scripts/docker-seed-h2.sh
```
After the stack is up the backend dev endpoints are available at:

http://localhost:8005/api/


Windows developers
------------------

If you're developing on Windows, the backend includes PowerShell helper scripts and a Windows usage section in `backend/README-DEV.md` (seed and predeploy helpers: `backend/scripts/seed_external_h2.ps1`, `backend/scripts/predeploy_check.ps1`). See that file for copy-paste examples to run the H2 seed helper and predeploy checks in PowerShell.

Developer docs and runbooks for the backend are in `backend/docs/`. See `backend/docs/external-db-runbook.md` for details about external DB opt-in flags and testing guidance.

Windows Quick Start (Oracle XE)
-------------------------------

If you want to quickly run the backend locally on Windows while pointing “external” connections at a local Oracle XE, use this abbreviated flow. For deeper context and alternatives, see `docs/LOCAL_SETUP.md`.

Prereqs
- Oracle XE installed and running (for example: service name `XEPDB1` on port 1521)
- A database user with privileges to read the external source tables you’ll query (see `backend/docs/external-db-runbook.md`)

Steps (PowerShell)

1) Build the backend JAR from the repo root:

```powershell
mvn -f .\backend\pom.xml -DskipTests package
```

2) Create a YAML file with your external connection(s), for example `C:\reloader\dbconnections.yml`:

```yaml
# C:\reloader\dbconnections.yml
EXTERNAL-qa:
	host: localhost:1521/XEPDB1   # service-style URL part for XE
	user: RELOADER                # replace with your Oracle user
	password: "<SECRET>"
	dbType: oracle
```

3) Run the backend pointing at that YAML. The app itself will still use its default local app DB; only external lookups go to Oracle XE:

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
java -Dreloader.dbconn.yaml.path="C:\reloader\dbconnections.yml" -jar .\backend\target\reloader-backend-0.0.1-SNAPSHOT.jar
```

4) Exercise endpoints quickly with Postman. Import these two files:
- `docs/postman/reloader-local.postman_environment.json`
- `docs/postman/reloader-local.postman_collection.json`

Then set the environment variables, send the Login request, and walk through Lookup → Discover → Queue → Run.

5) Optional: run the Angular dev server against the local backend using the included proxy:

```powershell
cd .\frontend
npm ci
ng serve --proxy-config proxy.conf.json
```

Notes
- Prefer service-style Oracle URLs (e.g., `jdbc:oracle:thin:@//host:port/SERVICE`) for XE; the backend will respect full `jdbc:` URLs if provided.
- The database uniqueness constraint on `(session_id, payload_id)` is enforced in production via Liquibase, and tests mirror it with a JPA `@UniqueConstraint`.
- For full end-to-end guidance (including running the app’s primary datasource on Oracle and Liquibase considerations), see `docs/LOCAL_SETUP.md` and `backend/docs/external-db-runbook.md`.

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

Profiles and testing
--------------------

- Production/onsemi runtime uses the `onsemi-oracle` profile to point the backend’s reference DB at Oracle. Run with:

	```bash
	SPRING_PROFILES_ACTIVE=onsemi-oracle \
	RELOADER_DBCONN_PATH=/etc/reloader/dbconnections.json \
	java -jar backend/target/reloader-backend-0.0.1-SNAPSHOT.jar
	```

- Tests/CI never contact Oracle. External connections are forced to H2 by `reloader.use-h2-external=true` (env `RELOADER_USE_H2_EXTERNAL=true`). To run backend tests:

	```bash
	mvn -f backend/pom.xml test
	```

See `backend/README.md` and `backend/docs/external-db-runbook.md` for more about the profile and flags.
