#!/usr/bin/env bash
# Sync project to a remote VPS and rebuild the production Docker stack.
# Secrets stay in ${REMOTE_DIR}/.env on the server — never commit or rsync .env.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPLOY_HOST="${DEPLOY_HOST:-194.226.97.139}"
DEPLOY_USER="${DEPLOY_USER:-root}"
REMOTE_DIR="${REMOTE_DIR:-/opt/tirotiro-oro}"
REMOTE="${DEPLOY_USER}@${DEPLOY_HOST}"
COMPOSE_FILE="docker-compose.prod.yml"

cd "${ROOT}"

echo "==> Target: ${REMOTE}:${REMOTE_DIR}"

echo "==> Ensuring remote directory exists"
ssh "${REMOTE}" "mkdir -p '${REMOTE_DIR}'"

echo "==> Stopping running stack on server (keeps postgres volume and .env)"
ssh "${REMOTE}" bash -s <<EOF
set -euo pipefail
cd '${REMOTE_DIR}'
if [[ -f '${COMPOSE_FILE}' ]] && docker compose -f '${COMPOSE_FILE}' ps -q 2>/dev/null | grep -q .; then
  echo "Stopping app gracefully (30s timeout)..."
  docker compose -f '${COMPOSE_FILE}' stop -t 30 app || true
  echo "Bringing stack down (containers only; volumes and .env preserved)..."
  docker compose -f '${COMPOSE_FILE}' down --remove-orphans
else
  echo "No running stack found; skipping stop."
fi
EOF

echo "==> Syncing files (excluding .git, target, secrets)"
rsync -avz --delete \
  --exclude '.git/' \
  --exclude 'target/' \
  --exclude 'build/' \
  --exclude '.env' \
  --exclude '.env.*' \
  --exclude '.idea/' \
  --exclude '.cursor/' \
  --exclude 'node_modules/' \
  --exclude '.DS_Store' \
  --exclude '*.log' \
  "${ROOT}/" "${REMOTE}:${REMOTE_DIR}/"

echo "==> Building and restarting app on server"
ssh "${REMOTE}" bash -s <<EOF
set -euo pipefail
cd '${REMOTE_DIR}'
if [[ ! -f .env ]]; then
  echo "ERROR: ${REMOTE_DIR}/.env not found. Copy .env.example to .env on the server and set secrets." >&2
  exit 1
fi
if [[ ! -f '${COMPOSE_FILE}' ]]; then
  echo "ERROR: ${COMPOSE_FILE} not found in ${REMOTE_DIR}" >&2
  exit 1
fi
docker compose -f '${COMPOSE_FILE}' build app
docker compose -f '${COMPOSE_FILE}' up -d
docker compose -f '${COMPOSE_FILE}' ps
EOF

echo "==> Waiting for /login to respond"
ssh "${REMOTE}" bash -s <<EOF
set -euo pipefail
cd '${REMOTE_DIR}'
PORT="\$(grep -E '^APP_HTTP_PORT=' .env 2>/dev/null | cut -d= -f2- | tr -d '\r' || true)"
PORT="\${PORT:-8080}"
URL="http://127.0.0.1:\${PORT}/login"
for attempt in \$(seq 1 30); do
  code="\$(curl -s -o /dev/null -w '%{http_code}' "\${URL}" || true)"
  if [[ "\${code}" == "200" ]]; then
    echo "OK  \${URL}  HTTP \${code}"
    exit 0
  fi
  echo "… attempt \${attempt}/30: HTTP \${code:-000}, retrying in 5s"
  sleep 5
done
echo "FAIL \${URL} did not return HTTP 200 in time" >&2
exit 1
EOF

echo "==> Update complete"
