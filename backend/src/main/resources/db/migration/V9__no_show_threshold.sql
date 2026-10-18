-- V9: separate no-show threshold from the general dashboard drop-off threshold.
ALTER TABLE gym_settings ADD COLUMN no_show_threshold_days INT NOT NULL DEFAULT 10 CHECK (no_show_threshold_days BETWEEN 1 AND 365);
