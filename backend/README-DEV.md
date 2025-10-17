Backend development notes
=========================

This file documents a couple of important runtime flags and how to run the backend tests when exercising the "external DB" write paths during local development.

Quick links
-----------

- Windows Quick Start (Oracle XE): see the top-level `README.md` → "Windows Quick Start (Oracle XE)" for a copy-paste flow to run the backend locally and point external lookups at Oracle XE. It also links to the Postman collection/environment and the Angular dev proxy.
- Full local setup with Oracle and end-to-end guidance: `docs/LOCAL_SETUP.md`

Important flags
---------------

- reloader.use-h2-external (property) / RELOADER_USE_H2_EXTERNAL (env)
  - When true the application will treat an H2 database as the "external" DB used for push tests. This is opt-in for local/dev use. Default: false.

- external-db.allow-writes (property) / EXTERNAL_DB_ALLOW_WRITES (env)
  - Safety guard. Must be true to allow the application to perform writes against the configured external DB. Default: false.

Why both flags
--------------
We require both flags to avoid accidental writes to production-like external databases. Set `reloader.use-h2-external` when you want to run tests locally using the in-memory H2 external DB schema. Then set `external-db.allow-writes` to actually allow write operations during those tests.

H2 seed file
------------
The H2 schema and seed used when `reloader.use-h2-external=true` are located at:

  backend/src/main/resources/external_h2_seed.sql

This seed file must include the external queue table and sequence used by tests (DTP_SENDER_QUEUE_ITEM and DTP_SENDER_QUEUE_ITEM_SEQ).

Run tests locally (external write paths)
--------------------------------------

From the repository root run:

```bash
RELOADER_USE_H2_EXTERNAL=true EXTERNAL_DB_ALLOW_WRITES=true \
  mvn -f backend/pom.xml -DskipITs=true test
```

Maven profile shortcuts
-----------------------

You can use the Maven profiles defined in `backend/pom.xml` to make running these scenarios more convenient.

- Use the `dev` profile to enable the in-memory H2 external DB, enable writes, and activate the Spring `dev` profile:

```bash
mvn -f backend/pom.xml -Pdev -DskipITs=true test
```

- Use the `external-oracle` profile when you want to run tests or manual checks against a real Oracle external DB. This profile does not provide credentials; pass them via `-D` or environment variables. Example (replace placeholders):

```bash
mvn -f backend/pom.xml -Pexternal-oracle -DskipITs=true \
  -Dexternal.db.username=MYUSER -Dexternal.db.password=MYSECRET -Dexternal.db.url=jdbc:oracle:thin:@//host:1521/DBSERVICE test
```

Note: For CI or automated runs prefer injecting credentials via your CI secret store or environment variables rather than on the command line.

Notes
-----
- The env vars above map to Spring properties `reloader.use-h2-external` and `external-db.allow-writes`. You can also provide them via `-D` when invoking Maven or via your IDE run configuration.
- Keep these flags off/false in CI/pipelines unless you explicitly want to run external-write tests there.
- If tests that exercise the external DB fail with SQL exceptions about missing tables or sequences, verify `external_h2_seed.sql` is present and correct.
Developer notes — enabling dev-only endpoints

This file documents how to enable the dev-only REST endpoints in the backend during development or controlled testing. These endpoints are annotated with @Profile("dev") and will only be available when the Spring "dev" profile is active.

Warning: Do NOT enable the dev profile in production. The dev endpoints expose schema information and include a helper write endpoint which can create a minimal DTP_SENDER_QUEUE_ITEM table. Use these only in isolated development or test environments.

How to enable the dev profile

1) Local run (Linux/macOS)

Export the environment variable and start the application:

```bash
export SPRING_PROFILES_ACTIVE=dev
java -jar target/reloader-backend-0.0.1-SNAPSHOT.jar
```

Or in one line:

```bash
SPRING_PROFILES_ACTIVE=dev java -jar target/reloader-backend-0.0.1-SNAPSHOT.jar
```

2) systemd unit example

If you run the service with systemd for a test server, add the environment to the unit file:

```
[Service]
Environment=SPRING_PROFILES_ACTIVE=dev
ExecStart=/usr/bin/java -jar /opt/reloader/reloader-backend.jar
```

3) Docker example

Set the environment variable when running the container:

```bash
docker run -e SPRING_PROFILES_ACTIVE=dev -p 8005:8080 --name reloader reloader-backend:latest
```

