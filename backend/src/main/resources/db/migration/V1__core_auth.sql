-- V1: gym, settings, accounts, staff permissions, one-time tokens, audit log, session tables.
-- All timestamps are timestamptz (UTC instants). Money is integer paise (BIGINT).

CREATE TABLE gym (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- One gym per deployment; tables carry gym_id for later multi-branch expansion.
INSERT INTO gym (id, name) VALUES (1, 'My Gym');
SELECT setval(pg_get_serial_sequence('gym', 'id'), 1);

CREATE TABLE gym_settings (
    gym_id                   BIGINT PRIMARY KEY REFERENCES gym (id),
    timezone                 VARCHAR(60) NOT NULL DEFAULT 'Asia/Kolkata',
    duplicate_window_minutes INT         NOT NULL DEFAULT 10  CHECK (duplicate_window_minutes BETWEEN 0 AND 1440),
    drop_off_days            INT         NOT NULL DEFAULT 7   CHECK (drop_off_days BETWEEN 1 AND 365),
    expiring_soon_days       INT         NOT NULL DEFAULT 7   CHECK (expiring_soon_days BETWEEN 0 AND 90),
    member_can_edit_email    BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO gym_settings (gym_id) VALUES (1);

CREATE TABLE user_account (
    id                   BIGSERIAL PRIMARY KEY,
    gym_id               BIGINT       NOT NULL DEFAULT 1 REFERENCES gym (id),
    login_id             VARCHAR(80)  NOT NULL,
    password_hash        VARCHAR(100),
    role                 VARCHAR(10)  NOT NULL CHECK (role IN ('OWNER', 'STAFF', 'MEMBER')),
    status               VARCHAR(10)  NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED', 'INVITED')),
    must_change_password  BOOLEAN     NOT NULL DEFAULT FALSE,
    failed_attempts      INT          NOT NULL DEFAULT 0,
    locked_until         TIMESTAMPTZ,
    last_login_at        TIMESTAMPTZ,
    is_demo              BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT user_account_hash_present CHECK (status = 'INVITED' OR password_hash IS NOT NULL)
);
CREATE UNIQUE INDEX user_account_login_id_uq ON user_account (lower(login_id));
CREATE INDEX user_account_role_idx ON user_account (role);

CREATE TABLE staff_permission (
    user_id                 BIGINT PRIMARY KEY REFERENCES user_account (id) ON DELETE CASCADE,
    can_record_payments     BOOLEAN NOT NULL DEFAULT FALSE,
    can_manage_memberships  BOOLEAN NOT NULL DEFAULT FALSE,
    can_edit_members        BOOLEAN NOT NULL DEFAULT FALSE,
    can_view_reports        BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE one_time_token (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    purpose     VARCHAR(20) NOT NULL CHECK (purpose IN ('INVITE', 'PASSWORD_RESET')),
    token_hash  CHAR(64)    NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX one_time_token_hash_uq ON one_time_token (token_hash);
CREATE INDEX one_time_token_user_idx ON one_time_token (user_id, purpose);

CREATE TABLE audit_log (
    id           BIGSERIAL PRIMARY KEY,
    actor_id     BIGINT REFERENCES user_account (id) ON DELETE SET NULL,
    action       VARCHAR(60) NOT NULL,
    entity_type  VARCHAR(40) NOT NULL,
    entity_id    BIGINT,
    details      JSONB,
    at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_log_at_idx ON audit_log (at DESC);
CREATE INDEX audit_log_entity_idx ON audit_log (entity_type, entity_id);

-- Append-only: block UPDATE and DELETE at the database level.
CREATE FUNCTION audit_log_block_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_block_mutation();

-- Spring Session JDBC schema (PostgreSQL), managed by Flyway instead of auto-init.
CREATE TABLE SPRING_SESSION (
    PRIMARY_ID            CHAR(36) NOT NULL,
    SESSION_ID            CHAR(36) NOT NULL,
    CREATION_TIME         BIGINT   NOT NULL,
    LAST_ACCESS_TIME      BIGINT   NOT NULL,
    MAX_INACTIVE_INTERVAL INT      NOT NULL,
    EXPIRY_TIME           BIGINT   NOT NULL,
    PRINCIPAL_NAME        VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);
CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36)     NOT NULL,
    ATTRIBUTE_NAME     VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES    BYTEA        NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
);
