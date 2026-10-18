-- V11: member renewal orders. A membership is extended only when a recorded payment is linked to the order.
CREATE TABLE renewal_order (
    id BIGSERIAL PRIMARY KEY,
    gym_id BIGINT NOT NULL DEFAULT 1 REFERENCES gym(id),
    member_id BIGINT NOT NULL REFERENCES member_profile(id),
    source_membership_id BIGINT NOT NULL REFERENCES membership(id),
    plan_id BIGINT NOT NULL REFERENCES membership_plan(id),
    price_paise BIGINT NOT NULL CHECK (price_paise > 0),
    duration_days INT NOT NULL CHECK (duration_days BETWEEN 1 AND 3650),
    status VARCHAR(20) NOT NULL DEFAULT 'PAYMENT_PENDING'
      CHECK (status IN ('PAYMENT_PENDING', 'PAID', 'CANCELLED')),
    paid_membership_id BIGINT REFERENCES membership(id),
    paid_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX renewal_one_pending_per_member ON renewal_order(member_id) WHERE status='PAYMENT_PENDING';
CREATE INDEX renewal_order_queue_idx ON renewal_order(gym_id,status,created_at DESC);

ALTER TABLE payment ADD COLUMN renewal_order_id BIGINT REFERENCES renewal_order(id);
CREATE UNIQUE INDEX payment_renewal_order_uq ON payment(renewal_order_id) WHERE renewal_order_id IS NOT NULL;
ALTER TABLE payment ADD CONSTRAINT payment_order_link_exclusive
  CHECK (NOT (addon_order_id IS NOT NULL AND renewal_order_id IS NOT NULL));
