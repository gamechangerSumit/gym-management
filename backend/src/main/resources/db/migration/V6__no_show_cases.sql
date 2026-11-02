-- V6: attendance-driven no-show cases and append-only follow-up history.
CREATE TABLE no_show_case (
    id BIGSERIAL PRIMARY KEY,
    gym_id BIGINT NOT NULL DEFAULT 1 REFERENCES gym(id),
    member_id BIGINT NOT NULL REFERENCES member_profile(id),
    opened_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_attendance_at TIMESTAMPTZ,
    absent_days INTEGER NOT NULL CHECK (absent_days >= 0),
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'RESOLVED', 'CANCELLED')),
    outcome VARCHAR(24)
        CHECK (outcome IS NULL OR outcome IN ('WILL_RETURN', 'INJURED', 'TRAVELLING', 'TIMING_ISSUE', 'UNHAPPY', 'NO_RESPONSE', 'CANCELLED')),
    next_action_date DATE,
    resolved_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX no_show_one_open_case_per_member
    ON no_show_case(member_id) WHERE status = 'OPEN';
CREATE INDEX no_show_open_absence_idx
    ON no_show_case(gym_id, absent_days DESC, opened_at) WHERE status = 'OPEN';

CREATE TABLE no_show_followup_event (
    id BIGSERIAL PRIMARY KEY,
    case_id BIGINT NOT NULL REFERENCES no_show_case(id),
    outcome VARCHAR(24) NOT NULL
        CHECK (outcome IN ('WILL_RETURN', 'INJURED', 'TRAVELLING', 'TIMING_ISSUE', 'UNHAPPY', 'NO_RESPONSE', 'CANCELLED')),
    note VARCHAR(500),
    next_action_date DATE,
    recorded_by BIGINT REFERENCES user_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX no_show_followup_case_idx ON no_show_followup_event(case_id, created_at DESC);
