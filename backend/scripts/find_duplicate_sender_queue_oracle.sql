-- Oracle: Find duplicate (sender_id, payload_id) rows in sender_queue
-- Returns sender_id, payload_id, count and example ids
SELECT sender_id,
       payload_id,
       COUNT(*) AS cnt,
       MIN(id) AS keep_id,
       LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) AS all_ids
FROM sender_queue
GROUP BY sender_id, payload_id
HAVING COUNT(*) > 1;
