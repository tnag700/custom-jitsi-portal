#!/bin/sh
set -eu

# Migrate one restored active DEV config set into the production JWT contour.
# Plaintext keys and secrets move only through anonymous pipes and shell memory;
# they are never command arguments, files, SQL source, logs, or stdout.

umask 077

REPOSITORY_DIR="${1:-$(pwd)}"
ENV_FILE="${2:-$REPOSITORY_DIR/.env.production}"
BASE_COMPOSE="$REPOSITORY_DIR/docker-compose.production.yml"
SQL_FILE="$REPOSITORY_DIR/scripts/migrate-production-active-config-set.sql"
TRACE_ID="production-bootstrap-$(date -u +%Y%m%dT%H%M%SZ)"

read_env_value() {
  key="$1"
  value="$(awk -F= -v key="$key" '$1 == key {sub(/^[^=]*=/, ""); print; exit}' "$ENV_FILE")"
  test -n "$value" || {
    echo "Required production value is missing: $key" >&2
    exit 1
  }
  printf '%s' "$value"
}

compose() {
  docker compose --project-name jitsi-prod --env-file "$ENV_FILE" -f "$BASE_COMPOSE" "$@"
}

vault_write_config_key() {
  value="$1"
  {
    printf '%s\n' "$ROOT_TOKEN"
    printf '%s\n' "$value"
  } | compose exec -T vault sh -ec '
    IFS= read -r root_token
    IFS= read -r config_key
    export VAULT_TOKEN="$root_token"
    vault kv patch kv/app/backend/runtime APP_CONFIG_SETS_ENCRYPTION_KEY="$config_key" >/dev/null
    unset VAULT_TOKEN root_token config_key
  '
}

PRODUCTION_ISSUER="$(read_env_value APP_MEETINGS_TOKEN_ISSUER)"
PRODUCTION_MEETINGS_SERVICE_URL="$(read_env_value APP_MEETINGS_SERVICE_URL)"
PRODUCTION_AUDIENCE="$(read_env_value APP_MEETINGS_TOKEN_AUDIENCE)"
PRODUCTION_ALGORITHM="$(read_env_value APP_MEETINGS_TOKEN_ALGORITHM)"
PRODUCTION_ROLE_CLAIM="$(read_env_value APP_MEETINGS_TOKEN_ROLE_CLAIM_NAME)"
RECOVERY_FILE="$(read_env_value VAULT_RECOVERY_DIR)/init.txt"

test -r "$RECOVERY_FILE" || {
  echo "Vault recovery file is unavailable." >&2
  exit 1
}
test -r "$SQL_FILE" || {
  echo "Production config-set migration SQL is unavailable." >&2
  exit 1
}
command -v openssl >/dev/null 2>&1 || {
  echo "openssl is required for production config-set migration." >&2
  exit 1
}

ROOT_TOKEN="$(awk -F': ' '/Initial Root Token/ {print $2}' "$RECOVERY_FILE")"
test -n "$ROOT_TOKEN"

VAULT_VALUES="$(printf '%s\n' "$ROOT_TOKEN" | compose exec -T vault sh -ec '
  IFS= read -r root_token
  export VAULT_TOKEN="$root_token"
  printf "%s\n" "$(vault kv get -field=APP_CONFIG_SETS_ENCRYPTION_KEY kv/app/backend/runtime)"
  printf "%s\n" "$(vault kv get -field=APP_MEETINGS_TOKEN_SIGNING_SECRET kv/app/backend/runtime)"
  unset VAULT_TOKEN root_token
')"
OLD_CONFIG_KEY="$(printf '%s\n' "$VAULT_VALUES" | sed -n '1p')"
MEETINGS_SIGNING_SECRET="$(printf '%s\n' "$VAULT_VALUES" | sed -n '2p')"
test -n "$OLD_CONFIG_KEY"
test -n "$MEETINGS_SIGNING_SECRET"
unset VAULT_VALUES

