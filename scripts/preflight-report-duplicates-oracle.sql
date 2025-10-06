-- Preflight report: find duplicate (session_id, payload_id) groups in load_session_payload
-- Non-destructive: only SELECTs.
-- Run with sqlplus as a user that can read the target schema (example using sys/sys_password as SYSDBA for local XE testing):
--   echo exit | sqlplus sys/Password123@//localhost:1521/XEPDB1 as sysdba @/path/to/preflight-report-duplicates-oracle.sql

SET ECHO ON
SET FEEDBACK ON
SET LINESIZE 200
SET PAGESIZE 200

PROMPT ----------------------------------------------------------------
PROMPT Duplicate groups (session_id, payload_id) with count > 1
PROMPT ----------------------------------------------------------------

SELECT session_id, payload_id, COUNT(*) AS cnt
FROM load_session_payload
GROUP BY session_id, payload_id
HAVING COUNT(*) > 1
ORDER BY cnt DESC;

PROMPT ----------------------------------------------------------------
PROMPT Sample rows for duplicate groups (first 10 groups)
PROMPT ----------------------------------------------------------------

-- This query shows up to 5 sample rows per duplicated group (uses ROW_NUMBER window function)
WITH dup_groups AS (
  SELECT session_id, payload_id
  FROM load_session_payload
  GROUP BY session_id, payload_id
  HAVING COUNT(*) > 1
), samples AS (
  SELECT id, session_id, payload_id, data, ROW_NUMBER() OVER (PARTITION BY session_id, payload_id ORDER BY id) rn
  FROM load_session_payload lsp
  WHERE (session_id, payload_id) IN (SELECT session_id, payload_id FROM dup_groups)
)
SELECT id, session_id, payload_id, rn, CASE WHEN DBMS_LOB.GETLENGTH(data) > 2000 THEN SUBSTR(data,1,2000) || '... (truncated)' ELSE data END AS data_sample
FROM samples
WHERE rn <= 5
ORDER BY session_id, payload_id, rn;

PROMPT ----------------------------------------------------------------
PROMPT Guidance:
PROMPT - If there are rows above, export them (e.g., using SQL Developer or an export script) before running the destructive dedupe.
PROMPT - Consider business rules for choosing which row to keep when consolidating duplicates (we keep lowest id in the provided migration).
PROMPT ----------------------------------------------------------------

EXIT
