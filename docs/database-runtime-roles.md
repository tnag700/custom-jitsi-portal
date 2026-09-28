# Database runtime roles and manual rotation

Fresh production installs create two separate PostgreSQL clusters. In each,
`POSTGRES_USER` / `KEYCLOAK_POSTGRES_USER` is the bootstrap administrator
(`jitsi_admin` / `keycloak_admin` by default). Consumers use `jitsi_app` and
`keycloak_app`. Runtime roles have LOGIN, CONNECT, TEMPORARY and ownership of
their own `public` schema; NOSUPERUSER, NOCREATEDB, NOCREATEROLE, NOREPLICATION,
NOBYPASSRLS and no role memberships. They do not own the database. Schema DDL is
intentional: backend Flyway and Keycloak Liquibase migrate at startup. This
does not provide isolation between tables owned by the same application role.

`scripts/prepare-production-operator-files.sh` generates distinct administrator
and runtime passwords. Private `postgres.env` / `keycloak-postgres.env` contain
`POSTGRES_PASSWORD` (bootstrap) and `APP_DB_PASSWORD` (runtime). Backend Vault
KV and Keycloak `KC_DB_PASSWORD` receive only runtime passwords. Administrators
stay in the private database operator files, never in a consumer bridge.
Fresh volumes run `deploy/postgres/init-runtime-role.sh`. An existing volume
does **not** rerun it, and changing environment variables does not change SQL
roles or passwords. Do not delete a volume to apply this change.

Run `npm run test:database-roles` on a host with Docker and a POSIX shell. It
uses a disposable PostgreSQL 18.4 container with tmpfs storage, no network or
published port, and tests fresh init, runtime DDL, elevated-operation denial
and existing-volume ownership migration. It never loads production files.

## Existing installation migration

Perform in a maintenance window, after verified database backups and a tested
restore. Keep the old images/config and private handoff until acceptance.

1. Stop backend and Keycloak consumers. Record their actual database, role,
   schema owners and `pg_roles` flags through the private operator path. Keep
   existing `POSTGRES_USER` / `KEYCLOAK_POSTGRES_USER` and their bootstrap
   passwords unchanged; renaming these environment variables cannot rename an
   existing SQL user. The commands below are examples for default databases.
2. On each cluster connect as its existing superuser. Run
   `scripts/migrate-database-runtime-role.sql` with `app_db` set to the current
   database and `app_user` to a **new** runtime role:

   ```sh
   psql -X -U "$EXISTING_ADMIN" -d jitsi -v app_db=jitsi -v app_user=jitsi_app -f scripts/migrate-database-runtime-role.sql
   # Separately, against the Keycloak cluster:
   psql -X -U "$EXISTING_KC_ADMIN" -d keycloak -v app_db=keycloak -v app_user=keycloak_app -f scripts/migrate-database-runtime-role.sql
   ```

   Use a private `.pgpass` or interactive authentication; never pass passwords
   on the command line. The transaction transfers only public schema tables,
   sequences and views; it does not use `REASSIGN OWNED` on a cluster superuser.
   Stop if custom functions, types, foreign tables, additional schemas or
   extension-owned objects exist: review their ownership separately before
   applying. The script refuses an existing runtime role and a wrong database.
3. Set each new password with interactive psql `\password jitsi_app` /
   `\password keycloak_app` (no SQL/history plaintext). Use separate random
   values of at least 32 bytes. Set `SPRING_DATASOURCE_USERNAME=jitsi_app` and
   `KC_DB_USERNAME=keycloak_app` in private `.env.production`; keep bootstrap
   usernames unchanged. Update private `APP_DB_PASSWORD` entries; update
   Keycloak `KC_DB_PASSWORD` and its Vault copy. Set Vault's static records with
   the runtime names and passwords. Use private files for CLI secret inputs,
   e.g. `vault write database/static-creds/backend-app username=jitsi_app password=@/private/password-file`.
4. Issue a fresh wrapped AppRole handoff with
   `scripts/reissue-production-backend-approle.sh`. While backend is stopped,
   remove only `/vault/runtime/backend/runtime.env` in its named runtime volume
   through the private operator path, then rerun `backend-vault-bootstrap`.
   Ordinary restarts deliberately reuse this file; stale usernames/contracts
   now fail closed at backend startup. Do not delete database or Vault volumes.
5. Run `npm run prod:preflight:offline`, start consumers, and check migrations,
   health, login, create/update/read and Keycloak admin logout of a portal
   session. Query `current_user`, `pg_roles` and `pg_auth_members` through **each
   runtime connection**; confirm five elevated flags are false and memberships
   are absent. Verify runtime schema DDL works and CREATE ROLE, CREATE DATABASE
   and `pg_read_file` fail. Successful health alone is insufficient evidence.
6. Old bootstrap credentials are now operator-only; rotate them separately
   after acceptance and prove the former consumer credential cannot reconnect.
   Rollback in the window may restore the old consumer configuration while
   keeping the new objects: the old bootstrap superuser can still access them.
   After bootstrap password rotation, restore consumer runtime credentials,
   not an old superuser bridge. Record migration/verification without secrets.

## Static KV contract and password rotation

`database/` is a legacy-named **KV v1 mount**, not the database secrets engine.
`database/static-creds/*` contains ordinary username/password values. There
are no credential leases, automatic password rotation, renewals or automatic
session revocations. The contract is `manual-static-kv-controlled-restart`.
Do not replace that mount with another engine in place. Any future lease-based
consumer needs one credential issuance and a complete lease lifecycle first.

For routine rotation: stop the affected consumers and startup-fetch jobs;
privately change the runtime password using psql `\password`; update the same
pair in Vault and the matching private service files; refresh the backend
bridge as in steps 3–4; recreate Keycloak when its env file changes; then run
step 5 and verify the old password fails. Do not run full Vault bootstrap to
rotate one secret: its seed writes all secrets. Update or retire the offline
seed under custody so a future bootstrap cannot overwrite current values.
If cutover fails, keep consumers stopped and restore a consistent pair in
PostgreSQL, KV and files before retrying. Password change alone does not kill
existing sessions; stopping consumers, or explicitly terminating their old
sessions after verifying identity, is part of the maintenance window.

The old `controlled-restart-static-role` bridge must be regenerated once on
upgrade. Thereafter an unchanged bridge remains valid across ordinary restarts.
