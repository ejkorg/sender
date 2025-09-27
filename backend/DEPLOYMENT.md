# Deployment notes — Reloader backend

This file summarizes runtime configuration and deployment notes for the Reloader backend module (discovery, notifications, and sender queue).

## Discovery configuration (app.discovery.*)

Key properties (set in `application.yml`, environment variables, or platform secrets):

- `app.discovery.sender-id` (int) — the sender id that discovery will enqueue into.
- `app.discovery.site` — optional site identifier for external DB selection/filter.
- `app.discovery.startDate`, `app.discovery.endDate` — optional discovery time range.
- `app.discovery.testerType`, `app.discovery.dataType`, `app.discovery.testPhase`, `app.discovery.location` — optional filters.
- `app.discovery.writeListFile` (boolean, default false) — write a list file of discovered payloads to working directory.
- `app.discovery.notifyRecipient` — email recipient for completion notifications. If not set, no email is sent.
- `app.discovery.notifyAttachList` (boolean) — attach the generated list file to the notification when true.
- `app.discovery.numberOfDataToSend` — limit number of enqueued payloads per discovery run.
- `app.discovery.countLimitTrigger` — skip discovery run when external queue size >= this threshold.
- `app.discovery.cron` — cron expression for scheduled discovery runs (if using the scheduler).

These properties map to `DiscoveryProperties` in the application and are used by `MetadataImporterService`.

UI-driven selections and how they map to external DB connections

In the current backend implementation the UI is expected to provide dropdowns and lookups for several discovery parameters (for example: `senderId`, `location`, `testerType`, `dataType`, `testPhase`). These dropdown values are not hard-coded in the frontend; instead the UI calls backend endpoints which query the selected external database (the same DB used during discovery) to populate the lists.

- The frontend calls `/api/environments` to list saved environments (these are `ExternalEnvironment` entries stored in the application DB).
- For a selected environment the frontend calls `/api/environments/{envName}/locations` to fetch saved `ExternalLocation` entries (labels + db connection key). These entries are managed via `ExternalLocationController` and `ExternalLocationService` and can be imported via the CSV import endpoint.
- When the user picks a saved location the UI will typically send the `locationId` back to discovery calls. The backend will resolve the `ExternalLocation` and derive the `site` (a site token is derived from the location label on import), and will use the `db_connection_name` stored on that location to build the JDBC connection via `ExternalDbConfig`.
- Alternatively the UI (or advanced callers) can pass a `connectionKey` directly to endpoints such as `/api/senders/lookup` or `/api/senders/external/locations`/`/external/testerTypes` etc. `ExternalDbConfig` will resolve that connection key (optionally qualified by the `environment` parameter such as `qa` or `prod`) against `dbconnections.json` or the external file pointed to by `RELOADER_DBCONN_PATH`.

As a result, dropdowns for `testerType`, `location`, `dataType`, and `testPhase` are populated by calling the `SenderController` endpoints that proxy queries against the resolved external DB connection (either via `locationId` -> saved `ExternalLocation` or `connectionKey`). The `site` and `environment` selection together determine which external DB instance is queried.

Practical implication for deployments: ensure that `dbconnections.json` (or the external file referenced by `RELOADER_DBCONN_PATH`) contains the connection entries expected by saved `ExternalLocation` records, and that the UI users know whether to pick `qa` or `prod` when selecting an environment. If you prefer the UI to only offer a small, pre-approved list of connection targets, seed `ExternalLocation` entries via the CSV import and avoid allowing arbitrary `connectionKey` inputs from the client.

## SMTP / Mail configuration

The application uses Spring Boot Mail (`JavaMailSender`). Configure with standard Spring properties:

- `spring.mail.host`
- `spring.mail.port`
- `spring.mail.username`
- `spring.mail.password`
- `spring.mail.properties.mail.smtp.auth` (true/false)
- `spring.mail.properties.mail.smtp.starttls.enable` (true/false)

For local development use MailHog (host `localhost`, port `1025`) or `smtp4dev` to inspect outgoing messages.