4) Kubernetes example

Add the env var to the deployment spec:

```yaml
env:
  - name: SPRING_PROFILES_ACTIVE
    value: "dev"
```

5) Quick curl examples (after enabling the dev profile)

Inspect external DB (safe read-only information):

```bash
curl -s -H "Authorization: Bearer <JWT>" \
  "http://localhost:8005/api/dev/db-inspect?site=default&environment=qa" | jq
```

Create minimal sender queue table (DEV-ONLY, creates table if missing):

```bash
curl -s -X GET -H "Authorization: Bearer <JWT>" \
  "http://localhost:8005/api/dev/create-external-queue-table?site=default&environment=qa" | jq
```

Notes and best practices

- Prefer database migrations (Liquibase/Flyway) or startup runners for schema/setup instead of runtime HTTP write helpers.
- Restrict access to dev servers via network controls (VPN, firewall) and do not expose them publicly.
- If you need temporary dev access on a shared server, consider using a short-lived feature branch or a separate ephemeral environment.

If you want, I can add a short paragraph to your main README pointing to this doc.

---

H2 as the dev "external" database (opt-in)

For convenience the project can use an H2 in-memory database as the external database used by discovery and the dev helpers. Because production external databases are real, potentially shared systems, any code that performs DDL (CREATE TABLE) or writes to the external queue is guarded and requires an explicit opt-in in development.

To enable the H2 external DB behavior set one of the following (the value must be "true") before running the app in the dev profile:

- Environment variable: RELOADER_USE_H2_EXTERNAL=true
- JVM system property: -Dreloader.use-h2-external=true

When this flag is not set to true the dev helpers that might execute DDL will log and skip the operation. This prevents accidental schema changes on real external databases during local development or tests.

Prefer the environment variable in local shells and CI scripts. If you'd like, I can add an example to the Docker or Kubernetes snippets above showing how to set the flag.

External DB write gating and per-site vendor hints
--------------------------------------------------

Two additional runtime flags affect how the application interacts with external databases:

- `external-db.allow-writes` (boolean, default: false)
  - When set to `true` (for example `EXTERNAL_DB_ALLOW_WRITES=true`), the application is allowed to perform write operations against configured external databases. This is an intentional safety gate so CI/dev environments don't accidentally write to production-like databases.

- Per-site `dbType`/`type`/`dialect` hint in `dbconnections.json`
  - The optional `dbType` key (also accepted as `type` or `dialect`) can be included per-connection in `dbconnections.json` to force vendor detection. Example values: `oracle`, `h2`, `postgres`.
  - When present, the application will prefer this hint over runtime JDBC metadata for branch-specific behavior (for example, Oracle sequence-based insert paths).

Example `dbconnections.json` snippet (see `src/main/resources/dbconnections.json.example` for a full example):

```json
{
  "SAMPLE": {
    "host": "mydb-host.example.com/DBSERVICE",
    "user": "DB_USER",
    "password": "REPLACE_WITH_SECRET",
    "port": "1521",
    "dbType": "oracle"
  }
}
```

Use these flags carefully in CI and development. Prefer using dedicated test databases for integration tests and set `external-db.allow-writes` only when the external DB is intentionally a test instance.

Test flags for running tests that hit external-write paths
--------------------------------------------------------

Some automated tests intentionally exercise the external write and push paths (for example, tests that validate SQLState classification, generated-key fallbacks, or the backoff/retry behavior). Those tests run against an in-memory H2 "external" instance and must opt-in to the runtime guards that normally prevent accidental writes. To run those tests locally or in CI, set both flags to true for the JVM/test run:

- `RELOADER_USE_H2_EXTERNAL=true` (or `-Dreloader.use-h2-external=true`) — enables the in-memory H2 external DB helpers.
- `EXTERNAL_DB_ALLOW_WRITES=true` (or `-Dexternal-db.allow-writes=true`) — allows the application to perform writes against the configured external DBs.

Example (Unix shell) when running Maven tests:

```bash
RELOADER_USE_H2_EXTERNAL=true EXTERNAL_DB_ALLOW_WRITES=true mvn -f backend/pom.xml -DskipITs=true test
```

Set these with care in shared CI runners. Prefer scoped CI job steps using dedicated test databases or ephemeral runners so actual production systems are never affected.

---

Run against Oracle RefDB (staging) and Oracle DTP
--------------------------------------------------

Use the `oracle` Spring profile and provide RefDB credentials via environment variables. Supply your DTP connection entries via an external YAML and point the app to it.

