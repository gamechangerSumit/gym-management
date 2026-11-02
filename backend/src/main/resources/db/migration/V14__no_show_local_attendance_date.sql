-- V14: preserve the gym-local attendance date separately from the UTC timestamp.
ALTER TABLE no_show_case ADD COLUMN last_attendance_date DATE;
UPDATE no_show_case c
SET last_attendance_date = (
    SELECT MAX(a.checkin_date) FROM attendance_record a WHERE a.member_id = c.member_id
);
