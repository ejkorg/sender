# Reloader Backend — Discovery & Notification Configuration

This document describes how to configure the discovery scheduler and SMTP (email) notifications for the Reloader backend module.

## Purpose
The backend includes a discovery/importer that:

- Queries an external metadata view (`all_metadata_view`) for payload identifiers.
- Optionally writes a discovery list file (plain text) with payload IDs.
- Enqueues discovered payloads into the `sender_queue` (via `SenderService`).
- Optionally sends a completion notification email and may attach the discovery list file.

Configuration is exposed via `app.discovery.*` properties (bound to `DiscoveryProperties`) and standard Spring Mail properties (`spring.mail.*`).

## Important properties
Add these to `application.properties`, `application.yml`, or provide them as environment variables (see examples below).

- app.discovery.sender-id (required when triggering discovery for a specific sender)
- app.discovery.site (optional) — discovery `site` filter
- app.discovery.startDate / app.discovery.endDate (optional) — discovery date range (ISO format or SQL-friendly string)
- app.discovery.testerType (optional)
- app.discovery.dataType (optional)
- app.discovery.writeListFile (boolean, default: false) — if true, discovery will write `sender_list_{senderId}.txt` to the working directory
- app.discovery.numberOfDataToSend (int, optional) — limit number of enqueued payloads
- app.discovery.countLimitTrigger (int, optional) — do not run discovery if external queue size >= this threshold
- app.discovery.cron (optional) — cron expression for scheduled discovery runs in `DiscoveryScheduler`
- app.discovery.notifyRecipient (string, optional) — email recipient for completion notifications
- app.discovery.notifyAttachList (boolean, default: false) — attach the generated list file to the notification email when true

Notes:
- Properties are available through `DiscoveryProperties` and are used by `MetadataImporterService` and `DiscoveryScheduler`.
- If `notifyRecipient` is empty or unset, no email will be sent.

## Example application.yml (production)

```yaml
app:
  discovery:
    sender-id: 42
    site: "SITE_A"
    startDate: "2025-01-01"
    endDate: "2025-01-31"
    writeListFile: true
    numberOfDataToSend: 100
    countLimitTrigger: 10000
    cron: "0 0 3 * * *" # every day at 03:00
    notifyRecipient: "ops@example.com"
    notifyAttachList: true

spring:
  mail:
    host: smtp.example.com
    port: 587
    username: smtp-user
    password: smtp-secret
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```

## Runtime profiles and databases

The backend supports a dedicated runtime profile for onsemi Oracle environments and a convenient H2-only mode for tests/CI:

- onsemi-oracle profile (production runtime)
  - Activate with:
    - Environment: `SPRING_PROFILES_ACTIVE=onsemi-oracle`
    - Or JVM arg: `-Dspring.profiles.active=onsemi-oracle`
  - Configuration lives in `application-onsemi-oracle.yml` and points the app’s reference DB (staging/dispatch) at Oracle. External connection definitions are still provided via YAML/JSON.
  - Example run:

    ```bash
    SPRING_PROFILES_ACTIVE=onsemi-oracle \
    RELOADER_DBCONN_PATH=/etc/reloader/dbconnections.json \
    java -jar target/reloader-backend-0.0.1-SNAPSHOT.jar
    ```

- Tests/CI with H2-only external DBs
  - Tests never contact Oracle. We force external connections to an in-memory H2 instance via `reloader.use-h2-external=true` (or env `RELOADER_USE_H2_EXTERNAL=true`).
  - Test profile config: `src/test/resources/application-test.yml` includes `reloader.use-h2-external: true` and H2 datasource settings for the app DB.
  - Run tests:

    ```bash
    mvn -f backend/pom.xml test
    ```

  - Optional (explicit env):

    ```bash
    RELOADER_USE_H2_EXTERNAL=true mvn -f backend/pom.xml test
    ```

Notes:
- External H2 schema used in tests is bootstrapped via `src/test/resources/schema.sql` to satisfy discovery queries (e.g., `ALL_METADATA_VIEW`).
- For local dev without Oracle, you can also run the app with external H2 by setting `RELOADER_USE_H2_EXTERNAL=true`. This affects only external lookups; the app’s primary datasource remains whatever your active profile configures.

## UI status surfacing

The UI consumes stage/dispatch statuses exposed by the backend service. Ensure the backend is running and the frontend environment points to it. Status APIs are available behind the existing security configuration and are read-only; the frontend surfaces queue sizes and recent activity.

## Application configuration (application.yml) - datasource example

Below is an example `application.yml` snippet showing the application datasource settings used for local development (H2) and notes on overriding for production.

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:reloader;DB_CLOSE_DELAY=-1;MODE=Oracle
    driver-class-name: org.h2.Driver
    username: sa
    password: ""
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
  liquibase:
    change-log: classpath:db/changelog/db.changelog-1.0.xml
```

Notes:
- For production, override these properties with environment variables (e.g. `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`) or provide a separate profile-specific YAML.
- External DB connection definitions (used when the UI selects a DB location) are read from a JSON file specified by the `RELOADER_DBCONN_PATH` environment variable. Example:

```bash
export RELOADER_DBCONN_PATH=/etc/reloader/dbconnections.json
```

- For integration testing or offline development you can set `RELOADER_USE_H2_EXTERNAL=true` to have the code use an in-memory H2 database for external connections (seeded from `external_h2_seed.sql`).

See also: `backend/docs/external-db-runbook.md` for detailed guidance on the opt-in flags (`reloader.use-h2-external` / `RELOADER_USE_H2_EXTERNAL` and `external-db.allow-writes`), pool lifecycle, and test patterns to avoid touching production databases.

CI note: Prefer enabling H2-as-external only for specific CI jobs that need to exercise external-DB behavior. Example (GitHub Actions snippet):

```yaml
# job that runs integration tests with H2-as-external
name: integration-tests-h2-external
runs-on: ubuntu-latest
steps:
  - uses: actions/checkout@v4
  - name: Run backend tests (H2-as-external)
    env:
      RELOADER_USE_H2_EXTERNAL: "true"
      EXTERNAL_DB_ALLOW_WRITES: "true"
    run: mvn -f backend/pom.xml -DskipITs=false -DskipTests=false test
