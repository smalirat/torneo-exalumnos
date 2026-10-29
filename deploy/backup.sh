#!/usr/bin/env bash
# Compressed dump of the database into backups/, deleting the ones older than KEEP_DAYS (.env, default 14).
# On the server it runs every night from cron: 0 4 * * * /opt/torneo/backup.sh >> /opt/torneo/backups/backup.log 2>&1
set -euo pipefail
cd "$(dirname "$0")"

KEEP_DAYS=$(grep -s '^KEEP_DAYS=' .env | cut -d= -f2)
KEEP_DAYS=${KEEP_DAYS:-14}
mkdir -p backups
file="backups/torneo-$(date +%Y%m%d-%H%M%S).sql.gz"

# --clean --if-exists: the dump drops each table before creating it, so it can be restored over an existing base
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" --clean --if-exists --no-owner "$POSTGRES_DB"' \
  | gzip > "$file.tmp"
mv "$file.tmp" "$file"   # only a complete dump gets the real name

find backups -name 'torneo-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
echo "$(date '+%F %T') backup ok: $file ($(du -h "$file" | cut -f1))"
