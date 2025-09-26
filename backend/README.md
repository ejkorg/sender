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

## Troubleshooting
- No email sent: confirm `spring.mail.host`/`port` and credentials are correct and that your SMTP provider allows the source IP. Check application logs for mail send exceptions.
- Discovery returns 0 items: verify `all_metadata_view` returns rows for the requested filters. The discovery SQL expects columns `id` and `id_data`; adjust the query in `MetadataImporterService` if your view differs.
- File attachments missing: ensure `writeListFile=true` and the application has write permission to its working directory.

## Next steps and improvements
- Add integration tests for `MetadataImporterService` that mock or use an in-memory external DB to validate enqueue behavior.
- Consider attaching a CSV with richer columns (timestamp, site, testerType) instead of a plain text list file.
- Add a feature toggle to disable scheduled discovery via feature flags or env var in production.

If you'd like, I can also add a brief README section to the top-level `reloader-app/README.md` and/or create a sample k8s secret/config map manifest for SMTP and discovery properties.