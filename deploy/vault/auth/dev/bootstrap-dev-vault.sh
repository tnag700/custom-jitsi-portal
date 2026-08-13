#!/bin/sh
set -eu

export VAULT_ADDR="${VAULT_ADDR:-http://vault:8200}"

LOCAL_ROOT="${DEV_VAULT_LOCAL_DIR:-/vault/local/dev}"
INIT_DIR="$LOCAL_ROOT/init"
BACKEND_DIR="$LOCAL_ROOT/backend"
RUNTIME_DIR="$LOCAL_ROOT/runtime"

UNSEAL_KEY_FILE="$INIT_DIR/unseal-key"
ROOT_TOKEN_FILE="$INIT_DIR/root-token"
ROLE_ID_FILE="$BACKEND_DIR/role_id"
WRAPPED_SECRET_ID_FILE="$BACKEND_DIR/wrapped-secret-id"

DEV_VAULT_POSTGRES_USERNAME="${DEV_VAULT_POSTGRES_USERNAME:-jitsi}"
DEV_VAULT_POSTGRES_PASSWORD="${DEV_VAULT_POSTGRES_PASSWORD:-dev-postgres-password}"
DEV_VAULT_REDIS_PASSWORD="${DEV_VAULT_REDIS_PASSWORD:-dev-redis-password}"
DEV_VAULT_KEYCLOAK_ADMIN_PASSWORD="${DEV_VAULT_KEYCLOAK_ADMIN_PASSWORD:-dev-keycloak-admin-password}"
DEV_VAULT_SSO_CLIENT_SECRET="${DEV_VAULT_SSO_CLIENT_SECRET:-jitsi-dev-client-secret}"
DEV_VAULT_APP_MEETINGS_TOKEN_SIGNING_SECRET="${DEV_VAULT_APP_MEETINGS_TOKEN_SIGNING_SECRET:-01234567890123456789012345678901}"
DEV_VAULT_APP_CONFIG_SETS_ENCRYPTION_KEY="${DEV_VAULT_APP_CONFIG_SETS_ENCRYPTION_KEY:-0123456789ABCDEF0123456789ABCDEF}"
DEV_VAULT_JICOFO_AUTH_PASSWORD="${DEV_VAULT_JICOFO_AUTH_PASSWORD:-focus}"
DEV_VAULT_JVB_AUTH_PASSWORD="${DEV_VAULT_JVB_AUTH_PASSWORD:-jvb}"
DEV_VAULT_JICOFO_COMPONENT_SECRET="${DEV_VAULT_JICOFO_COMPONENT_SECRET:-focussecret}"

mkdir -p "$INIT_DIR" "$BACKEND_DIR" "$RUNTIME_DIR"

wait_for_vault() {
  while :; do
    if vault status >/tmp/vault-status 2>&1; then
      return 0
    fi

    if grep -q "Initialized" /tmp/vault-status 2>/dev/null; then
      return 0
    fi

    sleep 1
  done
}

refresh_status() {
  vault status >/tmp/vault-status 2>&1 || true
}

is_initialized() {
  refresh_status
  grep -Eq "Initialized[[:space:]]+true" /tmp/vault-status
}

is_sealed() {
  refresh_status
  grep -Eq "Sealed[[:space:]]+true" /tmp/vault-status
}

write_kv_env() {
  file_path="$1"
  shift

  : > "$file_path"
  while [ "$#" -gt 0 ]; do
    key="$1"
    value="$2"
    printf '%s=%s\n' "$key" "$value" >> "$file_path"
    shift 2
  done
}

read_kv_field() {
  path="$1"
  field="$2"
  vault kv get -field="$field" "$path"
}

read_db_field() {
  path="$1"
  field="$2"
  vault read -field="$field" "$path"
}

wait_for_vault

if is_initialized; then
  if [ ! -f "$UNSEAL_KEY_FILE" ] || [ ! -f "$ROOT_TOKEN_FILE" ]; then
    echo "Vault is already initialized but local dev init files are missing. Remove the dev Vault data and rerun bootstrap." >&2
    exit 1
  fi
else
  INIT_OUTPUT="$(vault operator init -key-shares=1 -key-threshold=1)"
  printf '%s\n' "$INIT_OUTPUT" | awk -F': ' '/Unseal Key 1/ {print $2}' > "$UNSEAL_KEY_FILE"
  printf '%s\n' "$INIT_OUTPUT" | awk -F': ' '/Initial Root Token/ {print $2}' > "$ROOT_TOKEN_FILE"
fi

if is_sealed; then
  vault operator unseal "$(tr -d '\r\n' < "$UNSEAL_KEY_FILE")" >/dev/null
fi

export VAULT_TOKEN="$(tr -d '\r\n' < "$ROOT_TOKEN_FILE")"

if ! vault secrets list | grep -q '^kv/'; then
  vault secrets enable -path=kv -version=2 kv >/dev/null
fi

if ! vault secrets list | grep -q '^database/'; then
  vault secrets enable -path=database -version=1 kv >/dev/null
fi

if ! vault auth list | grep -q '^approle-workloads/'; then
  vault auth enable -path=approle-workloads approle >/dev/null
fi

vault policy write backend-service /vault/policies/backend.hcl >/dev/null

vault write auth/approle-workloads/role/backend-service \
  token_policies=backend-service \
  token_type=batch \
  token_ttl=15m \
  token_max_ttl=30m \
  secret_id_ttl=10m \
  secret_id_num_uses=1 \
  bind_secret_id=true >/dev/null

