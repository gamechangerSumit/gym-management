-- V16: short-lived QR challenges displayed at the gym for member self-check-in.
CREATE TABLE gym_checkin_qr (
    token_hash CHAR(64) PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    created_by BIGINT REFERENCES user_account(id)
);
CREATE INDEX gym_checkin_qr_expiry_idx ON gym_checkin_qr (expires_at);
