-- Minimal schema bootstrap for tests
-- Local app schema will be managed by JPA (ddl-auto=update). Here we ensure external H2 schema exists for discovery.
CREATE TABLE IF NOT EXISTS ALL_METADATA_VIEW (
  ID VARCHAR(64) PRIMARY KEY,
  ID_DATA VARCHAR(64) NOT NULL,
  END_TIME TIMESTAMP,
  TESTER_TYPE VARCHAR(64),
  DATA_TYPE VARCHAR(64)
);

-- Seed a little data to allow discovery to return rows when start/end filters are absent
MERGE INTO ALL_METADATA_VIEW (ID, ID_DATA, END_TIME, TESTER_TYPE, DATA_TYPE)
KEY (ID)
VALUES ('META','DATA', CURRENT_TIMESTAMP, 'ANY', 'ANY');