1) RefDB environment variables:

```bash
export REFDB_HOST=your-refdb-host
export REFDB_PORT=1521
export REFDB_SERVICE=ORCLPDB1   # or set REFDB_SID instead
export REFDB_USER=RELOADER
export REFDB_PASSWORD=******
```

2) External DTP entries (YAML file), e.g. `/etc/reloader/dbconnections.yml`:

```yaml
EXTERNAL_QA:
  host: dtp-host.example.com:1521/DTPSVC
  user: DTP_APP
  password: secret
  dbType: oracle
EXTERNAL_PROD:
  host: dtp-prod.example.com:1521/PRODSVC
  user: DTP_APP
  password: secret
  dbType: oracle
```

Point the app to that file:

```bash
export RELOADER_DBCONN_YAML_PATH=file:/etc/reloader/dbconnections.yml
```

3) Start the backend with the Oracle profile:

```bash
mvn -f backend/pom.xml -DskipTests spring-boot:run -Dspring-boot.run.profiles=oracle
```

Tuning and notes:
- Pool sizing for RefDB: `REFDB_POOL_MAX`, `REFDB_POOL_MIN_IDLE`, etc. For DTP pools: `DTP_POOL_MAX`, `DTP_CONN_TIMEOUT_MS`, etc. See `application-oracle.yml`.
- Ensure the Oracle JDBC driver is available to Maven. If using a private repository, configure settings.xml appropriately.
- Keep `RELOADER_USE_H2_EXTERNAL` unset or false so the app uses real Oracle for DTP.

CI workflow for external-write tests
------------------------------------

We've added a GitHub Actions workflow that runs the test suite with the H2 "external" DB and external writes enabled. The workflow lives at `.github/workflows/external-write-tests.yml` and is configured to run on pushes to `feature/**` and `work/**`, and manually via the Actions UI.

Why this helps:
- Validates push/retry/backoff behavior and SQLState classification against an in-memory external DB without touching production databases.

Run the same job locally (Unix shell):

```bash
RELOADER_USE_H2_EXTERNAL=true EXTERNAL_DB_ALLOW_WRITES=true mvn -f backend/pom.xml -DskipITs=true test
```

Exact property names & where they're read (code references)
----------------------------------------------------------

For precision in the README, here are the exact property names and the places in the code that read them:

- reloader.use-h2-external / RELOADER_USE_H2_EXTERNAL
  - Read via: com.onsemi.cim.apps.exensio.dearchiver.config.ConfigUtils.getBooleanFlag(env, "reloader.use-h2-external", "RELOADER_USE_H2_EXTERNAL", false)
  - Example code locations:
    - `com.onsemi.cim.apps.exensio.dearchiver.boot.DevExternalDbInitRunner` — the dev runner checks this flag before attempting to create the minimal external table.
    - `com.onsemi.cim.apps.exensio.dearchiver.service.SessionPushService` — used to decide whether to take the H2 insert path and the generated-keys behavior.

- external-db.allow-writes / EXTERNAL_DB_ALLOW_WRITES
  - Read via: com.onsemi.cim.apps.exensio.dearchiver.config.ConfigUtils.getBooleanFlag(env, "external-db.allow-writes", "EXTERNAL_DB_ALLOW_WRITES", false)
  - Example code locations:
    - `com.onsemi.cim.apps.exensio.dearchiver.service.SessionPushService` — the push path throws IllegalStateException if writes are not allowed.
    - `com.onsemi.cim.apps.exensio.dearchiver.service.SenderService` — guarded when the service may perform external writes.

Helpful test annotations
------------------------

Several tests opt into the H2-external + external-writes behavior using Spring test annotations, for example:

```java
@TestPropertySource(properties={"reloader.use-h2-external=true","external-db.allow-writes=true"})
```

Look for these annotations in `backend/src/test/java/com/onsemi/cim/apps/exensio/dearchiver/service` to find the tests that exercise remote-write code paths.

Troubleshooting
---------------

If an external-write test fails or you see SQL errors when running with the H2 external DB, try the following checklist in order:

1) Confirm you set the flags and (optionally) the dev profile

   - Environment variables (recommended for interactive shells):

```bash
export RELOADER_USE_H2_EXTERNAL=true
export EXTERNAL_DB_ALLOW_WRITES=true
export SPRING_PROFILES_ACTIVE=dev    # optional: allows DevExternalDbInitRunner dev HTTP helpers and runner
```

   - Or pass as JVM properties to Maven or Java:

