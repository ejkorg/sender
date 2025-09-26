-- Remove duplicate rows in sender_queue keeping the row with the lowest id per (sender_id, payload_id).
-- WARNING: Run this in a transaction in production and backup data first.

-- Example (Postgres / H2 compatible approach using ROW_NUMBER())
WITH ranked AS (
  SELECT id, sender_id, payload_id,
         ROW_NUMBER() OVER (PARTITION BY sender_id, payload_id ORDER BY id) as rn
  FROM sender_queue
)
DELETE FROM sender_queue
WHERE id IN (SELECT id FROM ranked WHERE rn > 1);

-- Notes:
-- - Some DBs (MySQL) require different syntax; adapt accordingly.
-- - Ensure you run in a safe maintenance window and have backups.
