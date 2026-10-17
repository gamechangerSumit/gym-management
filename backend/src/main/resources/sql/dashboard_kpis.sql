-- Params: :today (date), :soon (int). Counts members, not rows.
SELECT
  (SELECT count(*) FROM membership ms JOIN member_profile m ON m.id = ms.member_id
    WHERE m.status = 'ACTIVE' AND ms.status = 'ACTIVE'
      AND ms.start_date <= CAST(:today AS date) AND ms.end_date >= CAST(:today AS date)) AS active_members,
  (SELECT count(*) FROM membership ms JOIN member_profile m ON m.id = ms.member_id
    WHERE m.status = 'ACTIVE' AND ms.status = 'ACTIVE'
      AND ms.start_date <= CAST(:today AS date) AND ms.end_date >= CAST(:today AS date)
      AND (ms.end_date - CAST(:today AS date)) <= CAST(:soon AS integer)) AS expiring_soon,
  (SELECT count(*) FROM member_profile m
    WHERE m.status = 'ACTIVE'
      AND EXISTS (SELECT 1 FROM membership x WHERE x.member_id = m.id AND x.status <> 'CANCELLED'
                  AND x.end_date < CAST(:today AS date))
      AND NOT EXISTS (SELECT 1 FROM membership x WHERE x.member_id = m.id AND x.status <> 'CANCELLED'
                  AND x.end_date >= CAST(:today AS date))) AS expired,
  (SELECT count(DISTINCT member_id) FROM attendance_record
    WHERE checkin_date = CAST(:today AS date)) AS todays_checkins
