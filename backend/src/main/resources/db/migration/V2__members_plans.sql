-- V2: members and membership plans.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE SEQUENCE member_code_seq START 1;

CREATE TABLE membership_plan (
    id             BIGSERIAL PRIMARY KEY,
    gym_id         BIGINT       NOT NULL DEFAULT 1 REFERENCES gym (id),
    name           VARCHAR(80)  NOT NULL,
    duration_days  INT          NOT NULL CHECK (duration_days BETWEEN 1 AND 3660),
    price_paise    BIGINT       NOT NULL CHECK (price_paise >= 0),
    description    VARCHAR(500),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX membership_plan_name_uq ON membership_plan (gym_id, lower(name));

CREATE TABLE member_profile (
    id                 BIGSERIAL PRIMARY KEY,
    gym_id             BIGINT       NOT NULL DEFAULT 1 REFERENCES gym (id),
    member_code        VARCHAR(20)  NOT NULL,
    full_name          VARCHAR(120) NOT NULL,
    phone              VARCHAR(20)  NOT NULL,
    email              VARCHAR(160),
    join_date          DATE         NOT NULL,
    emergency_contact  VARCHAR(200),
    status             VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    staff_notes        TEXT,
    user_id            BIGINT REFERENCES user_account (id),
    is_demo            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX member_profile_code_uq  ON member_profile (member_code);
CREATE UNIQUE INDEX member_profile_phone_uq ON member_profile (phone);
CREATE UNIQUE INDEX member_profile_user_uq  ON member_profile (user_id) WHERE user_id IS NOT NULL;
CREATE INDEX member_profile_name_trgm  ON member_profile USING gin (full_name gin_trgm_ops);
CREATE INDEX member_profile_phone_trgm ON member_profile USING gin (phone gin_trgm_ops);
CREATE INDEX member_profile_code_trgm  ON member_profile USING gin (member_code gin_trgm_ops);
CREATE INDEX member_profile_status_idx ON member_profile (status);
