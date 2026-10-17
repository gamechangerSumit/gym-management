# Runbook

| Situation | What to do |
|---|---|
| App down | `docker compose ps`, `docker compose logs --tail=200 backend`. Health: `GET /actuator/health` (via the backend container). Restart: `docker compose restart backend`. |
| Staff/member forgot password | Owner: Staff page → *Reset link* (staff) or member page flow; share the one-time link in person. Links expire in 2 hours and work once. |
| Account locked (5 wrong passwords) | Locks for 15 minutes automatically. Owner can issue a reset link. |
| Member's phone lost / QR shared | Member: *My QR → Get a new code*. Staff: member page → QR pass → Reissue. The old code stops immediately. |
| Wrong payment | Owner voids it with a reason (never edited or deleted); record the correct one. Both stay in the audit log. |
| Wrong membership | Owner cancels with a reason, then assigns correctly. |
| Suspected misuse | Owner → Audit log. Disable the staff account; reset links; reissue affected QR passes. |
| Disk filling | Check `docker system df`, backup directory, and `docker compose logs` size. |
| Time looks wrong | Settings → Time zone (default Asia/Kolkata); "today" and expiries follow it. |
| Restore needed | See BACKUP_RESTORE.md. |

Logs are key=value lines; passwords, tokens and session ids are scrubbed. Do not paste raw logs publicly regardless.