```bash
mvn -f backend/pom.xml -Dreloader.use-h2-external=true -Dexternal-db.allow-writes=true -Dspring.profiles.active=dev test
```

2) Check DevExternalDbInitRunner output

   - When `reloader.use-h2-external` is false the runner will log:
     "DevExternalDbInitRunner: skipping external DDL because RELOADER_USE_H2_EXTERNAL is not true"

   - When enabled and running with the `dev` profile, the runner will attempt to create a minimal `DTP_SENDER_QUEUE_ITEM` table. If the runner logs a creation failure, check the stack trace for permission/connection details.

3) Verify `external_h2_seed.sql` contains the expected DDL

   - The tests and the H2 external helpers expect `DTP_SENDER_QUEUE_ITEM` and (when appropriate) `DTP_SENDER_QUEUE_ITEM_SEQ` to exist. If you see errors like "Table "DTP_SENDER_QUEUE_ITEM" not found" inspect `backend/src/main/resources/external_h2_seed.sql` and ensure it creates the table and sequence.

4) If you see unique constraint errors in logs during tests, those are often intentional test scenarios asserting that SQL constraint violations are classified as SKIPPED. The code classifies such errors when either:

   - the thrown exception is `java.sql.SQLIntegrityConstraintViolationException`, or
   - the `SQLException#getSQLState()` string starts with `23` (SQL integrity constraint class).

   These cases are handled in `SessionPushService` and are considered expected in specific tests (search for `Constraint violation pushing payload` in logs from tests).

5) When generated keys are not returned by the driver

   - The push code attempts `PreparedStatement.getGeneratedKeys()` and falls back to a `SELECT id FROM DTP_SENDER_QUEUE_ITEM ... ORDER BY record_created DESC` with a small time window if no key is returned. If you hit a failure here, make sure the H2 driver supports generated keys (the tests run with H2 in this repo and the fallback is exercised in tests).

6) Connection / vendor detection

   - The service uses two sources to detect vendor behavior:
     - Per-site `dbType`/`type`/`dialect` hint in `dbconnections.json` (see `src/main/resources/dbconnections.json.example`).
     - Fallback to JDBC `DatabaseMetaData.getDatabaseProductName()`.

   - If vendor detection fails or is misconfigured you may see the generic insert path used; that is expected and covered by tests (generated-key fallback test and Oracle-sequence test).

Quick local fix: create the external table manually in H2

If you need a fast manual fix while iterating, run a small Java snippet or H2 console against the in-memory DB used by tests, or add the DDL to `backend/src/main/resources/external_h2_seed.sql` (the test-runner will pick it up when `reloader.use-h2-external=true`). Example DDL (H2-friendly):

```sql
CREATE TABLE IF NOT EXISTS DTP_SENDER_QUEUE_ITEM (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  id_metadata VARCHAR(255),
  id_data VARCHAR(255),
  id_sender INT,
  record_created TIMESTAMP
);
CREATE SEQUENCE IF NOT EXISTS DTP_SENDER_QUEUE_ITEM_SEQ START WITH 1;
```

If you want, I can also add a small helper script under `backend/scripts/` to run the SQL seed against a running H2 instance for manual testing.

Tomcat WAR deployment
---------------------

You can build the backend either as a runnable JAR (default) or as a WAR suitable for deployment to an external servlet container (Tomcat). The repository includes a Maven `tomcat` profile that produces a WAR while keeping the default jar flow intact.

Build commands

```bash
# runnable jar (default)
mvn -f backend clean package

# war for Tomcat (uses -Ptomcat profile)
mvn -f backend -Ptomcat clean package
```

Tomcat `setenv.sh` example

Create `$CATALINA_BASE/bin/setenv.sh` (ensure it is executable) and add JVM options and app-specific system properties. Example below sets the external H2 DB path and increases heap for the containerized environment:

```bash
#!/bin/sh
JAVA_OPTS="-Xms512m -Xmx1g -Dspring.profiles.active=prod \
  -Dreloader.use-h2-external=true \
  -Dexternal.db.allow-writes=true \
  -Dexternal.h2.path=/opt/reloader/external-h2-db"
export JAVA_OPTS

# Optionally set CATALINA_OPTS for Tomcat-specific runtime flags
CATALINA_OPTS="-Djava.security.egd=file:/dev/./urandom"
export CATALINA_OPTS
```