Security: provide SMTP credentials via your platform's secret manager. Avoid storing credentials in repo.

## External DB selection (QA / PROD) and `ExternalDbConfig`

The code resolves external DB connections via `ExternalDbConfig.getConnection(site, environment)`. The `environment` parameter (e.g., `qa`, `prod`) is used to choose which external connection block to use from `dbconnections.json` or an external file path provided via the `RELOADER_DBCONN_PATH` environment variable.

UI/REST callers can pass an `environment` parameter to discovery endpoints; make sure users select `qa` or `prod` appropriately.

## Database-level dedupe (unique constraint)

To avoid race conditions where multiple processes insert the same payload concurrently, `sender_queue` now has a unique constraint on `(sender_id, payload_id)` applied via Liquibase. The changeSet id is `3` in `src/main/resources/db/changelog/db.changelog-1.0.xml` and creates `uk_sender_payload`.

`SenderService.enqueuePayloadsWithResult(...)` also catches `DataIntegrityViolationException` on insert and treats such cases as skipped payloads. This prevents the application from failing when a concurrent insert occurs.

## Migration / Backwards compatibility

- The new Liquibase changeSet will run on application startup. On existing databases, the unique constraint will be added; ensure no pre-existing duplicate rows for the same `(sender_id, payload_id)` exist before applying in production — Liquibase will fail if duplicates exist.

Migration checklist before deploying to production:

1. Run a migration plan that identifies duplicates: `SELECT sender_id, payload_id, COUNT(*) FROM sender_queue GROUP BY sender_id, payload_id HAVING COUNT(*) > 1;`
2. If duplicates exist, decide whether to dedupe (delete older duplicates) or fail the migration until cleaned.
3. Apply the deployment to a staging environment first and run smoke tests.

### Pre-deploy helper scripts

Two helper SQL scripts are included in `reloader-app/backend/scripts` to assist with migration. There are generic scripts and Oracle-specific versions:

- Generic (H2/Postgres/MySQL-ish):
  - `scripts/find_duplicate_sender_queue.sql`
  - `scripts/dedupe_sender_queue_keep_lowest_id.sql`

- Oracle-specific:
  - `scripts/find_duplicate_sender_queue_oracle.sql`
  - `scripts/dedupe_sender_queue_oracle.sql`

Usage examples:

Postgres (psql):
```bash
# find duplicates
psql -h <host> -U <user> -d <db> -f reloader-app/backend/scripts/find_duplicate_sender_queue.sql

# run dedupe in a transaction (backup first)
psql -h <host> -U <user> -d <db> -c "BEGIN;" -f reloader-app/backend/scripts/dedupe_sender_queue_keep_lowest_id.sql -c "COMMIT;"
```

Oracle (sqlplus):
```bash
# find duplicates
sqlplus user/password@//host:1521/ORCL @reloader-app/backend/scripts/find_duplicate_sender_queue_oracle.sql

# run dedupe (backup/export first)
sqlplus user/password@//host:1521/ORCL @reloader-app/backend/scripts/dedupe_sender_queue_oracle.sql
```

Notes:
- The Oracle scripts use `LISTAGG` and `ROW_NUMBER()` which are available in modern Oracle versions. Test against your Oracle version first.
- Always backup the DB and run in a controlled maintenance window.

### Pre-deploy wrapper

There is a convenience wrapper script `reloader-app/backend/scripts/predeploy_check.sh` that runs the appropriate find script for `postgres` or `oracle` and optionally runs the dedupe script when passed `--dedupe` (it will prompt for confirmation). Examples:

```bash
# postgres find only
reloader-app/backend/scripts/predeploy_check.sh --dbtype postgres --user dbuser --host dbhost --db reloaderdb

# postgres with dedupe (interactive confirmation required)
reloader-app/backend/scripts/predeploy_check.sh --dbtype postgres --user dbuser --host dbhost --db reloaderdb --dedupe

# oracle find only (use the --connect parameter for connection string)
reloader-app/backend/scripts/predeploy_check.sh --dbtype oracle --connect "user/password@//host:1521/SID"
```

