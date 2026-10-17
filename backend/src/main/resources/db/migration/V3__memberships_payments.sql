-- V3: memberships (with overlap exclusion), payments, receipt sequence, dues view.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE membership (
    id                  BIGSERIAL PRIMARY KEY,
    gym_id              BIGINT      NOT NULL DEFAULT 1 REFERENCES gym (id),
    member_id           BIGINT      NOT NULL REFERENCES member_profile (id),
    plan_id             BIGINT      NOT NULL REFERENCES membership_plan (id),
    start_date          DATE        NOT NULL,
    end_date            DATE        NOT NULL,      -- inclusive
    price_charged_paise BIGINT      NOT NULL CHECK (price_charged_paise >= 0),
    -- Stored lifecycle flag. Display status (SCHEDULED/ACTIVE/EXPIRING_SOON/EXPIRED) is derived from dates.
    status              VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ON_HOLD', 'CANCELLED')),
    renewed_from_id     BIGINT REFERENCES membership (id),
    hold_reason         VARCHAR(300),
    cancel_reason       VARCHAR(300),
    created_by          BIGINT REFERENCES user_account (id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT membership_dates_ok CHECK (end_date >= start_date),
    -- Prevents overlapping non-cancelled periods for the same member (inclusive ranges).
    CONSTRAINT membership_no_overlap EXCLUDE USING gist (
        member_id WITH =,
        daterange(start_date, end_date, '[]') WITH &&
    ) WHERE (status <> 'CANCELLED')
);
CREATE INDEX membership_member_idx ON membership (member_id, end_date DESC);
CREATE INDEX membership_end_idx    ON membership (end_date) WHERE status <> 'CANCELLED';
CREATE INDEX membership_renewed_idx ON membership (renewed_from_id);

CREATE SEQUENCE receipt_seq START 1;

CREATE TABLE payment (
    id             BIGSERIAL PRIMARY KEY,
    gym_id         BIGINT      NOT NULL DEFAULT 1 REFERENCES gym (id),
    member_id      BIGINT      NOT NULL REFERENCES member_profile (id),
    membership_id  BIGINT REFERENCES membership (id),
    amount_paise   BIGINT      NOT NULL CHECK (amount_paise > 0),
    paid_at        TIMESTAMPTZ NOT NULL,
    method         VARCHAR(20) NOT NULL CHECK (method IN ('CASH', 'UPI', 'CARD', 'BANK_TRANSFER', 'OTHER')),
    receipt_no     VARCHAR(30) NOT NULL,
    status         VARCHAR(10) NOT NULL DEFAULT 'RECORDED' CHECK (status IN ('RECORDED', 'VOID')),
    void_reason    VARCHAR(300),
    voided_by      BIGINT REFERENCES user_account (id),
    voided_at      TIMESTAMPTZ,
    recorded_by    BIGINT REFERENCES user_account (id),
    note           VARCHAR(300),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT payment_void_has_reason CHECK (status <> 'VOID' OR (void_reason IS NOT NULL AND length(trim(void_reason)) > 0))
);
CREATE UNIQUE INDEX payment_receipt_uq ON payment (receipt_no);
CREATE INDEX payment_member_idx ON payment (member_id, paid_at DESC);
CREATE INDEX payment_paid_at_idx ON payment (paid_at);

-- Dues are always computed from records; there is no editable balance column.
-- dues = SUM(price of non-cancelled memberships) - SUM(amount of RECORDED payments)
CREATE VIEW member_dues_v AS
SELECT m.id AS member_id,
       COALESCE(b.billed, 0) AS billed_paise,
       COALESCE(p.paid, 0)   AS paid_paise,
       COALESCE(b.billed, 0) - COALESCE(p.paid, 0) AS dues_paise
FROM member_profile m
LEFT JOIN (SELECT member_id, SUM(price_charged_paise) AS billed
           FROM membership WHERE status <> 'CANCELLED' GROUP BY member_id) b ON b.member_id = m.id
LEFT JOIN (SELECT member_id, SUM(amount_paise) AS paid
           FROM payment WHERE status = 'RECORDED' GROUP BY member_id) p ON p.member_id = m.id;
