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
docker run -e SPRING_PROFILES_ACTIVE=dev -p 8080:8080 --name reloader reloader-backend:latest
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
  "http://localhost:8080/api/dev/db-inspect?site=default&environment=qa" | jq
```

Create minimal sender queue table (DEV-ONLY, creates table if missing):

```bash
curl -s -X GET -H "Authorization: Bearer <JWT>" \
  "http://localhost:8080/api/dev/create-external-queue-table?site=default&environment=qa" | jq
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

CI workflow for external-write tests
------------------------------------

We've added a GitHub Actions workflow that runs the test suite with the H2 "external" DB and external writes enabled. The workflow lives at `.github/workflows/external-write-tests.yml` and is configured to run on pushes to `feature/**` and `work/**`, and manually via the Actions UI.

Why this helps:
- Validates push/retry/backoff behavior and SQLState classification against an in-memory external DB without touching production databases.

Run the same job locally (Unix shell):

```bash
RELOADER_USE_H2_EXTERNAL=true EXTERNAL_DB_ALLOW_WRITES=true mvn -f backend/pom.xml -DskipITs=true test
```

If you'd like I can also add a short badge or a targeted workflow that runs on PR to `main` once we're comfortable with the behavior.
