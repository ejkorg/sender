-- Seed file for the in-memory external H2 database used in tests when
-- RELOADER_USE_H2_EXTERNAL=true. Keep this minimal and idempotent so
-- it can be reused across test runs. Tests may further clean/seed data
-- in @BeforeEach methods.

CREATE TABLE IF NOT EXISTS all_metadata_view (
  lot VARCHAR(50),
  id VARCHAR(50),
  id_data VARCHAR(50),
  end_time TIMESTAMP,
  tester_type VARCHAR(50),
  data_type VARCHAR(50),
  test_phase VARCHAR(50),
  location VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS dtp_dist_conf (
  id INT PRIMARY KEY AUTO_INCREMENT,
  id_location INT,
  id_data_type INT,
  id_tester_type INT,
  id_data_type_ext INT,
  id_sender INT
);

CREATE TABLE IF NOT EXISTS dtp_location (
  id INT PRIMARY KEY AUTO_INCREMENT,
  location VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS dtp_data_type (
  id INT PRIMARY KEY AUTO_INCREMENT,
  data_type VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS dtp_tester_type (
  id INT PRIMARY KEY AUTO_INCREMENT,
  type VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS dtp_data_type_ext (
  id INT PRIMARY KEY AUTO_INCREMENT,
  data_type_ext VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS dtp_sender (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(200)
);

CREATE TABLE IF NOT EXISTS dtp_simple_client_setting (
  id INT PRIMARY KEY AUTO_INCREMENT,
  location VARCHAR(200),
  data_type VARCHAR(200),
  tester_type VARCHAR(200),
  data_type_ext VARCHAR(200),
  enabled CHAR(1),
  file_type VARCHAR(50)
);

-- No initial rows here; tests will insert and clean as needed.

-- Create a sequence for parity with Oracle-based external DBs. H2 will
-- ignore this if it already exists; tests that run in H2 write-mode may
-- rely on a sequence name when external code expects it.
CREATE SEQUENCE IF NOT EXISTS DTP_SENDER_QUEUE_ITEM_SEQ START WITH 1 INCREMENT BY 1;
