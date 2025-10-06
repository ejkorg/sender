Local setup — run full stack (backend + frontend) against Oracle

This file documents how to run the reloader app locally and test full end-to-end functionality using an Oracle database as an external connection target. It includes sample configuration, commands, verification steps and common troubleshooting.

Prerequisites
- Java 17 (or the JDK specified by `backend/pom.xml`).
- Maven 3.x.
- Node.js (16+) and npm (for building/running the Angular frontend).
- Network access to your Oracle DB from your machine (VPN, port access, etc.).
- (Recommended) A secure location for `dbconnections.json` (do NOT commit secrets to git).

Overview
- The backend (Spring Boot) reads external connection definitions from `dbconnections.json` or from the path in the `RELOADER_DBCONN_PATH` env var. Each connection definition can include a nested `hikari` overrides map.
- The backend creates a bounded number of Hikari pools for external targets and registers Micrometer gauges for each pool. Admin endpoints live under `/internal`.
- The frontend (Angular) communicates with the backend APIs. The backend runs on port 8080 by default (see `backend/src/main/resources/application.yml`).

Important config keys and behavior
- RELOADER_DBCONN_PATH — environment variable or Spring property path to an external `dbconnections.json` (preferred for secrets). The app falls back to `classpath:dbconnections.json` when this is not set.
- RELOADER_USE_H2_EXTERNAL — set to `true` (string) to use an in-memory H2 DB for external connections (for dev/CI).
- external-db.cache.max-pools — application property to limit concurrently-held external Hikari pools (see `application.yml`).
- external-db.hikari.* — Hikari defaults; per-connection overrides may be set in `dbconnections.json`.
- Admin endpoints: `/internal/pools` (GET) and `/internal/pools/recreate?key=...` (POST). These are protected by ROLE_ADMIN in SecurityConfig.
- Metrics: `external_db_pool_active`, `external_db_pool_idle`, `external_db_pool_threads_waiting` should be visible in the actuator `/actuator/prometheus` output (if Prometheus registry/actuator endpoint is enabled).

Sample `dbconnections.json` (DO NOT COMMIT WITH REAL CREDENTIALS)
Place the file somewhere secure, e.g. `/etc/reloader/dbconnections.json`, and set `RELOADER_DBCONN_PATH` to that path.

Example:

```json
{
  "EXAMPLE_SITE": {
    "host": "db.example.internal/ORCL",
    "port": 1521,
    "user": "MYUSER",
    "password": "MYSECRET",
    "hikari": {
      "maximumPoolSize": 20,
      "minimumIdle": 2,
      "connectionTimeoutMs": 20000
    }
  },
  "PROD_SITE": {
    "host": "jdbc:oracle:thin:@//prod-host:1521/PROD",
    "user": "PRODUSER",
    "password": "prodpw"
  }
}
```

Notes:
- `host` may be a simple `host/SERVICE` value (for Oracle thin building) or a full `jdbc:` URL.
- The `hikari` block can be a nested JSON object or a JSON string in your config; `ExternalDbConfig` supports both.

Run the backend (connecting to Oracle external targets)

1) Build (from repo root):

```bash
mvn -f backend/pom.xml -DskipTests package
```

2) Run with the external dbconnections file; example using env var:

```bash
export RELOADER_DBCONN_PATH=/etc/reloader/dbconnections.json
# If you want the app to run with 'prod' profile (optional)
export SPRING_PROFILES_ACTIVE=prod
# Run the JAR
java -jar backend/target/reloader-backend-0.0.1-SNAPSHOT.jar
```

Or run via Maven (dev-friendly):

```bash
RELOADER_DBCONN_PATH=/etc/reloader/dbconnections.json mvn -f backend/pom.xml spring-boot:run
```

If you want the app's own primary datasource (the app schema used by Liquibase and JPA) to be Oracle instead of the default embedded H2 used in `application.yml`, update `backend/src/main/resources/application-prod.yml` (or set the corresponding Spring env vars):
- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`
- `spring.datasource.driver-class-name` (use `oracle.jdbc.OracleDriver` or let the JDBC URL discovery work)

Caution: when pointing the app's main datasource at Oracle, ensure the user has permissions to run Liquibase migrations or pre-create the schema/tables.

Run the frontend

1) Install deps and build (from repo root):

```bash
cd frontend
npm install
npm run build
```

2) Serve the build with any static server, or run the dev server:

```bash
# dev server (hot reload)
npm run start
# or serve static build (example using http-server)
npx http-server ./dist -p 4200
```

By default the frontend will call the backend at its configured location. In dev you can use a proxy config or set environment variables in the Angular app to point to `http://localhost:8080`.

Verify end-to-end behavior