# A canonical Base64 representation of 32 random bytes is unambiguous under
# the application's decode-first key parser.
NEW_CONFIG_KEY="$(openssl rand -base64 32 | tr -d '\r\n')"
test -n "$NEW_CONFIG_KEY"
ENCRYPTED_SECRET="$(
  printf '%s\n%s\n' "$NEW_CONFIG_KEY" "$MEETINGS_SIGNING_SECRET" \
    | docker run --rm -i \
        -v "$REPOSITORY_DIR/scripts:/scripts:ro" \
        node:24.18.0-alpine \
        node /scripts/encrypt-config-set-secret.mjs
)"
test -n "$ENCRYPTED_SECRET"

VAULT_ROTATED=0
DB_MIGRATED=0
cleanup() {
  status="$?"
  trap - EXIT HUP INT TERM
  if test "$VAULT_ROTATED" -eq 1 && test "$DB_MIGRATED" -eq 0; then
    if ! vault_write_config_key "$OLD_CONFIG_KEY"; then
      echo "CRITICAL: database migration failed and the previous Vault key could not be restored." >&2
      status=10
    fi
  fi
  unset ROOT_TOKEN OLD_CONFIG_KEY NEW_CONFIG_KEY MEETINGS_SIGNING_SECRET ENCRYPTED_SECRET
  exit "$status"
}
trap cleanup EXIT HUP INT TERM

compose stop backend frontend >/dev/null
vault_write_config_key "$NEW_CONFIG_KEY"
VAULT_ROTATED=1

# First input line is ciphertext; the remaining stream is the reviewed SQL.
# The inner shell turns it into a psql variable without putting it in argv.
if ! {
  printf '%s\n' "$ENCRYPTED_SECRET"
  cat "$SQL_FILE"
} | compose exec -T \
    -e PRODUCTION_ISSUER="$PRODUCTION_ISSUER" \
    -e PRODUCTION_AUDIENCE="$PRODUCTION_AUDIENCE" \
    -e PRODUCTION_ALGORITHM="$PRODUCTION_ALGORITHM" \
    -e PRODUCTION_ROLE_CLAIM="$PRODUCTION_ROLE_CLAIM" \
    -e PRODUCTION_MEETINGS_SERVICE_URL="$PRODUCTION_MEETINGS_SERVICE_URL" \
    -e MIGRATION_TRACE_ID="$TRACE_ID" \
    db sh -ec '
      IFS= read -r encrypted_secret
      {
        printf "\\set encrypted_signing_secret %s\n" "$encrypted_secret"
        cat
      } | PGPASSWORD="$POSTGRES_PASSWORD" psql \
          --username="$POSTGRES_USER" \
          --dbname="$POSTGRES_DB" \
          --set=production_issuer="$PRODUCTION_ISSUER" \
          --set=production_audience="$PRODUCTION_AUDIENCE" \
          --set=production_algorithm="$PRODUCTION_ALGORITHM" \
          --set=production_role_claim="$PRODUCTION_ROLE_CLAIM" \
          --set=production_access_ttl_minutes=20 \
          --set=production_refresh_ttl_minutes=60 \
          --set=production_meetings_service_url="$PRODUCTION_MEETINGS_SERVICE_URL" \
          --set=migration_trace_id="$MIGRATION_TRACE_ID"
      unset encrypted_secret
    '
then
  echo "Production config-set database migration failed; restoring the previous Vault key." >&2
  exit 1
fi

DB_MIGRATED=1

# The persistent bridge contains the previous encryption key. Remove only its
# two bounded runtime files so the next AppRole handoff renders a fresh bridge.
compose run --rm --no-deps --entrypoint sh backend-vault-bootstrap -ec \
  'rm -f /vault/runtime/backend/runtime.env /vault/runtime/backend-batch-token'

VAULT_ROTATED=0
trap - EXIT HUP INT TERM
unset ROOT_TOKEN OLD_CONFIG_KEY NEW_CONFIG_KEY MEETINGS_SIGNING_SECRET ENCRYPTED_SECRET

echo "The single restored active config set was transactionally migrated to the production contour."
echo "Issue a fresh backend AppRole handoff before starting the backend."
