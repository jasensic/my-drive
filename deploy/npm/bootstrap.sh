#!/bin/sh
# Create Nginx Proxy Manager proxy hosts from environment variables.
# Safe to re-run: existing domain names are left alone.
set -eu

API="${NPM_API:-http://nginx-proxy-manager:81}"
EMAIL="${NPM_ADMIN_EMAIL:-admin@example.com}"
PASSWORD="${NPM_ADMIN_PASSWORD:-changeme}"

json_escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

token=""
i=0
while [ "$i" -lt 40 ]; do
  body=$(curl -sf -X POST "$API/api/tokens" \
    -H 'Content-Type: application/json' \
    -d "{\"identity\":\"$(json_escape "$EMAIL")\",\"secret\":\"$(json_escape "$PASSWORD")\"}" || true)
  token=$(printf '%s' "$body" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
  if [ -n "$token" ]; then
    break
  fi
  i=$((i + 1))
  sleep 3
done

if [ -z "$token" ]; then
  echo "npm-bootstrap: could not sign in to $API. Create proxy hosts in the admin UI."
  exit 0
fi

existing=$(curl -sf -H "Authorization: Bearer $token" "$API/api/nginx/proxy-hosts" || printf '%s' '[]')

ensure_host() {
  domain="$1"
  forward_host="$2"
  forward_port="$3"
  if [ -z "$domain" ] || [ -z "$forward_host" ] || [ -z "$forward_port" ]; then
    return 0
  fi
  if printf '%s' "$existing" | grep -q "\"$domain\""; then
    echo "npm-bootstrap: $domain already exists"
    return 0
  fi
  code=$(curl -s -o /tmp/npm-proxy-host.json -w '%{http_code}' -X POST "$API/api/nginx/proxy-hosts" \
    -H "Authorization: Bearer $token" \
    -H 'Content-Type: application/json' \
    -d "{\"domain_names\":[\"$(json_escape "$domain")\"],\"forward_scheme\":\"http\",\"forward_host\":\"$(json_escape "$forward_host")\",\"forward_port\":$forward_port,\"access_list_id\":0,\"certificate_id\":0,\"ssl_forced\":false,\"caching_enabled\":false,\"block_exploits\":false,\"advanced_config\":\"client_max_body_size 512m;\",\"meta\":{\"letsencrypt_agree\":false,\"dns_challenge\":false},\"allow_websocket_upgrade\":true,\"http2_support\":false,\"enabled\":true,\"hsts_enabled\":false,\"hsts_subdomains\":false,\"trust_forwarded_proto\":false,\"locations\":[]}")
  if [ "$code" = "200" ] || [ "$code" = "201" ]; then
    echo "npm-bootstrap: created $domain -> $forward_host:$forward_port"
  else
    echo "npm-bootstrap: failed to create $domain (HTTP $code)"
    cat /tmp/npm-proxy-host.json || true
  fi
}

ensure_host "${PORTAL_DOMAIN:-}" "${PORTAL_UPSTREAM_HOST:-}" "${PORTAL_UPSTREAM_PORT:-}"
ensure_host "${API_DOMAIN:-}" "${API_UPSTREAM_HOST:-}" "${API_UPSTREAM_PORT:-}"
ensure_host "${MINIO_DOMAIN:-}" "${MINIO_UPSTREAM_HOST:-}" "${MINIO_UPSTREAM_PORT:-}"
ensure_host "${MINIO_CONSOLE_DOMAIN:-}" "${MINIO_CONSOLE_UPSTREAM_HOST:-}" "${MINIO_CONSOLE_UPSTREAM_PORT:-}"
