#!/bin/sh
# Daily logical backup of the database. Usage: scripts/backup.sh [output-dir]   (run from the project root)
# Keeps the 14 newest dumps. Copy the output directory off the server (and keep APP_QR_ENC_KEY safe separately).
set -e
OUT="${1:-backups}"
mkdir -p "$OUT"
STAMP="$(date +%Y%m%d-%H%M%S)"
FILE="$OUT/gym-$STAMP.dump"
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$FILE"
[ -s "$FILE" ] || { echo "backup is empty, aborting" >&2; rm -f "$FILE"; exit 1; }
ls -1t "$OUT"/gym-*.dump | tail -n +15 | xargs -r rm -f
echo "Backup written: $FILE"
