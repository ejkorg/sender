# Backend contributor guide — tests & external DB

This short guide explains preferred patterns for writing tests that interact with configuration and the "external DB" plumbing in the `backend/` module.

Key rules

- Do not mutate global process state in tests (avoid `System.setProperty`, `System.clearProperty`, or attempts to mutate `System.getenv`). These cause flakiness for parallel tests and CI.
- Prefer injecting test properties through Spring test annotations or using `MockEnvironment` / `StandardEnvironment` for unit tests.
- Use `ConfigUtils` for boolean/string lookups that need nested fallback semantics.
- Tests that would otherwise open production/Oracle connections should mock `ExternalDbConfig#getConnection(...)` or `getConnectionByKey(...)` to return an H2 `Connection` seeded from `classpath:external_h2_seed.sql`.

Examples

- Use `@SpringBootTest(properties = "external-db.allow-writes=true")` for integration tests that need to enable remote-write guarded code paths.

- Use `@TestPropertySource(properties = "reloader.use-h2-external=true")` or `MockEnvironment` for unit tests that exercise H2-as-external behavior.

  MockEnvironment example:

  ```java
  MockEnvironment env = new MockEnvironment();
  env.setProperty("RELOADER_USE_H2_EXTERNAL", "true");
  ExternalDbConfig cfg = new ExternalDbConfig(env);
  // now cfg.getConnectionByKey(...) will use the H2 seed when implemented
  ```

- Mock ExternalDbConfig#getConnection example (JUnit + Mockito):

  ```java
  @Mock
  ExternalDbConfig externalDbConfig;

  @Test
  void testServiceUsesMockedConnection() throws Exception {
      Connection h2 = DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
      when(externalDbConfig.getConnectionByKey(anyString(), any())).thenReturn(h2);
      // exercise service that would otherwise try to connect to Oracle
  }
  ```

Why this matters

- Centralizing config access and avoiding global state changes keeps tests deterministic and parallelizable.
- Mocking external connections prevents accidental network calls to production databases from CI or local developer machines.

Where to look

- `com.example.reloader.config.ConfigUtils` — centralized property lookup helpers.
- `com.example.reloader.config.ExternalDbConfig` — per-site pool creation and H2 dev support.
