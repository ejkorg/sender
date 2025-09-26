-- Oracle: Remove duplicate rows in sender_queue keeping the row with the lowest id per (sender_id, payload_id).
-- WARNING: Run this in a transaction in production and backup data first.

-- This deletes rows where the ROW_NUMBER() over partition is greater than 1
DELETE FROM sender_queue a
WHERE a.id IN (
  SELECT id FROM (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY sender_id, payload_id ORDER BY id) rn
    FROM sender_queue
  ) WHERE rn > 1
);

COMMIT;
