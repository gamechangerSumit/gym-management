-- Rule: valid membership whose end date is within :soon days (0 = last valid day).
SELECT m.id AS member_id, m.member_code, m.full_name, m.phone, ms.end_date AS ref_date,
       (ms.end_date - CAST(:today AS date)) AS days
FROM membership ms JOIN member_profile m ON m.id = ms.member_id
WHERE m.status = 'ACTIVE' AND ms.status = 'ACTIVE'
  AND ms.start_date <= CAST(:today AS date) AND ms.end_date >= CAST(:today AS date)
  AND (ms.end_date - CAST(:today AS date)) <= CAST(:soon AS integer)
ORDER BY ms.end_date, lower(m.full_name)
LIMIT 200
