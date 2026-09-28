#!/bin/sh
# Non-executable init scripts are sourced by the official entrypoint. Keep
# strict shell options local to this script in either invocation mode.
(
set -eu

# The official entrypoint runs this only for a fresh PGDATA. Existing volumes
# require the explicit migration described in docs/database-runtime-roles.md.
: "${POSTGRES_USER:?bootstrap database user is required}"
: "${POSTGRES_DB:?application database is required}"
: "${APP_DB_USER:?runtime database user is required}"
: "${APP_DB_PASSWORD:?runtime database password is required}"
test "$APP_DB_USER" != "$POSTGRES_USER"
test "$APP_DB_PASSWORD" != "$POSTGRES_PASSWORD"

psql -X --set=ON_ERROR_STOP=1 --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" \
  --set=app_user="$APP_DB_USER" --set=app_db="$POSTGRES_DB" <<'SQL'
\getenv app_password APP_DB_PASSWORD
BEGIN;
CREATE ROLE :"app_user" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD :'app_password';
REVOKE ALL ON DATABASE :"app_db" FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE :"app_db" TO :"app_user";
REVOKE ALL ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO :"app_user";
COMMIT;
SQL
)
