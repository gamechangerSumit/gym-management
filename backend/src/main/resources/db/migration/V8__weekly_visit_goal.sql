-- V8: configurable weekly attendance goal for member habit tracking.
ALTER TABLE gym_settings ADD COLUMN weekly_visit_goal INT NOT NULL DEFAULT 3 CHECK (weekly_visit_goal BETWEEN 1 AND 14);
