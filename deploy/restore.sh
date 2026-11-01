#!/usr/bin/env bash
# Replaces the database with a backup: ./restore.sh backups/torneo-20261026-040000.sql.gz
# The backend is stopped meanwhile so nobody writes in the middle.
set -euo pipefail
cd "$(dirname "$0")"

if [ $# -ne 1 ] || [ ! -f "$1" ]; then
  echo "usage: $0 backups/torneo-YYYYMMDD-HHMMSS.sql.gz" >&2
  exit 1
fi

docker compose stop api
# As torneo_owner, not the superuser: the restored tables belong to it and torneo_app gets its usual grants
gunzip -c "$1" | docker compose exec -T db sh -c 'psql -q -v ON_ERROR_STOP=1 -U torneo_owner -d "$POSTGRES_DB"'
docker compose start api
echo "restored $1"
