-- Rule: every membership ended within the last :lookback days and none is current or upcoming.
SELECT m.id AS member_id, m.member_code, m.full_name, m.phone, l.last_end AS ref_date,
       (CAST(:today AS date) - l.last_end) AS days
FROM member_profile m
JOIN LATERAL (SELECT max(x.end_date) AS last_end FROM membership x
              WHERE x.member_id = m.id AND x.status <> 'CANCELLED') l ON true
WHERE m.status = 'ACTIVE'
  AND l.last_end < CAST(:today AS date)
  AND l.last_end >= CAST(:today AS date) - CAST(:lookback AS integer)
ORDER BY l.last_end DESC, lower(m.full_name)
LIMIT 200