vault kv put kv/app/backend/runtime \
  APP_MEETINGS_TOKEN_SIGNING_SECRET="$DEV_VAULT_APP_MEETINGS_TOKEN_SIGNING_SECRET" \
  APP_CONFIG_SETS_ENCRYPTION_KEY="$DEV_VAULT_APP_CONFIG_SETS_ENCRYPTION_KEY" >/dev/null

vault kv put kv/identity/backend/oidc \
  SSO_CLIENT_SECRET="$DEV_VAULT_SSO_CLIENT_SECRET" >/dev/null

vault kv put kv/identity/keycloak/runtime \
  KEYCLOAK_ADMIN_PASSWORD="$DEV_VAULT_KEYCLOAK_ADMIN_PASSWORD" \
  SSO_CLIENT_SECRET="$DEV_VAULT_SSO_CLIENT_SECRET" >/dev/null

vault kv put kv/app/redis/runtime \
  REDIS_PASSWORD="$DEV_VAULT_REDIS_PASSWORD" >/dev/null

vault kv put kv/realtime/jitsi/web \
  JWT_APP_SECRET="$DEV_VAULT_APP_MEETINGS_TOKEN_SIGNING_SECRET" \
  JICOFO_AUTH_PASSWORD="$DEV_VAULT_JICOFO_AUTH_PASSWORD" \
  JVB_AUTH_PASSWORD="$DEV_VAULT_JVB_AUTH_PASSWORD" >/dev/null

vault kv put kv/realtime/jitsi/prosody \
  JWT_APP_SECRET="$DEV_VAULT_APP_MEETINGS_TOKEN_SIGNING_SECRET" \
  JICOFO_AUTH_PASSWORD="$DEV_VAULT_JICOFO_AUTH_PASSWORD" \
  JVB_AUTH_PASSWORD="$DEV_VAULT_JVB_AUTH_PASSWORD" >/dev/null

vault kv put kv/realtime/jitsi/jicofo \
  JICOFO_AUTH_PASSWORD="$DEV_VAULT_JICOFO_AUTH_PASSWORD" \
  JICOFO_COMPONENT_SECRET="$DEV_VAULT_JICOFO_COMPONENT_SECRET" >/dev/null

vault kv put kv/realtime/jitsi/jvb \
  JVB_AUTH_PASSWORD="$DEV_VAULT_JVB_AUTH_PASSWORD" >/dev/null

vault write database/static-creds/backend-app \
  username="$DEV_VAULT_POSTGRES_USERNAME" \
  password="$DEV_VAULT_POSTGRES_PASSWORD" >/dev/null

ROLE_ID="$(vault read -field=role_id auth/approle-workloads/role/backend-service/role-id)"
WRAP_JSON="$(vault write -wrap-ttl=5m -format=json -f auth/approle-workloads/role/backend-service/secret-id)"
WRAPPED_SECRET_ID="$(printf '%s\n' "$WRAP_JSON" | awk -F'"' '/^[[:space:]]*"token":/ {print $4; exit}')"

if [ -z "$WRAPPED_SECRET_ID" ]; then
  echo "Failed to obtain a wrapped secret_id for backend-service." >&2
  exit 1
fi

umask 077
printf '%s\n' "$ROLE_ID" > "$ROLE_ID_FILE"
printf '%s\n' "$WRAPPED_SECRET_ID" > "$WRAPPED_SECRET_ID_FILE"

write_kv_env "$RUNTIME_DIR/postgres.env" \
  POSTGRES_PASSWORD "$(read_db_field database/static-creds/backend-app password)"

write_kv_env "$RUNTIME_DIR/redis.env" \
  REDIS_PASSWORD "$(read_kv_field kv/app/redis/runtime REDIS_PASSWORD)"

write_kv_env "$RUNTIME_DIR/keycloak.env" \
  KEYCLOAK_ADMIN_PASSWORD "$(read_kv_field kv/identity/keycloak/runtime KEYCLOAK_ADMIN_PASSWORD)" \
  SSO_CLIENT_SECRET "$(read_kv_field kv/identity/keycloak/runtime SSO_CLIENT_SECRET)"

write_kv_env "$RUNTIME_DIR/jitsi-web.env" \
  JWT_APP_SECRET "$(read_kv_field kv/realtime/jitsi/web JWT_APP_SECRET)" \
  JICOFO_AUTH_PASSWORD "$(read_kv_field kv/realtime/jitsi/web JICOFO_AUTH_PASSWORD)" \
  JVB_AUTH_PASSWORD "$(read_kv_field kv/realtime/jitsi/web JVB_AUTH_PASSWORD)"

write_kv_env "$RUNTIME_DIR/jitsi-prosody.env" \
  JWT_APP_SECRET "$(read_kv_field kv/realtime/jitsi/prosody JWT_APP_SECRET)" \
  JICOFO_AUTH_PASSWORD "$(read_kv_field kv/realtime/jitsi/prosody JICOFO_AUTH_PASSWORD)" \
  JVB_AUTH_PASSWORD "$(read_kv_field kv/realtime/jitsi/prosody JVB_AUTH_PASSWORD)"

write_kv_env "$RUNTIME_DIR/jitsi-jicofo.env" \
  JICOFO_AUTH_PASSWORD "$(read_kv_field kv/realtime/jitsi/jicofo JICOFO_AUTH_PASSWORD)" \
  JICOFO_COMPONENT_SECRET "$(read_kv_field kv/realtime/jitsi/jicofo JICOFO_COMPONENT_SECRET)"

write_kv_env "$RUNTIME_DIR/jitsi-jvb.env" \
  JVB_AUTH_PASSWORD "$(read_kv_field kv/realtime/jitsi/jvb JVB_AUTH_PASSWORD)"

echo "dev Vault bootstrap completed"
