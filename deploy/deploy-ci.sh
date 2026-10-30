#!/usr/bin/env bash
# Forced command of the CI's SSH key. In ~/.ssh/authorized_keys:
#   restrict,command="/opt/torneo/deploy-ci.sh" ssh-ed25519 AAAA... github-actions-deploy
# Whatever the CI asks to run arrives here as SSH_ORIGINAL_COMMAND and is only accepted if it is a published image
# tag (sha-1a2b3c4): even with the key, the only thing that can be done is deploying a version CI built.
set -euo pipefail

tag="${SSH_ORIGINAL_COMMAND:-}"
if [[ ! "$tag" =~ ^sha-[0-9a-f]{7,40}$ ]]; then
  echo "refused: expected an image tag like sha-1a2b3c4, got '$tag'" >&2
  exit 1
fi
exec /opt/torneo/deploy.sh "$tag"
