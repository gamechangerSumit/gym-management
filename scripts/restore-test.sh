#!/bin/sh
# Proves a backup is restorable: restores it into a throwaway database and counts rows. Usage: scripts/restore-test.sh <file.dump>
set -e
FILE="${1:?usage: restore-test.sh <backup.dump>}"
TMPDB="gym_restore_test"
docker compose exec -T db sh -c "dropdb -U \"\$POSTGRES_USER\" --if-exists $TMPDB && createdb -U \"\$POSTGRES_USER\" $TMPDB"
docker compose exec -T db sh -c "pg_restore -U \"\$POSTGRES_USER\" -d $TMPDB --no-owner" < "$FILE"
docker compose exec -T db sh -c "psql -U \"\$POSTGRES_USER\" -d $TMPDB -tc \"select 'members='||count(*) from member_profile; select 'payments='||count(*) from payment; select 'migrations='||count(*) from flyway_schema_history;\""
docker compose exec -T db sh -c "dropdb -U \"\$POSTGRES_USER\" $TMPDB"
echo "Restore test OK"
