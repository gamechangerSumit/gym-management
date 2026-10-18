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
,
  (SELECT count(DISTINCT member_id) FROM attendance_record
    WHERE checkin_date BETWEEN CAST(:today AS date) - 6 AND CAST(:today AS date)) AS active_members_7d,
  (SELECT count(*) FROM no_show_case WHERE status = 'OPEN') AS open_no_show_cases,
  (SELECT count(DISTINCT member_id) FROM no_show_case
    WHERE status = 'RESOLVED' AND resolved_at >= now() - INTERVAL '7 days') AS members_returned_7d,
  (SELECT count(DISTINCT ms.member_id) FROM membership ms
    JOIN member_profile m ON m.id = ms.member_id
    WHERE m.status = 'ACTIVE' AND ms.status = 'ACTIVE'
      AND ms.end_date BETWEEN CAST(:today AS date) AND CAST(:today AS date) + 7) AS renewals_due_7d,
  (SELECT COALESCE(sum(p.amount_paise), 0) FROM payment p
    JOIN membership ms ON ms.id = p.membership_id
    WHERE p.status = 'RECORDED' AND ms.renewed_from_id IS NOT NULL
      AND p.paid_at >= date_trunc('month', now()) AND p.paid_at < date_trunc('month', now()) + INTERVAL '1 month') AS renewal_collected_month_paise
