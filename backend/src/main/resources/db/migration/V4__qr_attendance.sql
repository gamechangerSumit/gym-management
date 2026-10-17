-- V4: QR passes and attendance.
-- Option A (chosen): SHA-256 hash for lookup + AES-GCM encrypted copy so "My Gym" can redisplay the pass.
CREATE TABLE member_qr_pass (
    id          BIGSERIAL PRIMARY KEY,
    member_id   BIGINT      NOT NULL REFERENCES member_profile (id),
    token_hash  CHAR(64)    NOT NULL,
    token_enc   TEXT,                       -- base64(iv || ciphertext+tag); NULL once revoked
    status      VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVOKED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at  TIMESTAMPTZ,
    revoked_by  BIGINT REFERENCES user_account (id)
);
CREATE UNIQUE INDEX member_qr_pass_hash_uq ON member_qr_pass (token_hash);
-- At most one ACTIVE pass per member.
CREATE UNIQUE INDEX member_qr_pass_one_active_uq ON member_qr_pass (member_id) WHERE status = 'ACTIVE';

CREATE TABLE attendance_record (
    id             BIGSERIAL PRIMARY KEY,
    gym_id         BIGINT      NOT NULL DEFAULT 1 REFERENCES gym (id),
    member_id      BIGINT      NOT NULL REFERENCES member_profile (id),
    checked_in_at  TIMESTAMPTZ NOT NULL,
    checkin_date   DATE        NOT NULL,     -- local date in the gym timezone at check-in
    source         VARCHAR(10) NOT NULL CHECK (source IN ('QR', 'MANUAL')),
    recorded_by    BIGINT REFERENCES user_account (id),
    qr_pass_id     BIGINT REFERENCES member_qr_pass (id)
);
CREATE INDEX attendance_member_time_idx ON attendance_record (member_id, checked_in_at DESC);
CREATE INDEX attendance_date_idx ON attendance_record (checkin_date);
