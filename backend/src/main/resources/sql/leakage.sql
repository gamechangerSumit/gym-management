-- Revenue leakage indicators from payment/membership records only. Params: :today (date), :since (timestamptz).
WITH valid AS (
  SELECT ms.member_id FROM membership ms JOIN member_profile m ON m.id = ms.member_id
  WHERE m.status = 'ACTIVE' AND ms.status = 'ACTIVE'
    AND ms.start_date <= CAST(:today AS date) AND ms.end_date >= CAST(:today AS date)
)
SELECT
  (SELECT count(*) FROM valid v JOIN member_dues_v d ON d.member_id = v.member_id WHERE d.dues_paise > 0) AS unpaid_count,
  (SELECT COALESCE(sum(d.dues_paise), 0) FROM valid v JOIN member_dues_v d ON d.member_id = v.member_id
    WHERE d.dues_paise > 0) AS unpaid_paise,
  (SELECT count(*) FROM membership ms JOIN member_profile m ON m.id = ms.member_id
    WHERE m.status = 'ACTIVE' AND ms.status <> 'CANCELLED'
      AND ms.end_date < CAST(:today AS date) AND ms.end_date >= CAST(:today AS date) - 30
      AND NOT EXISTS (SELECT 1 FROM membership n WHERE n.member_id = ms.member_id AND n.status <> 'CANCELLED'
                      AND n.start_date > ms.end_date)
      AND NOT EXISTS (SELECT 1 FROM renewal_followup f WHERE f.membership_id = ms.id
                      AND f.contact_status <> 'NOT_CONTACTED')) AS uncontacted_count,
  (SELECT count(*) FROM payment WHERE status = 'VOID' AND voided_at >= CAST(:since AS timestamptz)) AS voided_count,
  (SELECT COALESCE(sum(amount_paise), 0) FROM payment WHERE status = 'VOID'
    AND voided_at >= CAST(:since AS timestamptz)) AS voided_paise,
  (SELECT count(*) FROM membership ms JOIN membership_plan p ON p.id = ms.plan_id
    WHERE ms.status <> 'CANCELLED' AND ms.created_at >= CAST(:since AS timestamptz)
      AND ms.price_charged_paise < p.price_paise) AS discounted_count,
  (SELECT COALESCE(sum(p.price_paise - ms.price_charged_paise), 0) FROM membership ms
    JOIN membership_plan p ON p.id = ms.plan_id
    WHERE ms.status <> 'CANCELLED' AND ms.created_at >= CAST(:since AS timestamptz)
      AND ms.price_charged_paise < p.price_paise) AS discounted_paise
