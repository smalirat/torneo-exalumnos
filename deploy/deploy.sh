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
