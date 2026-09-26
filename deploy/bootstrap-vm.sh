#!/usr/bin/env bash
# Provisions an Ubuntu VM to run the Aura server under systemd.
# Assumes code already extracted at /opt/aura/server (done by publish.ps1).
set -euo pipefail

NODE_MAJOR="${NODE_MAJOR:-26}"
AURA_USER="aura"
APP_DIR="/opt/aura/server"
DATA_DIR="/var/lib/aura"

echo "[1/6] Installing Node.js ${NODE_MAJOR}..."
if ! command -v node >/dev/null || [[ "$(node -e 'console.log(process.versions.node.split(".")[0])')" != "$NODE_MAJOR" ]]; then
  apt-get update -qq
  apt-get install -y -qq ca-certificates curl gnupg
  mkdir -p /etc/apt/keyrings
  curl -fsSL "https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key" \
    | gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg
  echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_${NODE_MAJOR}.x nodistro main" \
    > /etc/apt/sources.list.d/nodesource.list
  apt-get update -qq
  apt-get install -y -qq nodejs
fi
echo "  node $(node -v), npm $(npm -v)"

echo "[2/6] Creating service user ${AURA_USER}..."
id -u "$AURA_USER" >/dev/null 2>&1 || useradd --system --create-home --home-dir "$DATA_DIR" "$AURA_USER"
mkdir -p "$DATA_DIR"
chown -R "$AURA_USER":"$AURA_USER" "$DATA_DIR" "$APP_DIR"

echo "[3/6] Installing dependencies (incl. tsx runtime)..."
sudo -u "$AURA_USER" bash -c "cd $APP_DIR && npm install --no-audit --no-fund"

echo "[4/6] Fetching yt-dlp Linux binary..."
sudo -u "$AURA_USER" bash -c "cd $APP_DIR && node scripts/setup-ytdlp.mjs"

echo "[5/6] Installing systemd service..."
cat > /etc/systemd/system/aura.service <<EOF
[Unit]
Description=Aura music server
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=${AURA_USER}
WorkingDirectory=${APP_DIR}
Environment=PORT=8787
Environment=AURA_DATA_DIR=${DATA_DIR}
Environment=NODE_ENV=production
ExecStart=/usr/bin/npm start
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF
chmod 644 /etc/systemd/system/aura.service
systemctl daemon-reload
systemctl enable --now aura >/dev/null

if command -v ufw >/dev/null && ufw status | grep -q "Status: active"; then
  echo "  ufw active -> allowing 8787"
  ufw allow 8787/tcp >/dev/null
fi

echo "[6/6] Waiting for health..."
for i in $(seq 1 20); do
  if curl -fsS http://127.0.0.1:8787/api/health >/dev/null 2>&1; then
    echo "READY:"
    curl -fsS http://127.0.0.1:8787/api/health
    echo
    exit 0
  fi
  sleep 1
done
echo "FAILED: server did not become healthy. Log tail:" >&2
journalctl -u aura -n 40 --no-pager >&2
exit 1
