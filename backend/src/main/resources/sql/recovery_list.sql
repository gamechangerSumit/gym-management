-- Renewal Recovery Center: latest memberships that ended in the last :lookback days or end within :soon days,
-- with no later membership. Contact status is tracking only; nothing is ever sent.
SELECT ms.id AS membership_id, m.id AS member_id, m.member_code, m.full_name, m.phone, ms.end_date,
       p.name AS plan_name, COALESCE(f.contact_status, 'NOT_CONTACTED') AS contact_status, f.note, f.updated_at
FROM membership ms
JOIN member_profile m ON m.id = ms.member_id
JOIN membership_plan p ON p.id = ms.plan_id
LEFT JOIN renewal_followup f ON f.membership_id = ms.id
WHERE m.status = 'ACTIVE' AND ms.status <> 'CANCELLED'
  AND ms.end_date >= CAST(:today AS date) - CAST(:lookback AS integer)
  AND ms.end_date <= CAST(:today AS date) + CAST(:soon AS integer)
  AND NOT EXISTS (SELECT 1 FROM membership n WHERE n.member_id = ms.member_id AND n.status <> 'CANCELLED'
                  AND n.start_date > ms.end_date)
ORDER BY ms.end_date, lower(m.full_name)
LIMIT 500