Deployment notes

- Use Tomcat 10.1+ (required for Spring Boot 3 / Jakarta EE namespaces).
- Deploy the WAR by copying `target/reloader-backend-<version>.war` into `$CATALINA_BASE/webapps/` or use the Tomcat manager to upload.
- If you need the app on the root context, rename the WAR to `ROOT.war` before deployment.
- Ensure the Tomcat user has read/write permission to any host directories referenced by `external.h2.path` or other file-backed storage.
- If your environment uses SELinux, ensure proper labels on mounted volumes (or use appropriate container entitlements when running Tomcat in a container).

Troubleshooting

- If you see `ClassNotFoundException` for `javax.*` classes, you are likely running an older Tomcat (e.g., Tomcat 9). Upgrade to Tomcat 10.1+ which implements Jakarta namespaces used by Spring Boot 3.
- If the WAR still contains an embedded Tomcat (port conflict or duplicate server), ensure the `tomcat` Maven profile is active and that `spring-boot-starter-tomcat` is provided-scoped in that profile.

Want me to commit a `setenv.sh` template under `backend/examples/` and add a short section in the top-level `README.md` linking to this doc? I can commit that change next.

Seed helper script
------------------

A small convenience script is included to seed a local H2 database with the external queue schema used by tests:

- Path: `backend/scripts/seed_external_h2.sh`
- Default DB URL: `jdbc:h2:./external-h2-db`

Usage (from repository root):

```bash
bash backend/scripts/seed_external_h2.sh
```

The script will attempt to locate an H2 jar in your local Maven cache and fall back to copying the project's dependencies into `target/dependency` before invoking H2's RunScript tool against `backend/src/main/resources/external_h2_seed.sql`.

Where to look next in the code
------------------------------

- `com.onsemi.cim.apps.exensio.dearchiver.config.ConfigUtils` — helper used across the codebase to read flags with a primary property and fallback env/property name.
- `com.onsemi.cim.apps.exensio.dearchiver.boot.DevExternalDbInitRunner` — dev-only runner that can create the minimal table when `reloader.use-h2-external=true` and `dev` profile is active.
- `com.onsemi.cim.apps.exensio.dearchiver.service.SessionPushService` — main push logic (oracle fast-path, generic path, generated-key fallback, SKIPPED classification, backoff/retry).
- `backend/src/main/resources/dbconnections.json.example` — per-site vendor hints example.

If you'd like I can:

- Add the quick helper script to seed H2 automatically (low-risk) under `backend/scripts/`.
- Add a short pointer in the project's top-level `README.md` linking to this DEV doc.
If you'd like I can also add a short badge or a targeted workflow that runs on PR to `main` once we're comfortable with the behavior.

Temporary dev helper: relax Liquibase preConditions
-----------------------------------------------

When iterating locally you may want to relax the `onFail="HALT"` preConditions so Liquibase marks changeSets as run in dev environments where the schema was created by other means. To support this workflow safely a helper script is provided under `backend/scripts`:

  - `backend/scripts/relax_liquibase_for_dev.sh`

Usage (local development only):

```bash
# Temporarily replaces onFail="HALT" with onFail="MARK_RAN" in the main changelogs,
# optionally runs Liquibase update if RUN_LIQUIBASE=true is set, then restores originals.
bash backend/scripts/relax_liquibase_for_dev.sh

# To also run Liquibase update during the relaxed window:
RUN_LIQUIBASE=true bash backend/scripts/relax_liquibase_for_dev.sh
```

Important safety notes:

- This script is intended for local development and refuses to run in CI (it checks the `CI` env var).
- Do NOT run this script in production environments. The repository now uses `onFail="HALT"` in changelogs to ensure deployments fail fast if schema objects are missing.
- The helper backs up the original changelog files and restores them after you confirm (it will prompt you to press Enter). If you cancel restoration, restore them manually from the backup directory created under `target/`.

If you prefer an environment-driven toggle instead of editing files, use Liquibase contexts (for example, add a secondary changelog include guarded by `context="dev"`) or wrap the helper script above in your tooling. Liquibase validates changelog XML before property substitution, so attributes such as `onFail` and `onError` must always be literal values (`HALT`, `WARN`, `CONTINUE`, or `MARK_RAN`).

Windows (PowerShell) usage
---------------------------

If you're developing on Windows or prefer PowerShell, equivalents of the convenience scripts are provided in `backend/scripts/`:

