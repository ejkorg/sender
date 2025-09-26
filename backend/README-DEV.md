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