1) Open the frontend in your browser and navigate to the flows that trigger discovery/enqueue against an ExternalLocation that references `EXAMPLE_SITE` (as in your `dbconnections.json`). Confirm discovery returns rows and enqueues payloads.

2) Admin endpoints (protected):
- GET `/internal/pools` — lists pool stats for active external pools.
- POST `/internal/pools/recreate?key=EXAMPLE_SITE` — closes & recreates the pool for the given key.

Note: Security is enforced; for local runs the `SecurityConfig` may configure an in-memory admin user for convenience (check `backend/src/main/java/.../SecurityConfig.java` for credentials used by tests/dev). If not, temporarily configure a dev admin user or disable security for quick experimentation (not recommended).

3) Metrics & actuator:
- If actuator's prometheus registry is enabled (prometheus exporter exists in `pom.xml`), visit `/actuator/prometheus` and look for `external_db_pool_active{pool="EXAMPLE_SITE"}` etc.
- After hitting `POST /internal/pools/recreate?key=EXAMPLE_SITE`, previously-registered per-pool metrics should be removed (the code now tracks and removes exact Meter.Id entries upon pool close).

Developer/test convenience

- Run the backend tests:

```bash
mvn -f backend/pom.xml test
```

- Use H2 for external DBs when developing (no Oracle required):

```bash
export RELOADER_USE_H2_EXTERNAL=true
mvn -f backend/pom.xml spring-boot:run
```

Troubleshooting

1) Oracle JDBC driver issues
- The project includes `ojdbc11` in `backend/pom.xml`. If Maven cannot download it due to corporate firewall/policy, you can manually install `ojdbc11.jar` to your local Maven repo:

```bash
mvn install:install-file -Dfile=/path/to/ojdbc11.jar -DgroupId=com.oracle.database.jdbc -DartifactId=ojdbc11 -Dversion=23.3.0.23.09 -Dpackaging=jar
```

2) ORA- errors / connectivity
- Confirm you can connect to the DB from your machine (sqlplus/SQL Developer) with the same credentials and JDBC URL.
- If the `host` is `host/SERVICE` format and the code builds `jdbc:oracle:thin:@host:port:sid`, ensure the derived SID is correct. If uncertain, use a full JDBC URL in `dbconnections.json` (starting with `jdbc:`) to avoid ambiguity.

3) Missing metrics after recreate
- Ensure the node that registered gauges was using the same MeterRegistry bean; in the code the `ExternalDbConfig` registers gauges when a MeterRegistry is available. For local testing the `SimpleMeterRegistry` is used by some tests; the app uses Micrometer + Prometheus in production.

4) Liquibase migration failures
- If you point the app's main datasource to Oracle, Liquibase will attempt to run changesets. If the user lacks permission, pre-apply the changesets or grant the required permissions.

5) Admin endpoints 401/403
- Admin endpoints are protected; check `SecurityConfig` for the dev admin user or temporarily relax security for debugging.

Optional tweaks / enhancements (I can implement)
- Add `external-db.metrics.enabled` property to toggle per-pool metric registration (recommended for high cardinality environments).
- Add a small regression test verifying the `registeredMeterIds` list is cleaned after `recreatePool` or eviction.
- Provide a simple docker-compose for quick local Oracle replacement (e.g., with Oracle XE) — I can scaffold this if useful.

Tests and schema strategy
-------------------------

In tests we use an in-memory H2 database. Liquibase is the canonical source of production migrations (the changelogs under `src/main/resources/db/changelog`).

However, in the test environment Hibernate may create the schema after Liquibase runs which can create ordering/visibility issues for changeSets that alter an already-created table. To keep tests simple and reliable we apply the following pragmatic approach:

- Keep a JPA-level `@UniqueConstraint` on `LoadSessionPayload(session_id, payload_id)` so the Hibernate-created test schema enforces the same uniqueness expected in production. This prevents duplicate rows from being inserted in tests and surface SQL errors (H2 SQLState 23505) where appropriate.
- Keep Liquibase as the canonical migration source for production. The Liquibase changelog added to perform dedupe and create a unique constraint remains the authoritative migration and should be applied during deployments.
- If you prefer tests to rely strictly on Liquibase, switch test config to disable Hibernate DDL (`spring.jpa.hibernate.ddl-auto=none`) and ensure Liquibase creates the full schema in tests before tests start. That is more canonical but requires additional test config adjustments.

If you want me to switch tests to use Liquibase-only schema creation (disable Hibernate DDL) I can implement that change and update the CI config accordingly.

If you'd like, I can add a `docs/LOCAL_SETUP.md` (this content) into the repo (I can commit it), implement the metrics toggle, or add the regression test. Which would you like me to do next?