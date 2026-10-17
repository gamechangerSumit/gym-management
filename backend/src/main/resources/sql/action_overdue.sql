-- Rule: dues (membership price charged minus recorded payments) greater than zero.
SELECT m.id AS member_id, m.member_code, m.full_name, m.phone, d.dues_paise
FROM member_profile m JOIN member_dues_v d ON d.member_id = m.id
WHERE m.status = 'ACTIVE' AND d.dues_paise > 0
ORDER BY d.dues_paise DESC, lower(m.full_name)
LIMIT 200
