#!/usr/bin/env bash
# Run ON the VPS from /opt/tirotiro-oro after .env is in place.
set -euo pipefail
cd /opt/tirotiro-oro
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps
