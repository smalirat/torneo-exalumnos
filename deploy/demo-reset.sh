#!/usr/bin/env bash
# Public demo only: puts the database back to its clean copy (backups/demo-golden.sql.gz), so whatever visitors did
# with the public "demo" login (delete teams, rename them, upload photos) lasts at most a day.
#   Save the clean copy once, when the demo looks right:
#     docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" --clean --if-exists --no-owner "$POSTGRES_DB"' \
#       | gzip > backups/demo-golden.sql.gz
#   Every night from cron (server in UTC, 08:00 UTC = 05:00 in Argentina):
#     0 8 * * * /opt/torneo/demo-reset.sh >> /opt/torneo/backups/demo-reset.log 2>&1
set -euo pipefail
cd "$(dirname "$0")"

GOLDEN=backups/demo-golden.sql.gz
grep -qs '^APP_DEMO_ENABLED=true' .env || { echo "not a demo (APP_DEMO_ENABLED is not true in .env): nothing done" >&2; exit 1; }
[ -f "$GOLDEN" ] || { echo "$GOLDEN is missing: save the clean copy first (see the top of this file)" >&2; exit 1; }

./restore.sh "$GOLDEN"
docker compose up -d --wait api >/dev/null   # restore.sh restarts it; wait until it answers again
# The same cache warming as a deploy (the public answers changed back)
sed -n '/^# Cache warming/,$p' deploy.sh | bash
echo "$(date '+%F %T') demo reset to $GOLDEN"
