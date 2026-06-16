#!/usr/bin/env bash
# Run ON the VPS from /opt/tirotiro-oro after .env is in place.
set -euo pipefail
cd /opt/tirotiro-oro
COMPOSE_FILE="docker-compose.prod.yml"

if docker compose -f "${COMPOSE_FILE}" ps -q 2>/dev/null | grep -q .; then
  echo "Stopping app gracefully (30s timeout)..."
  docker compose -f "${COMPOSE_FILE}" stop -t 30 app || true
  echo "Bringing stack down (containers only; volumes and .env preserved)..."
  docker compose -f "${COMPOSE_FILE}" down --remove-orphans
fi

docker compose -f "${COMPOSE_FILE}" build app
docker compose -f "${COMPOSE_FILE}" up -d
docker compose -f "${COMPOSE_FILE}" ps