```

- The `ExternalDbConfig` supports connection pooling via HikariCP. The implementation caches a pooled `HikariDataSource` per resolved connection key (e.g. `EXTERNAL-qa`), improving performance for repeated external queries.


## Example environment variables (12-factor)

- APP_DISCOVERY_SENDER-ID=42 or APP_DISCOVERY_SENDER_ID=42
- APP_DISCOVERY_NOTIFYRECIPIENT=ops@example.com (note: Spring Boot relaxed binding applies; prefer underscores or dots)
- SPRING_MAIL_HOST, SPRING_MAIL_PORT, SPRING_MAIL_USERNAME, SPRING_MAIL_PASSWORD

Tip: Use your container runtime or orchestration platform's secret mechanism to inject SMTP credentials.

## How discovery is triggered

1. Scheduled runs: `DiscoveryScheduler` reads `app.discovery.cron`. If set, discovery will run on that schedule.
2. Manual / on-demand: call the REST endpoint exposed by `SenderController`:

- POST /api/senders/{senderId}/discover
- Query parameters (optional): site, startDate, endDate, testerType, dataType, writeListFile, numberOfDataToSend, countLimitTrigger

Example (curl):

```bash
curl -X POST "http://localhost:8080/api/senders/42/discover?site=SITE_A&writeListFile=true&numberOfDataToSend=50" \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json"
```

The endpoint will return a JSON object summarizing how many items were discovered and enqueued.

## Mail / Notification behavior

- If `app.discovery.notifyRecipient` is set, the system will attempt to send a notification email when discovery finishes. The subject includes the sender id and counts.
- If `app.discovery.notifyAttachList` is true and `app.discovery.writeListFile` produced a file (e.g., `sender_list_42.txt`), the file will be attached to the email.
- Mail sending is implemented using Spring Boot Mail (`JavaMailSender`). The `MailService` is tolerant of missing mail configuration in test/CI contexts (it will skip sends or fall back to a simple log message if no SMTP server is configured).

## Filenames and outputs
- Discovery list (when `writeListFile=true`): `sender_list_{senderId}.txt` in the working directory (one payload id per line, or id and id_data separated by comma depending on configuration).

## Testing locally

1. Start the backend (example):

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=test
```

2. For local email testing, use an SMTP testing service:
- MailHog (recommended for local dev): run `mailhog` and set `spring.mail.host=localhost`, `spring.mail.port=1025`.
- Fake SMTP: services like smtp4dev or a local Postfix relay.

3. To run the discovery manually in tests, call the REST endpoint above or write a small integration test that invokes `MetadataImporterService`.

## Security & Production notes
- Protect the discovery REST endpoint with proper authentication and authorization (it is behind the existing security configuration, so ensure callers have the correct role).
- Use TLS/STARTTLS for SMTP. Never store plaintext SMTP passwords in source control — use secrets management.
- Validate the external DB and view permissions. Discovery directly queries `all_metadata_view` (or your configured metadata table) — ensure the DB user has read access.

## Authentication

The backend provides a small JSON-based authentication API used by the Angular frontend. Key endpoints:

- POST /api/auth/login
  - Body: { username, password }
  - Response: { accessToken }
  - Side effect: sets an HttpOnly cookie `refresh_token` that the server persists and rotates on refresh.

- POST /api/auth/refresh
  - Reads the `refresh_token` HttpOnly cookie, validates against stored tokens, rotates the refresh token, and returns a new `accessToken` in JSON. The response also sets a new `refresh_token` cookie.

- POST /api/auth/logout
  - Revokes the server-side refresh token and clears the cookie.

Notes:

- The frontend stores the short-lived JWT `accessToken` and sends it in `Authorization: Bearer <token>` headers. On 401 the frontend should call `/api/auth/refresh` (the cookie will be sent automatically by the browser) to obtain a new `accessToken`.
- Tests or non-browser clients will need to persist and send the `refresh_token` cookie manually; the test-suite contains a small helper interceptor used by `TestRestTemplate` to capture and replay cookies.

## Troubleshooting
- No email sent: confirm `spring.mail.host`/`port` and credentials are correct and that your SMTP provider allows the source IP. Check application logs for mail send exceptions.
- Discovery returns 0 items: verify `all_metadata_view` returns rows for the requested filters. The discovery SQL expects columns `id` and `id_data`; adjust the query in `MetadataImporterService` if your view differs.
- File attachments missing: ensure `writeListFile=true` and the application has write permission to its working directory.

## Next steps and improvements
- Add integration tests for `MetadataImporterService` that mock or use an in-memory external DB to validate enqueue behavior.
- Consider attaching a CSV with richer columns (timestamp, site, testerType) instead of a plain text list file.
- Add a feature toggle to disable scheduled discovery via feature flags or env var in production.

If you'd like, I can also add a brief README section to the top-level `reloader-app/README.md` and/or create a sample k8s secret/config map manifest for SMTP and discovery properties.