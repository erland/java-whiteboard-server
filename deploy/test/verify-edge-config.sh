#!/bin/sh
set -eu

PUBLIC_BASE_URL="${PUBLIC_BASE_URL:-http://localhost}"
OIDC_ISSUER="$PUBLIC_BASE_URL/auth/realms/whiteboard"

TMPDIR="$(mktemp -d)"
trap 'rm -rf "$TMPDIR"' EXIT

sed \
  -e "s|__PUBLIC_BASE_URL__|$PUBLIC_BASE_URL|g" \
  -e "s|__OIDC_ISSUER__|$OIDC_ISSUER|g" \
  deploy/test/pwa-whiteboard/config.json.template > "$TMPDIR/config.json"

sed \
  -e "s|__PUBLIC_BASE_URL__|$PUBLIC_BASE_URL|g" \
  deploy/test/keycloak/realm-whiteboard.json.template > "$TMPDIR/realm-whiteboard.json"

printf 'Rendered verification for PUBLIC_BASE_URL=%s\n' "$PUBLIC_BASE_URL"

printf '\n[1/3] Checking PWA issuer...\n'
grep -Fq "\"issuer\": \"$OIDC_ISSUER\"" "$TMPDIR/config.json"
printf 'OK: issuer=%s\n' "$OIDC_ISSUER"

printf '\n[2/3] Checking Keycloak redirect URI...\n'
grep -Fq "$PUBLIC_BASE_URL/pwa-whiteboard/*" "$TMPDIR/realm-whiteboard.json"
printf 'OK: redirect URI contains %s/pwa-whiteboard/*\n' "$PUBLIC_BASE_URL"

printf '\n[3/3] Checking Keycloak web origin...\n'
grep -Fq "\"$PUBLIC_BASE_URL\"" "$TMPDIR/realm-whiteboard.json"
printf 'OK: web origin contains %s\n' "$PUBLIC_BASE_URL"

printf '\nRendered config checks passed.\n'
printf 'This script validates template rendering only. Run the docker compose workflow in deploy/test/VERIFY.md for end-to-end verification.\n'
