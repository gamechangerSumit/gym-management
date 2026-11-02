-- V13: preserve membership hold intervals so paused weeks do not break attendance streaks.
CREATE TABLE membership_hold_event (
    id BIGSERIAL PRIMARY KEY,
    membership_id BIGINT NOT NULL REFERENCES membership(id),
    held_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resumed_at TIMESTAMPTZ,
    reason VARCHAR(300) NOT NULL,
    held_by BIGINT REFERENCES user_account(id),
    resumed_by BIGINT REFERENCES user_account(id),
    CHECK (resumed_at IS NULL OR resumed_at >= held_at)
);
CREATE INDEX membership_hold_interval_idx ON membership_hold_event(membership_id,held_at,resumed_at);
CREATE UNIQUE INDEX membership_one_open_hold_uq ON membership_hold_event(membership_id) WHERE resumed_at IS NULL;

INSERT INTO membership_hold_event(membership_id,held_at,reason)
SELECT id,created_at,COALESCE(hold_reason,'Legacy membership hold')
FROM membership WHERE status='ON_HOLD';
