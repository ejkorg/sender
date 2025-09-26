-- Find duplicate (sender_id, payload_id) rows in sender_queue
-- Returns sender_id, payload_id, count and example ids
SELECT sender_id, payload_id, COUNT(*) as cnt,
       MIN(id) as keep_id,
       GROUP_CONCAT(id) as all_ids
FROM sender_queue
GROUP BY sender_id, payload_id
HAVING COUNT(*) > 1;
