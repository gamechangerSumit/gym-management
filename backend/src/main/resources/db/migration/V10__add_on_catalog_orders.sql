-- V10: add-on catalogue, member orders, stock reservation and payment linkage.
CREATE TABLE gym_addon (
    id BIGSERIAL PRIMARY KEY,
    gym_id BIGINT NOT NULL DEFAULT 1 REFERENCES gym(id),
    kind VARCHAR(12) NOT NULL CHECK (kind IN ('PT', 'DIET', 'PRODUCT')),
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    price_paise BIGINT NOT NULL CHECK (price_paise > 0),
    validity_days INT CHECK (validity_days IS NULL OR validity_days BETWEEN 1 AND 3650),
    stock_on_hand INT CHECK (stock_on_hand IS NULL OR stock_on_hand >= 0),
    sessions_included INT CHECK (sessions_included IS NULL OR sessions_included BETWEEN 1 AND 500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (kind <> 'PRODUCT' OR stock_on_hand IS NOT NULL)
);
CREATE INDEX gym_addon_active_idx ON gym_addon(gym_id, kind, name) WHERE active = TRUE;

CREATE TABLE addon_order (
    id BIGSERIAL PRIMARY KEY,
    gym_id BIGINT NOT NULL DEFAULT 1 REFERENCES gym(id),
    member_id BIGINT NOT NULL REFERENCES member_profile(id),
    addon_id BIGINT NOT NULL REFERENCES gym_addon(id),
    quantity INT NOT NULL CHECK (quantity BETWEEN 1 AND 20),
    unit_price_paise BIGINT NOT NULL CHECK (unit_price_paise > 0),
    total_paise BIGINT NOT NULL CHECK (total_paise > 0),
    sessions_included INT,
    sessions_used INT NOT NULL DEFAULT 0 CHECK (sessions_used >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'PAYMENT_PENDING'
      CHECK (status IN ('PAYMENT_PENDING', 'PAID', 'IN_PROGRESS', 'FULFILLED', 'CANCELLED')),
    paid_at TIMESTAMPTZ,
    fulfilled_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_by BIGINT REFERENCES user_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (sessions_included IS NULL OR sessions_used <= sessions_included)
);
CREATE INDEX addon_order_member_idx ON addon_order(member_id, created_at DESC);
CREATE INDEX addon_order_status_idx ON addon_order(gym_id, status, created_at DESC);

ALTER TABLE payment ADD COLUMN addon_order_id BIGINT REFERENCES addon_order(id);
CREATE UNIQUE INDEX payment_addon_order_uq ON payment(addon_order_id) WHERE addon_order_id IS NOT NULL;

-- Add-on payments are revenue, but must not reduce membership dues.
CREATE OR REPLACE VIEW member_dues_v AS
SELECT m.id AS member_id,
       COALESCE(b.billed, 0) AS billed_paise,
       COALESCE(p.paid, 0)   AS paid_paise,
       COALESCE(b.billed, 0) - COALESCE(p.paid, 0) AS dues_paise
FROM member_profile m
LEFT JOIN (SELECT member_id, SUM(price_charged_paise) AS billed
           FROM membership WHERE status <> 'CANCELLED' GROUP BY member_id) b ON b.member_id = m.id
LEFT JOIN (SELECT member_id, SUM(amount_paise) AS paid
           FROM payment WHERE status = 'RECORDED' AND addon_order_id IS NULL GROUP BY member_id) p ON p.member_id = m.id;
