# External DB runbook

This runbook documents how the application interacts with external databases, the runtime opt-in flags used for development, and guidance for running tests locally without touching production/Oracle databases.

This file is intended for contributors and operators working on the `backend/` service.

## Opt-in flags

- `reloader.use-h2-external` (property) / `RELOADER_USE_H2_EXTERNAL` (env)
  - When true (string `"true"` or numeric `"1"`), the application will use an in-memory H2 database for any code paths that otherwise resolve external DB connections from `dbconnections.json`.
  - This is intended for local development and CI. The H2 DB is seeded from `classpath:external_h2_seed.sql` when enabled.

- `external-db.allow-writes` (property) / `EXTERNAL_DB_ALLOW_WRITES` (env)
  - When true, the application will permit remote write operations to external DBs (for example inserting rows into remote `SENDER_QUEUE` tables).
  - For safety, remote writes are disabled by default (false). Tests and local dev must explicitly enable this to exercise remote-write code paths.

## How external pools are managed

- Per-external-site HikariCP DataSources are created on demand. The configuration is loaded from `dbconnections.json` (overridden by `RELOADER_DBCONN_PATH` when set).
- DataSources are cached using a Caffeine cache keyed by resolved connection key (site + optional environment).
- When an entry is evicted from the cache the removal listener:
  - closes the corresponding `HikariDataSource` (deterministic shutdown), and
  - deregisters any Micrometer meters associated with the DataSource (if a `MeterRegistry` is available).

This ensures that long-running test runs and dev sessions don't leak open pools or metrics.

## Testing guidance

- Do NOT mutate global process environment or `System.setProperty` in tests. Instead use one of the patterns below:
  - `@SpringBootTest(properties = "external-db.allow-writes=true")` for integration tests that need writes enabled.
  - `@TestPropertySource(properties = "reloader.use-h2-external=true")` for tests that exercise dev-only H2 behavior.
  - `MockEnvironment` or `StandardEnvironment` for unit tests. Example:

    MockEnvironment env = new MockEnvironment();
    env.setProperty("RELOADER_USE_H2_EXTERNAL", "true");

- Tests that would otherwise open Oracle connections should mock `ExternalDbConfig#getConnection(...)` or `getConnectionByKey(...)` to return an in-memory H2 `Connection` seeded from `classpath:external_h2_seed.sql`.

- Example local run (from repository root):

```bash
# enable H2-as-external and allow remote-write code paths for a specific test run
export RELOADER_USE_H2_EXTERNAL=true
export EXTERNAL_DB_ALLOW_WRITES=true
mvn -f backend/pom.xml -DskipTests=false test -Dtest=SomeSpecificTest
```

## Common troubleshooting

- If tests try to reach a real Oracle DB, confirm that:
  - `RELOADER_USE_H2_EXTERNAL` is enabled in the test context, or
  - the test mocks `ExternalDbConfig#getConnection...` to return an H2 connection.

- If Micrometer shows stale meters after pool eviction, ensure the `MeterRegistry` bean is available during pool creation so the deregistration logic runs on eviction.

## Where to look in code

- `com.example.reloader.config.ExternalDbConfig` — per-site pool creation, Caffeine cache and eviction handling.
- `com.example.reloader.config.ConfigUtils` — centralized config lookup and boolean parsing used across the app.
- `com.example.reloader.service.SenderService` — guarded by `external-db.allow-writes` when performing remote writes.

## Notes

- These runtime guards and the centralized `ConfigUtils` were added to prevent accidental remote writes and to make tests deterministic and safe to run in CI.

## Profiles and environments

- onsemi-oracle (runtime)
  - Activate with `SPRING_PROFILES_ACTIVE=onsemi-oracle`.
  - Uses `application-onsemi-oracle.yml` to point the app’s reference database (staging/dispatch) at Oracle.
  - External connections are still supplied via YAML/JSON (see RELOADER_DBCONN_PATH) and can point at Oracle instances per environment.

- Tests/CI
  - Tests force external connections to H2 via `reloader.use-h2-external=true` (env: `RELOADER_USE_H2_EXTERNAL=true`).
  - The test profile lives in `src/test/resources/application-test.yml` with H2 datasource settings for the app’s primary DB and the above flag set.
  - A `schema.sql` under `src/test/resources/` provides minimal objects (like `ALL_METADATA_VIEW`) to satisfy discovery queries.

## Optional hardening: per-test DB isolation

By default, the suite uses a shared in-memory DB name (e.g., `jdbc:h2:mem:testdb`). To reduce cross-test interference:

- Prefer deterministic reads (e.g., repository methods like `findTopByOrderByIdDesc`) when selecting the latest entity.
- For stricter isolation, give each test class a unique DB name via a property override:

  ```java
  @TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:${random.value};DB_CLOSE_DELAY=-1"
  })
  public class MyIsolatedTest { /* ... */ }
  ```

This increases startup time (additional contexts) but avoids flakiness in concurrent runs.