## Example Kubernetes secret / configmap snippets

Create a Kubernetes secret for SMTP credentials (example):

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: reloader-mail
type: Opaque
data:
  spring.mail.username: <base64-encoded-username>
  spring.mail.password: <base64-encoded-password>
```

Create a config map for discovery properties (example):

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: reloader-config
data:
  application.yml: |
    app:
      discovery:
        notifyRecipient: ops@example.com
        writeListFile: "true"

```

## Troubleshooting

- If discovery emails are not sent, check application logs for mail exceptions and ensure SMTP connectivity from the host.
- If discovery enqueues fewer items than expected, confirm the `all_metadata_view` returned rows for the requested filters and that `numberOfDataToSend` is not limiting results.

---

If you want, I can also add a short migration script and a PR note recommending a pre-deploy dedupe check. Tell me and I'll add it.

## External DB connections & metrics (important operational notes)

This application can connect to many external databases defined in a JSON file (`dbconnections.json`). The runtime supports loading that file from the classpath (dev) or from an external path for production secrets.

- RELOADER_DBCONN_PATH (env / system property)
  - If set, `ExternalDbConfig` will load the JSON file at this path. This is the recommended way to provide production connection definitions (keep secrets out of the JAR).
  - Example: set `RELOADER_DBCONN_PATH=/etc/reloader/dbconnections.json` in your deployment manifest.

- RELOADER_USE_H2_EXTERNAL (env)
  - For local development / CI you can enable an in-memory H2 "external" DB by setting `RELOADER_USE_H2_EXTERNAL=true`. When enabled, external DB lookups use an H2 DB seeded from `classpath:external_h2_seed.sql` and ignore `dbconnections.json`.

- `external-db.cache.max-pools` (application property)
  - Controls the maximum number of active pooled DataSources the app will keep open at once. Default is `50`. Reduce this if you expect many distinct connection keys to avoid high memory and metric cardinality.
  - Set this in `application.yml` or via environment properties (e.g., `EXTERNAL_DB_CACHE_MAX_POOLS=20` depending on your environment variable style).

- Per-connection Hikari overrides
  - Each connection entry in `dbconnections.json` may include a `hikari` block to override defaults for that connection. The `hikari` value may be either a nested JSON object or a JSON string (both are accepted). Supported keys: `maximumPoolSize`, `minimumIdle`, `connectionTimeoutMs`, `idleTimeoutMs`, `validationTimeoutMs`.

- Metrics and cardinality concerns
  - By default the app registers per-pool metrics (either via Hikari's Micrometer tracker factory or fallback manual gauges). These metrics include a `pool` or `poolName` tag containing the resolved pool name (e.g., `external-EXAMPLE_SITE`).
  - If you have many unique connection keys (for example, dynamically generated per-tenant keys), metric cardinality can grow unbounded and overload your metrics system.
  - Mitigations:
    - Lower `external-db.cache.max-pools` to limit how many distinct pools are created/kept concurrently.
    - Disable per-pool metrics in your environment by not providing a MeterRegistry or by adjusting metric filters at your metrics scrape/ingest pipeline.
    - Use consistent connection keys (avoid dynamically generating distinct keys per request) and prefer a small set of long-lived connections.

### Example `dbconnections.json` snippet

```
{
  "EXAMPLE_SITE": {
    "host": "jdbc:h2:mem:external_repo",
    "user": "sa",
    "password": "",
    "hikari": { "maximumPoolSize": 5 }
  }
}
```

Notes:
- The runtime will attempt to set HikariCP's Micrometer tracker factory when a `MeterRegistry` is available so Hikari's built-in metrics are emitted. The code also registers manual gauges as a fallback for robustness.
- The application will automatically remove meters for a pool when the pool is closed/evicted from the cache to avoid long-lived metric leaks — however you should still monitor cardinality and size of `external-db.cache.max-pools` in production.
