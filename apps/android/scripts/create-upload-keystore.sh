#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
store="$root/mydrive-upload.jks"
props="$root/keystore.properties"
if [[ -f "$store" ]]; then
  echo "Refusing to overwrite $store" >&2
  exit 1
fi
password="${MYDRIVE_STORE_PASSWORD:-$(openssl rand -base64 18)}"
keytool -genkeypair \
  -keystore "$store" \
  -alias mydrive \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass "$password" \
  -keypass "$password" \
  -dname "CN=my-drive, OU=jasensic, O=jasensic, L=LAN, ST=local, C=ES"
cat > "$props" <<EOF
storeFile=mydrive-upload.jks
storePassword=$password
keyAlias=mydrive
keyPassword=$password
EOF
echo "Wrote $store and $props (gitignored)."
echo "GitHub secrets: MYDRIVE_KEYSTORE_BASE64=\$(base64 -w0 \"$store\"), MYDRIVE_STORE_PASSWORD, MYDRIVE_KEY_PASSWORD (same value), MYDRIVE_KEY_ALIAS=mydrive"
