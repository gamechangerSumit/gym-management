# Backup and restore

## Backup
`scripts/backup.sh [dir]` writes a compressed `pg_dump` (custom format), keeps the 14 newest, and aborts on an empty file.
Daily cron example (02:30): `30 2 * * * cd /opt/gym && scripts/backup.sh /var/backups/gym`
Copy the directory off the server (another machine or object storage). Keep `APP_QR_ENC_KEY` and `.env` backed up
separately and securely — a database dump alone does not contain them.

## Prove it restores (do this before go-live and monthly)
`scripts/restore-test.sh /var/backups/gym/gym-YYYYMMDD-HHMMSS.dump`
Restores into a throwaway database, prints row counts (members, payments, migrations), then drops it.

## Real restore
1. `docker compose stop backend web`
2. `docker compose exec -T db sh -c 'dropdb -U "$POSTGRES_USER" "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'`
3. `docker compose exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner' < backup.dump`
4. `docker compose start backend web`, sign in, spot-check recent payments and attendance.

Honesty note: these scripts were written but **not executed** in the build environment (no Docker available there).
Run the restore test yourself before relying on them.
