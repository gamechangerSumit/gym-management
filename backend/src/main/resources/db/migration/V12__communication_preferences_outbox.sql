-- V12: communication preferences and a durable, consent-aware reminder outbox.
ALTER TABLE member_profile
  ADD COLUMN transactional_messages_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN marketing_consent BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE notification_outbox (
    id BIGSERIAL PRIMARY KEY,
    gym_id BIGINT NOT NULL DEFAULT 1 REFERENCES gym(id),
    member_id BIGINT NOT NULL REFERENCES member_profile(id),
    membership_id BIGINT REFERENCES membership(id),
    kind VARCHAR(30) NOT NULL CHECK (kind IN ('RENEWAL_REMINDER', 'NO_SHOW_CARE', 'STREAK_UPDATE', 'PT_SESSION_REMINDER')),
    stage_days INT,
    channel VARCHAR(12) NOT NULL DEFAULT 'IN_APP' CHECK (channel IN ('IN_APP', 'WHATSAPP', 'SMS', 'EMAIL')),
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED'
      CHECK (status IN ('QUEUED', 'SKIPPED_OPT_OUT', 'SENT', 'FAILED', 'CANCELLED')),
    message_text VARCHAR(1000) NOT NULL,
    scheduled_for TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ,
    failure_reason VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX notification_renewal_stage_uq
  ON notification_outbox(membership_id, kind, stage_days)
  WHERE kind='RENEWAL_REMINDER' AND membership_id IS NOT NULL;
CREATE INDEX notification_queue_idx ON notification_outbox(gym_id,status,scheduled_for);
CREATE INDEX notification_member_idx ON notification_outbox(member_id,created_at DESC);
