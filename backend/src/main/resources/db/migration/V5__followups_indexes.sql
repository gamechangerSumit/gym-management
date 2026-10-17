-- V5: renewal follow-ups (contact tracking only; nothing is ever sent) and report indexes.
CREATE TABLE renewal_followup (
    id             BIGSERIAL PRIMARY KEY,
    member_id      BIGINT      NOT NULL REFERENCES member_profile (id),
    membership_id  BIGINT      NOT NULL REFERENCES membership (id),
    contact_status VARCHAR(20) NOT NULL DEFAULT 'NOT_CONTACTED'
        CHECK (contact_status IN ('NOT_CONTACTED', 'CONTACTED', 'NO_ANSWER', 'WILL_RENEW', 'NOT_INTERESTED')),
    note           VARCHAR(300),
    updated_by     BIGINT REFERENCES user_account (id),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX renewal_followup_membership_uq ON renewal_followup (membership_id);
CREATE INDEX renewal_followup_member_idx ON renewal_followup (member_id);

CREATE INDEX payment_status_paid_idx ON payment (status, paid_at);
CREATE INDEX membership_status_end_idx ON membership (status, end_date);
CREATE INDEX audit_log_action_idx ON audit_log (action, at DESC);
