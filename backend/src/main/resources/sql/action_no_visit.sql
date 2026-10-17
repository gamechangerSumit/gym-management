-- Rule: valid membership and (today - last visit) >= :n days. With no visit yet, the membership start date is used,
-- and a visit older than the membership start does not count. A visit exactly :n days ago IS flagged.
SELECT m.id AS member_id, m.member_code, m.full_name, m.phone, ref.ref_date,
       (CAST(:today AS date) - ref.ref_date) AS days
FROM membership ms
JOIN member_profile m ON m.id = ms.member_id
CROSS JOIN LATERAL (
  SELECT GREATEST(COALESCE((SELECT max(a.checkin_date) FROM attendance_record a WHERE a.member_id = m.id),
                           ms.start_date), ms.start_date) AS ref_date) ref
WHERE m.status = 'ACTIVE' AND ms.status = 'ACTIVE'
  AND ms.start_date <= CAST(:today AS date) AND ms.end_date >= CAST(:today AS date)
  AND (CAST(:today AS date) - ref.ref_date) >= CAST(:n AS integer)
ORDER BY ref.ref_date, lower(m.full_name)
LIMIT 200
