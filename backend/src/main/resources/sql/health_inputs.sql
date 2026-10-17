-- Inputs for the Business Health Score. Params: :today (date), :dropoff (int).
WITH valid AS (
  SELECT ms.member_id, ms.start_date FROM membership ms JOIN member_profile m ON m.id = ms.member_id
  WHERE m.status = 'ACTIVE' AND ms.status = 'ACTIVE'
    AND ms.start_date <= CAST(:today AS date) AND ms.end_date >= CAST(:today AS date)
), ended AS (
  SELECT ms.id, ms.member_id, ms.end_date FROM membership ms JOIN member_profile m ON m.id = ms.member_id
  WHERE m.status = 'ACTIVE' AND ms.status <> 'CANCELLED'
    AND ms.end_date < CAST(:today AS date) AND ms.end_date >= CAST(:today AS date) - 30
)
SELECT
  (SELECT count(*) FROM member_profile WHERE status = 'ACTIVE') AS active_profiles,
  (SELECT count(*) FROM valid) AS valid_members,
  (SELECT count(*) FROM valid v
    WHERE GREATEST(COALESCE((SELECT max(a.checkin_date) FROM attendance_record a WHERE a.member_id = v.member_id),
                            v.start_date), v.start_date) > CAST(:today AS date) - CAST(:dropoff AS integer)) AS engaged_members,
  (SELECT count(*) FROM valid v JOIN member_dues_v d ON d.member_id = v.member_id WHERE d.dues_paise <= 0) AS valid_zero_dues,
  (SELECT count(*) FROM ended) AS ended_recent,
  (SELECT count(*) FROM ended e WHERE EXISTS (
      SELECT 1 FROM membership n WHERE n.member_id = e.member_id AND n.status <> 'CANCELLED'
        AND n.start_date > e.end_date)) AS ended_recent_renewed
