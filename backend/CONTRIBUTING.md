# Backend contributor guide — external DB integration

This short guide explains preferred patterns for writing tests that interact with configuration and the external Oracle connections in the `backend/` module.

Key rules

- Avoid mutating global process state in tests (no `System.setProperty` / `System.clearProperty` / `System.getenv` hacks). Prefer Spring's test property support or `MockEnvironment`.
- Use `ConfigUtils` for boolean/string lookups that need nested fallback semantics.
- Mock `ExternalDbConfig#getConnection(...)` / `getConnectionByKey(...)` when a test should not reach real Oracle instances.

Example mock (JUnit + Mockito):

```java
@Mock
ExternalDbConfig externalDbConfig;

@Test
void testServiceUsesMockedConnection() throws Exception {
    Connection oracleLike = mock(Connection.class);
    when(externalDbConfig.getConnectionByKey(anyString(), any())).thenReturn(oracleLike);
    // exercise service that would otherwise try to connect to Oracle
}
```

Why this matters

- Centralizing config access keeps tests deterministic and parallelizable.
- Mocking external connections prevents accidental network calls to production databases from CI or developer machines.

Where to look

- `com.example.reloader.config.ConfigUtils` — centralized property lookup helpers.
- `com.example.reloader.config.ExternalDbConfig` — per-site pool creation for Oracle connections.
