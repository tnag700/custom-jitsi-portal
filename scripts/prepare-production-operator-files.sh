#!/bin/sh
set -eu

# Generate one coherent, private handoff set for the single-host production
# deployment. Existing material is never overwritten.

umask 077

REPOSITORY_DIR="${1:-$(pwd)}"
OPERATOR_ROOT="${2:-$REPOSITORY_DIR/deploy/production/local}"
ENV_FILE="$REPOSITORY_DIR/.env.production"
ENV_TEMPLATE="$REPOSITORY_DIR/.env.production.example"
SECRETS_DIR="$OPERATOR_ROOT/secrets"
VAULT_DIR="$OPERATOR_ROOT/vault"
VAULT_TLS_DIR="$VAULT_DIR/tls"
VAULT_RECOVERY_DIR="$VAULT_DIR/recovery"
BACKEND_HANDOFF_DIR="$VAULT_DIR/backend"
VAULT_SEED_FILE="$VAULT_DIR/seed.env"
VAULT_CUSTODY_DIR="$OPERATOR_ROOT/custody"
VAULT_CA_DIR="$VAULT_CUSTODY_DIR/vault-ca"
KEYCLOAK_REALM_IMPORT_DIR="$OPERATOR_ROOT/keycloak/realm-import"

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command is unavailable: $1" >&2
    exit 1
  }
}

replace_env() {
  key="$1"
  value="$2"
  temporary="$(mktemp)"
  awk -v key="$key" -v value="$value" '
    BEGIN { replaced = 0 }
    index($0, key "=") == 1 { print key "=" value; replaced = 1; next }
    { print }
    END { if (!replaced) print key "=" value }
  ' "$ENV_FILE" > "$temporary"
  mv "$temporary" "$ENV_FILE"
  chmod 0600 "$ENV_FILE"
}

write_env_file() {
  destination="$1"
  shift
  : > "$destination"
  while [ "$#" -gt 0 ]; do
    printf '%s=%s\n' "$1" "$2" >> "$destination"
    shift 2
  done
  chmod 0600 "$destination"
}

require_command openssl
require_command awk
require_command mktemp

test -f "$ENV_TEMPLATE" || {
  echo "Production env template is missing." >&2
  exit 1
}

if [ -e "$ENV_FILE" ] || [ -e "$VAULT_SEED_FILE" ] || [ -e "$VAULT_TLS_DIR/server.key" ] || [ -e "$VAULT_CA_DIR/ca.key" ]; then
  echo "Production operator material already exists; refusing to overwrite it." >&2
  exit 1
fi

mkdir -p "$SECRETS_DIR" "$VAULT_TLS_DIR" "$VAULT_RECOVERY_DIR" "$BACKEND_HANDOFF_DIR" "$VAULT_CA_DIR" "$KEYCLOAK_REALM_IMPORT_DIR"
chmod 0700 "$OPERATOR_ROOT" "$SECRETS_DIR" "$VAULT_DIR" "$VAULT_TLS_DIR" "$VAULT_RECOVERY_DIR" "$BACKEND_HANDOFF_DIR" "$VAULT_CUSTODY_DIR" "$VAULT_CA_DIR" "$KEYCLOAK_REALM_IMPORT_DIR"

APP_POSTGRES_PASSWORD="$(openssl rand -hex 32)"
REDIS_PASSWORD="$(openssl rand -hex 32)"
KEYCLOAK_DB_PASSWORD="$(openssl rand -hex 32)"
KEYCLOAK_ADMIN_PASSWORD="$(openssl rand -hex 32)"
SSO_CLIENT_SECRET="$(openssl rand -hex 32)"
APP_MEETINGS_TOKEN_SIGNING_SECRET="$(openssl rand -hex 32)"
# Canonical Base64 avoids the application's intentional Base64-first parser
# treating a hex-looking key differently than an operator expects.
APP_CONFIG_SETS_ENCRYPTION_KEY="$(openssl rand -base64 32 | tr -d '\r\n')"
JICOFO_AUTH_PASSWORD="$(openssl rand -hex 32)"
JVB_AUTH_PASSWORD="$(openssl rand -hex 32)"
JICOFO_COMPONENT_SECRET="$(openssl rand -hex 32)"
GRAFANA_ADMIN_PASSWORD="$(openssl rand -hex 32)"

