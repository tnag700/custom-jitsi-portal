#!/bin/sh
set -eu

# Reissue the short-lived, one-use wrapped SecretID without exposing the Vault
# root token or wrapping token in command arguments or terminal output.

umask 077

REPOSITORY_DIR="${1:-$(pwd)}"
ENV_FILE="${2:-$REPOSITORY_DIR/.env.production}"
BASE_COMPOSE="$REPOSITORY_DIR/docker-compose.production.yml"

read_env_value() {
  key="$1"
  value="$(awk -F= -v key="$key" '$1 == key {sub(/^[^=]*=/, ""); print; exit}' "$ENV_FILE")"
  test -n "$value" || {
    echo "Required production path is missing: $key" >&2
    exit 1
  }
  printf '%s' "$value"
}

RECOVERY_FILE="$(read_env_value VAULT_RECOVERY_DIR)/init.txt"
WRAPPED_SECRET_ID_FILE="$(read_env_value BACKEND_VAULT_WRAPPED_SECRET_ID_PATH)"
TEMPORARY_FILE="${WRAPPED_SECRET_ID_FILE}.tmp.$$"

cleanup() {
  rm -f "$TEMPORARY_FILE"
}
trap cleanup EXIT HUP INT TERM

test -r "$RECOVERY_FILE" || {
  echo "Vault recovery file is unavailable." >&2
  exit 1
}

awk -F': ' '/Initial Root Token/ {print $2}' "$RECOVERY_FILE" \
  | docker compose --project-name jitsi-prod --env-file "$ENV_FILE" -f "$BASE_COMPOSE" \
      exec -T vault sh -ec '
        IFS= read -r root_token
        export VAULT_TOKEN="$root_token"
        vault write -wrap-ttl=5m -format=json -f auth/approle-workloads/role/backend-service/secret-id \
          | grep "\"token\":" \
          | head -n 1 \
          | cut -d "\"" -f 4
        unset VAULT_TOKEN root_token
      ' > "$TEMPORARY_FILE"

test -s "$TEMPORARY_FILE" || {
  echo "Vault did not issue a wrapped backend SecretID." >&2
  exit 1
}
chmod 0600 "$TEMPORARY_FILE"
cp "$TEMPORARY_FILE" "$WRAPPED_SECRET_ID_FILE"
chmod 0600 "$WRAPPED_SECRET_ID_FILE"
rm -f "$TEMPORARY_FILE"
trap - EXIT HUP INT TERM

echo "A new one-use backend AppRole handoff was issued with a five-minute unwrap TTL."
