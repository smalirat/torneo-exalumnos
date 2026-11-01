#!/usr/bin/env bash
# Compressed dump of the database into backups/, deleting the ones older than KEEP_DAYS (.env, default 14).
# With OFFSITE_URL in .env, also uploads it outside the server (write-only Object Storage URL, infra/oci_backup_bucket.py),
# encrypted with the public key in backup-key.asc: only the private key, kept off the server, can open it.
# Without that file there is no upload (a backup with every DNI never leaves the server unencrypted).
# With HEALTHCHECK_URL, reports to healthchecks.io: start, success or failure (and it alerts if no report arrives).
# On the server it runs every night from cron (server in UTC): 0 7 * * * /opt/torneo/backup.sh >> /opt/torneo/backups/backup.log 2>&1
set -euo pipefail
cd "$(dirname "$0")"
umask 077   # backups have personal data: new files readable by this user only

env_value() { grep -s "^$1=" .env | cut -d= -f2- || true; }
KEEP_DAYS=$(env_value KEEP_DAYS)
KEEP_DAYS=${KEEP_DAYS:-14}
OFFSITE_URL=$(env_value OFFSITE_URL)
HEALTHCHECK_URL=$(env_value HEALTHCHECK_URL)

ping() { [ -z "$HEALTHCHECK_URL" ] || curl -fsS -m 10 --retry 3 -o /dev/null "$HEALTHCHECK_URL$1" || true; }
trap 'ping /fail' ERR   # any failing step reports the failure
ping /start

mkdir -p backups
chmod 700 backups
name="torneo-$(date +%Y%m%d-%H%M%S).sql.gz"
file="backups/$name"

# --clean --if-exists: the dump drops each table before creating it, so it can be restored over an existing base
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" --clean --if-exists --no-owner "$POSTGRES_DB"' \
  | gzip > "$file.tmp"
mv "$file.tmp" "$file"   # only a complete dump gets the real name

if [ -n "$OFFSITE_URL" ]; then
  if [ ! -f backup-key.asc ]; then
    echo "OFFSITE_URL is set but backup-key.asc is missing: not uploading an unencrypted backup" >&2
    false   # fails the run (and reports it)
  fi
  # Public-key encryption: the server can lock the file but not open it again
  gpg --batch --yes --quiet --trust-model always --recipient-file backup-key.asc --output "$file.gpg" --encrypt "$file"
  # PUT to <url><object name>; -f turns an HTTP error (expired URL, bucket gone) into a failure
  curl -fsS -m 120 --retry 3 -T "$file.gpg" "$OFFSITE_URL$name.gpg"
  rm "$file.gpg"
  where="local + off-site encrypted"
else
  where="local only"
fi

find backups -name 'torneo-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
echo "$(date '+%F %T') backup ok ($where): $file ($(du -h "$file" | cut -f1))"
ping ""