write_env_file "$SECRETS_DIR/postgres.env" \
  POSTGRES_PASSWORD "$APP_POSTGRES_PASSWORD"
write_env_file "$SECRETS_DIR/redis.env" \
  REDIS_PASSWORD "$REDIS_PASSWORD"
write_env_file "$SECRETS_DIR/keycloak-postgres.env" \
  POSTGRES_PASSWORD "$KEYCLOAK_DB_PASSWORD"
write_env_file "$SECRETS_DIR/keycloak.env" \
  KC_BOOTSTRAP_ADMIN_PASSWORD "$KEYCLOAK_ADMIN_PASSWORD" \
  KC_DB_PASSWORD "$KEYCLOAK_DB_PASSWORD" \
  SSO_CLIENT_SECRET "$SSO_CLIENT_SECRET"
write_env_file "$SECRETS_DIR/jitsi-web.env" \
  JWT_APP_SECRET "$APP_MEETINGS_TOKEN_SIGNING_SECRET" \
  JICOFO_AUTH_PASSWORD "$JICOFO_AUTH_PASSWORD" \
  JVB_AUTH_PASSWORD "$JVB_AUTH_PASSWORD"
write_env_file "$SECRETS_DIR/jitsi-prosody.env" \
  JWT_APP_SECRET "$APP_MEETINGS_TOKEN_SIGNING_SECRET" \
  JICOFO_AUTH_PASSWORD "$JICOFO_AUTH_PASSWORD" \
  JVB_AUTH_PASSWORD "$JVB_AUTH_PASSWORD"
write_env_file "$SECRETS_DIR/jitsi-jicofo.env" \
  JICOFO_AUTH_PASSWORD "$JICOFO_AUTH_PASSWORD" \
  JICOFO_COMPONENT_SECRET "$JICOFO_COMPONENT_SECRET"
write_env_file "$SECRETS_DIR/jitsi-jvb.env" \
  JVB_AUTH_PASSWORD "$JVB_AUTH_PASSWORD"
write_env_file "$SECRETS_DIR/grafana.env" \
  GF_SECURITY_ADMIN_PASSWORD "$GRAFANA_ADMIN_PASSWORD"

write_env_file "$VAULT_SEED_FILE" \
  APP_POSTGRES_PASSWORD "$APP_POSTGRES_PASSWORD" \
  REDIS_PASSWORD "$REDIS_PASSWORD" \
  KEYCLOAK_DB_PASSWORD "$KEYCLOAK_DB_PASSWORD" \
  KEYCLOAK_ADMIN_PASSWORD "$KEYCLOAK_ADMIN_PASSWORD" \
  SSO_CLIENT_SECRET "$SSO_CLIENT_SECRET" \
  APP_MEETINGS_TOKEN_SIGNING_SECRET "$APP_MEETINGS_TOKEN_SIGNING_SECRET" \
  APP_CONFIG_SETS_ENCRYPTION_KEY "$APP_CONFIG_SETS_ENCRYPTION_KEY" \
  JICOFO_AUTH_PASSWORD "$JICOFO_AUTH_PASSWORD" \
  JVB_AUTH_PASSWORD "$JVB_AUTH_PASSWORD" \
  JICOFO_COMPONENT_SECRET "$JICOFO_COMPONENT_SECRET" \
  GRAFANA_ADMIN_PASSWORD "$GRAFANA_ADMIN_PASSWORD"

openssl genrsa -out "$VAULT_CA_DIR/ca.key" 4096 >/dev/null 2>&1
openssl req -x509 -new -sha256 -days 3650 \
  -key "$VAULT_CA_DIR/ca.key" \
  -subj "/CN=Jitsi Production Vault CA" \
  -out "$VAULT_CA_DIR/ca.crt"
install -m 0644 "$VAULT_CA_DIR/ca.crt" "$VAULT_TLS_DIR/ca.crt"
openssl genrsa -out "$VAULT_TLS_DIR/server.key" 4096 >/dev/null 2>&1
openssl req -new -sha256 \
  -key "$VAULT_TLS_DIR/server.key" \
  -subj "/CN=vault" \
  -out "$VAULT_TLS_DIR/server.csr"
