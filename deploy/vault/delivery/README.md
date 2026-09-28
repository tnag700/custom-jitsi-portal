# Vault secret delivery model

Этот каталог фиксирует Story 19.3 как реальный delivery layer поверх уже существующего Vault secret plane и scoped auth baseline.

## Scope

- `.env.production.example` больше не хранит expected production secret values.
- Production secrets приходят либо через backend startup fetch, либо через service-specific private operator-rendered env files.
- Глобальный `.env.production` больше не считается каноническим каналом доставки секретов.

## Canonical delivery surfaces

- Backend: `backend-vault-bootstrap` логинится через AppRole, читает `kv/app/backend/runtime`, `kv/identity/backend/oidc`, `kv/app/redis/runtime` и `database/static-creds/backend-app` (KV v1), затем пишет permission-bound runtime env bridge в named volume `backend-vault-runtime`.
- PostgreSQL: separate bootstrap `POSTGRES_PASSWORD` and runtime `APP_DB_PASSWORD` in a service-specific env file, рендеримый из Vault path/operator handoff и монтируемый через `POSTGRES_VAULT_ENV_FILE_PATH`.
- Redis: service-specific env file через `REDIS_VAULT_ENV_FILE_PATH`.
- Keycloak: service-specific env file через `KEYCLOAK_VAULT_ENV_FILE_PATH`.
- Jitsi Web/Prosody/Jicofo/JVB: отдельные env files через `JITSI_*_VAULT_ENV_FILE_PATH`.

## Vault target matrix

- `kv/app/backend/runtime`: `APP_MEETINGS_TOKEN_SIGNING_SECRET`, `APP_CONFIG_SETS_ENCRYPTION_KEY`
- `kv/identity/backend/oidc`: `SSO_CLIENT_SECRET`
- `kv/app/redis/runtime`: `REDIS_PASSWORD`
- `database/static-creds/backend-app` (KV v1): backend DB username/password
- `database/static-creds/keycloak-app` (KV v1): Keycloak DB credentials
- `database/static-creds/backup-job`: reserved KV v1 backup contour; provisioning remains operator-owned
- `kv/identity/keycloak/runtime`: `KC_BOOTSTRAP_ADMIN_PASSWORD`, `KC_DB_PASSWORD`, `SSO_CLIENT_SECRET`
- `kv/realtime/jitsi/web`: `JWT_APP_SECRET`, `JICOFO_AUTH_PASSWORD`, `JVB_AUTH_PASSWORD`
- `kv/realtime/jitsi/prosody`: `JWT_APP_SECRET`, `JICOFO_AUTH_PASSWORD`, `JVB_AUTH_PASSWORD`
- `kv/realtime/jitsi/jicofo`: `JICOFO_AUTH_PASSWORD`, `JICOFO_COMPONENT_SECRET`
- `kv/realtime/jitsi/jvb`: `JVB_AUTH_PASSWORD`
- `kv/backup/runner/runtime`: backup destination credentials and snapshot material
- `kv/backup/backend/runtime`: backend-facing backup integration material

## Runtime contracts

- Backend DB credentials: static KV v1, contract `manual-static-kv-controlled-restart`. No automatic rotation or dynamic leases. Applying rotation requires controlled restart/redeploy; follow [database-runtime-roles.md](../../../docs/database-runtime-roles.md). The rendered backend bridge stays in the bounded runtime volume until the next bootstrap refresh, so ordinary backend restarts remain restart-safe.
- Redis password: controlled restart of Redis and reconnect of backend consumers.
- Keycloak admin/bootstrap secret: controlled restart/redeploy after new rendered file is in place.
- Jitsi runtime secrets: controlled restart/redeploy of the affected Jitsi service after its service-specific rendered file changes.
- Rendered env files and wrapped values остаются вне git; committed `examples/*.env.example` существуют только для `docker compose config` и не являются production source of truth.

## Sensitive artifact lifecycle

- response-wrapped secret_id хранится только в private handoff surface и должен удаляться immediately after handoff/unwrapping; он не должен попадать в ProblemDetail, startup diagnostics, crash dumps или pasted troubleshooting snippets.
- token sink существует только как краткоживущий transport artifact. Для backend path он удаляется immediately after handoff, а для downstream one-shot consumers допускается private retention только до завершения handoff contract.
- Backend runtime bridge остаётся в bounded runtime volume до next controlled restart/redeploy or next bootstrap refresh; файл должен оставаться с permission level 0600 и ownership, соответствующим runtime consumer.
- Service-specific rendered env files для PostgreSQL, Redis, Keycloak и Jitsi могут жить только до next controlled restart/redeploy и не должны копироваться в repo-kept notes, shared local override areas или generic troubleshooting bundles.
- Leases и lease dumps считаются sensitive operational evidence. Они не публикуются в обычных логах, ProblemDetail payloads, startup diagnostics или operator notes; допускаются только masked audit/evidence references.
