#!/usr/bin/env bash
# Puts a published version online: ./deploy.sh sha-14f7f04 (no argument: the TAG already in .env)
# Rollback is the same command with the previous tag.
set -euo pipefail
cd "$(dirname "$0")"

if [ $# -gt 0 ]; then
  sed -i "s/^TAG=.*/TAG=$1/" .env
fi

docker compose pull               # download the images of that tag (only the layers that changed)
docker compose up -d --wait       # recreate what changed and wait until every healthcheck passes (fails otherwise)
docker image prune -af            # delete the images no container uses (old versions; a rollback pulls them again)
docker compose ps

# Cache warming: a freshly started Java takes ~15 s on its first requests (loading classes, compiling hot code) and
# nginx's cache starts empty. Ask for the public data once now, so the first real visitor does not pay for it.
# Best effort: it never fails the deploy.
warm() { curl -fsS -m 60 --retry 5 --retry-delay 10 --retry-all-errors -o /dev/null "$BASE$1" || true; }
DOMAIN=$(grep -s '^DOMAIN=' .env | cut -d= -f2- || true)
if [ -n "$DOMAIN" ] && command -v jq >/dev/null; then
  BASE="https://$DOMAIN/api/public"
  # The same requests the public pages make (home, standings, fixture, scorers, sanctions, history)
  warm /league; warm /tournaments; warm /champions; warm /collage
  for s in $(curl -fsS -m 60 "$BASE/seasons" | jq -r '.[].id' 2>/dev/null || true); do
    for path in scorers figures sanctions; do warm "/seasons/$s/$path"; done
  done
  for t in $(curl -fsS -m 60 "$BASE/tournaments" | jq -r '.[].id' 2>/dev/null || true); do
    for path in categories special-matches yellow-cards; do warm "/tournaments/$t/$path"; done
    for z in $(curl -fsS -m 60 "$BASE/tournaments/$t/categories" | jq -r '.[].zones[].id' 2>/dev/null || true); do
      warm "/zones/$z/standings"; warm "/zones/$z/fixtures"
    done
  done
  echo "cache warmed"
fi