printf 'subjectAltName=DNS:vault\nextendedKeyUsage=serverAuth\n' > "$VAULT_TLS_DIR/server.ext"
openssl x509 -req -sha256 -days 825 \
  -in "$VAULT_TLS_DIR/server.csr" \
  -CA "$VAULT_CA_DIR/ca.crt" \
  -CAkey "$VAULT_CA_DIR/ca.key" \
  -CAcreateserial \
  -extfile "$VAULT_TLS_DIR/server.ext" \
  -out "$VAULT_TLS_DIR/server.crt" >/dev/null 2>&1
rm -f "$VAULT_TLS_DIR/server.csr" "$VAULT_TLS_DIR/server.ext"
chmod 0600 "$VAULT_CA_DIR/ca.key" "$VAULT_CA_DIR/ca.srl" "$VAULT_TLS_DIR/server.key"
chmod 0644 "$VAULT_CA_DIR/ca.crt"
chmod 0644 "$VAULT_TLS_DIR/ca.crt" "$VAULT_TLS_DIR/server.crt"

cp "$ENV_TEMPLATE" "$ENV_FILE"
chmod 0600 "$ENV_FILE"
replace_env VAULT_TLS_DIR "$VAULT_TLS_DIR"
replace_env VAULT_RECOVERY_DIR "$VAULT_RECOVERY_DIR"
replace_env VAULT_SEED_ENV_FILE_PATH "$VAULT_SEED_FILE"
replace_env BACKEND_VAULT_HANDOFF_DIR "$BACKEND_HANDOFF_DIR"
replace_env BACKEND_VAULT_ROLE_ID_PATH "$BACKEND_HANDOFF_DIR/role_id"
replace_env BACKEND_VAULT_WRAPPED_SECRET_ID_PATH "$BACKEND_HANDOFF_DIR/wrapped-secret-id"
replace_env POSTGRES_VAULT_ENV_FILE_PATH "$SECRETS_DIR/postgres.env"
replace_env REDIS_VAULT_ENV_FILE_PATH "$SECRETS_DIR/redis.env"
replace_env KEYCLOAK_POSTGRES_VAULT_ENV_FILE_PATH "$SECRETS_DIR/keycloak-postgres.env"
replace_env KEYCLOAK_VAULT_ENV_FILE_PATH "$SECRETS_DIR/keycloak.env"
replace_env JITSI_WEB_VAULT_ENV_FILE_PATH "$SECRETS_DIR/jitsi-web.env"
replace_env JITSI_PROSODY_VAULT_ENV_FILE_PATH "$SECRETS_DIR/jitsi-prosody.env"
replace_env JITSI_JICOFO_VAULT_ENV_FILE_PATH "$SECRETS_DIR/jitsi-jicofo.env"
replace_env JITSI_JVB_VAULT_ENV_FILE_PATH "$SECRETS_DIR/jitsi-jvb.env"
replace_env GRAFANA_VAULT_ENV_FILE_PATH "$SECRETS_DIR/grafana.env"
replace_env KEYCLOAK_REALM_IMPORT_DIR "$KEYCLOAK_REALM_IMPORT_DIR"

unset APP_POSTGRES_PASSWORD REDIS_PASSWORD KEYCLOAK_DB_PASSWORD KEYCLOAK_ADMIN_PASSWORD
unset SSO_CLIENT_SECRET APP_MEETINGS_TOKEN_SIGNING_SECRET APP_CONFIG_SETS_ENCRYPTION_KEY
unset JICOFO_AUTH_PASSWORD JVB_AUTH_PASSWORD JICOFO_COMPONENT_SECRET
unset GRAFANA_ADMIN_PASSWORD

printf 'Production operator files prepared under %s.\n' "$OPERATOR_ROOT"
printf 'Vault CA signing material is isolated under %s; move it to approved offline custody.\n' "$VAULT_CA_DIR"
printf 'Public TLS certificates are intentionally not generated by this script.\n'
