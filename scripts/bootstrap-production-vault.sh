#!/bin/sh
set -eu

REPOSITORY_DIR="${1:-$(pwd)}"
ENV_FILE="${2:-$REPOSITORY_DIR/.env.production}"
BASE_COMPOSE="$REPOSITORY_DIR/docker-compose.production.yml"
BOOTSTRAP_COMPOSE="$REPOSITORY_DIR/docker-compose.production.bootstrap.yml"

test -r "$ENV_FILE" || {
  echo "Production env file is missing." >&2
  exit 1
}

read_env_value() {
  key="$1"
  value="$(awk -F= -v key="$key" '$1 == key {sub(/^[^=]*=/, ""); print; exit}' "$ENV_FILE")"
  test -n "$value" || {
    echo "Required production path is missing: $key" >&2
    exit 1
  }
  printf '%s' "$value"
}

RECOVERY_DIR="$(read_env_value VAULT_RECOVERY_DIR)"
SEED_FILE="$(read_env_value VAULT_SEED_ENV_FILE_PATH)"
RECOVERY_FILE="$RECOVERY_DIR/init.txt"

compose_base() {
  docker compose --project-name jitsi-prod --env-file "$ENV_FILE" -f "$BASE_COMPOSE" "$@"
}

compose_bootstrap() {
  docker compose --project-name jitsi-prod --env-file "$ENV_FILE" \
    -f "$BASE_COMPOSE" -f "$BOOTSTRAP_COMPOSE" "$@"
}

compose_bootstrap up --build -d vault

attempt=0
until compose_bootstrap exec -T vault sh -ec 'vault status >/dev/null 2>&1 || [ "$?" -eq 2 ]'; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 30 ]; then
    echo "Vault did not expose its private TLS listener in time." >&2
    exit 1
  fi
  sleep 2
done

compose_bootstrap exec -T vault sh /vault/bootstrap/bootstrap-production.sh.example
test -s "$RECOVERY_FILE"
test -s "$(read_env_value BACKEND_VAULT_ROLE_ID_PATH)"
test -s "$(read_env_value BACKEND_VAULT_WRAPPED_SECRET_ID_PATH)"

# Recreate Vault without the temporary seed/recovery bootstrap mounts. The
# operator-held recovery file is then used only through stdin for this unseal.
compose_bootstrap stop vault
compose_base up -d --force-recreate vault

attempt=0
until compose_base exec -T vault sh -ec 'vault status >/dev/null 2>&1 || [ "$?" -eq 2 ]'; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 30 ]; then
    echo "Vault did not restart after bootstrap." >&2
    exit 1
  fi
  sleep 2
done

awk -F': ' '/Unseal Key 1/ {print $2}' "$RECOVERY_FILE" \
  | compose_base exec -T vault sh -ec 'IFS= read -r key; vault operator unseal "$key" >/dev/null'
awk -F': ' '/Unseal Key 2/ {print $2}' "$RECOVERY_FILE" \
  | compose_base exec -T vault sh -ec 'IFS= read -r key; vault operator unseal "$key" >/dev/null'

compose_base exec -T vault vault status >/dev/null
rm -f "$SEED_FILE"

echo "Production Vault is initialized, provisioned, unsealed and running without bootstrap mounts."
echo "Move the recovery file to approved offline custody before public cutover."
