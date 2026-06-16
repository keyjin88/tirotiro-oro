# Deployment on Reg.ru VPS (Docker Compose)

This guide describes how to run **tirotiro-oro** on a Reg.ru (or similar) Ubuntu VPS with Docker Compose and optional nginx on port 80.

Secrets never belong in git. Copy `.env.example` to `.env` on the server only.

## Server layout

| Path | Purpose |
|------|---------|
| `/opt/tirotiro-oro/` | Application sources, `docker-compose.prod.yml`, `.env` |
| `/opt/tirotiro-oro/.env` | Production secrets (`chmod 600`) |

## Prerequisites

- Ubuntu 22.04+ / 26.04 LTS (tested on 26.04)
- SSH access as `root` or a sudo user
- Docker Engine + Compose plugin (`docker compose version`)
- Inbound TCP **80** (and optionally **8080** for direct app access)

### Install Docker (once)

Use Docker’s official Ubuntu repository (replace `resolute` with your `VERSION_CODENAME` if different):

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" | sudo tee /etc/apt/sources.list.d/docker.list
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
```

## Configure environment

On the server:

```bash
sudo mkdir -p /opt/tirotiro-oro
cd /opt/tirotiro-oro
sudo cp .env.example .env   # after syncing the repo
sudo chmod 600 .env
sudo nano .env
```

Required variables (see `.env.example`):

- `POSTGRES_PASSWORD` — strong random password
- `APP_BOOTSTRAP_ADMIN_EMAIL` / `APP_BOOTSTRAP_ADMIN_PASSWORD` / `APP_BOOTSTRAP_ADMIN_NAME` — first admin (only if no admin exists yet)
- `SPRING_LIQUIBASE_CONTEXTS` — **leave empty** in production (no `local` demo data)
- `APP_HTTP_PORT` — host port mapped to container `8080` (default `8080`)

## Deploy application files

From your workstation (replace `YOUR_VPS_IP`):

```bash
cd /path/to/tirotiro-oro
tar czf /tmp/tirotiro-oro-deploy.tgz \
  --exclude='./target' --exclude='./.git' --exclude='./.env' --exclude='.idea' .
scp /tmp/tirotiro-oro-deploy.tgz root@YOUR_VPS_IP:/opt/tirotiro-oro/
ssh root@YOUR_VPS_IP 'tar -xzf /opt/tirotiro-oro/tirotiro-oro-deploy.tgz -C /opt/tirotiro-oro'
```

Ensure `.env` exists on the server before starting the stack.

## Start / update stack

### From your workstation (recommended)

`scripts/update-remote.sh` rsyncs the repo to the VPS, stops the running stack (graceful app shutdown, then `docker compose down` without removing volumes or `.env`), rebuilds the app image, and starts the stack again:

```bash
cd /path/to/tirotiro-oro
./scripts/update-remote.sh
# Optional overrides: DEPLOY_HOST, DEPLOY_USER, REMOTE_DIR
```

### On the server

```bash
cd /opt/tirotiro-oro
./scripts/deploy-regru-remote.sh
```

Or manually (same stop → build → up sequence):

```bash
cd /opt/tirotiro-oro
docker compose -f docker-compose.prod.yml stop -t 30 app
docker compose -f docker-compose.prod.yml down --remove-orphans
docker compose -f docker-compose.prod.yml build app
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f app
```

## HTTP on port 80 (nginx)

For MVP without a domain, nginx can proxy port 80 to the app on `127.0.0.1:8080`:

```bash
sudo apt-get install -y nginx
sudo tee /etc/nginx/sites-available/tirotiro-oro <<'NGINX'
server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name _;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
NGINX
sudo rm -f /etc/nginx/sites-enabled/default
sudo ln -sf /etc/nginx/sites-available/tirotiro-oro /etc/nginx/sites-enabled/tirotiro-oro
sudo nginx -t && sudo systemctl reload nginx
```

## Verify

```bash
curl -I http://YOUR_VPS_IP/login          # expect HTTP 200
curl -I http://YOUR_VPS_IP/               # expect HTTP 302 → /login
curl -I http://YOUR_VPS_IP:8080/login     # direct app port (if exposed)
```

Log in at `/login` with the bootstrap admin from `.env`.

## Security checklist

1. **Do not paste passwords into chat or commit `.env`.**
2. Prefer **SSH keys** instead of root password login; disable password auth when keys work.
3. Rotate bootstrap admin and database passwords after first login.
4. Restrict firewall (e.g. `ufw allow OpenSSH`, `ufw allow 80/tcp`, `ufw enable`); avoid exposing PostgreSQL publicly (prod compose does not publish `5432`).
5. Add TLS when you have a domain (Let’s Encrypt + certbot).

## Troubleshooting

| Symptom | Action |
|---------|--------|
| App exits on startup | `docker compose ... logs app` — often missing bootstrap admin env or DB not ready |
| 502 from nginx | Check `docker compose ps`, app listening on 8080 |
| Old code after deploy | `docker compose -f docker-compose.prod.yml up -d --build --force-recreate app` |

See also: [deployment-hosting-russia.md](./deployment-hosting-russia.md) for hosting context.