- `seed_external_h2.ps1` — PowerShell seed helper (same behavior as the shell script). Example usage (PowerShell):

```powershell
# From repository root (PowerShell)
.\backend\scripts\seed_external_h2.ps1
# You can set DB_URL in the session if you want a different file:
$env:DB_URL='jdbc:h2:./mydb'; .\backend\scripts\seed_external_h2.ps1
```

- `predeploy_check.ps1` — PowerShell predeploy check wrapper. Example usage:

```powershell
.\backend\scripts\predeploy_check.ps1 -dbtype postgres -user dbuser -host dbhost -db reloaderdb
```

Notes:

- These PowerShell scripts assume `psql` (for Postgres) or `sqlplus` (for Oracle) are available on your PATH if you invoke the predeploy check against those databases.
- The PowerShell seed helper will attempt to locate H2 in `%USERPROFILE%\.m2\repository` and otherwise runs `mvn dependency:copy-dependencies` (so you need Maven and Java on PATH).
- If you'd like, I can add `.ps1` examples to the top-level README and to CI docs.

````

Docker & Podman — local containerized development
-----------------------------------------------

This project includes a simple multi-stage `Dockerfile` for building the backend and a `docker-compose.yml` that launches the backend for local development. The compose file is intentionally simple and enables the H2 "external" DB in dev mode so you can exercise discovery and enqueue/push paths without an external database.

Files added:

- `backend/Dockerfile` — multi-stage Dockerfile: build with Maven, run on a small JRE base image.
- `docker-compose.yml` — compose file that builds the backend image and exposes port 8080. It configures the environment variables to enable the H2 external DB and the Spring `dev` profile by default.

Quick Docker build & run (Linux / macOS / Windows with Docker Desktop):

1) Build the image:

```bash
docker build -f backend/Dockerfile -t reloader-backend:local .
```

2) Run the container (enables H2 external DB and dev profile):

```bash
docker run -e RELOADER_USE_H2_EXTERNAL=true -e EXTERNAL_DB_ALLOW_WRITES=true -e SPRING_PROFILES_ACTIVE=dev -p 8080:8080 --name reloader-local reloader-backend:local
```

Or use docker-compose (recommended for local dev convenience):

```bash
docker compose up --build
```

Notes:

- The compose file mounts `./backend/external-h2-data` to persist any file-backed H2 database files. If you prefer an ephemeral in-memory DB, you can remove the volume and use `jdbc:h2:mem:` style URLs in your `dbconnections.json` or runtime properties.
- The container exposes port 8080 and runs the app jar as `java -jar /app/app.jar`.

Podman and RHEL 8 notes
-----------------------

On RHEL 8 or other SELinux-enabled systems it's common to prefer `podman` over `docker`. The same `Dockerfile` works with `podman build` and `podman run`.

Example (build and run with podman):

```bash
podman build -f backend/Dockerfile -t reloader-backend:local .
podman run --name reloader-local -p 8080:8080 -e RELOADER_USE_H2_EXTERNAL=true -e EXTERNAL_DB_ALLOW_WRITES=true -e SPRING_PROFILES_ACTIVE=dev reloader-backend:local
```

SELinux volume considerations

If you bind-mount a host directory into the container (for example to persist an H2 file), Podman on SELinux systems may block writes unless the volume has the correct SELinux label. Two common approaches:

- Use the `:Z` or `:z` mount option to relabel the content for container use:

```bash
podman run -v $(pwd)/backend/external-h2-data:/workspace/external-h2-db:Z ...
```

- Or pre-create the directory and set a permissive SELinux label for the container user (admin exercise only):

```bash
mkdir -p backend/external-h2-data
chcon -R -t container_file_t backend/external-h2-data
```

Troubleshooting & tips
----------------------

- If the application logs show that `DevExternalDbInitRunner` skipped creation, ensure `RELOADER_USE_H2_EXTERNAL=true` and `SPRING_PROFILES_ACTIVE=dev` are set in the container environment.
- If you get port conflicts on 8080, change the `-p` mapping (for example `-p 9080:8080`) or edit `docker-compose.yml` to map a different host port.
- For faster local iterative builds, prefer `docker compose up --build` which will reuse layers; when actively developing Java code consider running the app outside a container for faster incremental feedback.

If you'd like, I can add a small `backend/scripts/docker-seed-h2.sh` helper that runs the existing seed SQL against a running containerized H2 instance or a `Makefile` target that wraps the compose commands.
