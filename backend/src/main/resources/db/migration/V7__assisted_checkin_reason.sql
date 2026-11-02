-- V7: record why staff assisted a member check-in.
ALTER TABLE attendance_record ADD COLUMN assisted_reason VARCHAR(80);
-- Existing manual records remain NULL for historical compatibility; new manual check-ins require a reason in the API.
CREATE INDEX attendance_manual_reason_idx ON attendance_record (checkin_date, source) WHERE source = 'MANUAL';
