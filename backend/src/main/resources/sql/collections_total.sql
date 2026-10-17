-- Params: :from, :to (timestamptz, to exclusive). RECORDED payments only; voided ones never count.
SELECT COALESCE(sum(amount_paise), 0) AS total_paise, count(*) AS payment_count
FROM payment
WHERE status = 'RECORDED' AND paid_at >= CAST(:from AS timestamptz) AND paid_at < CAST(:to AS timestamptz)
