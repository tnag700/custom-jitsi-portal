#!/bin/sh
set -eu
# Disposable PostgreSQL 18 acceptance test. Never uses a deployment env/volume.
ROOT="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
NAME="jitsi-db-role-test-$$"
cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT HUP INT TERM
docker run --detach --rm --name "$NAME" --network none \
  --tmpfs /var/lib/postgresql \
  -e POSTGRES_USER=test_admin -e POSTGRES_DB=role_test \
  -e POSTGRES_PASSWORD=test-bootstrap-password \
  -e APP_DB_USER=test_app -e APP_DB_PASSWORD=test-runtime-password \
  --mount "type=bind,src=$ROOT/deploy/postgres/init-runtime-role.sh,dst=/docker-entrypoint-initdb.d/10-runtime-role.sh,readonly" \
  postgres:18.6@sha256:5a5a84b19854a9ffaa54082c166ff4ec27473a361e496e5ea167f298f2da9722 >/dev/null
attempt=0
until docker exec "$NAME" psql -X -U test_admin -d role_test -Atc "SELECT 1 FROM pg_roles WHERE rolname = 'test_app'" 2>/dev/null | grep -qx 1; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 30 ]; then
    docker logs "$NAME" >&2
    exit 1
  fi
  sleep 1
done
runtime_sql() {
  docker exec -e PGPASSWORD=test-runtime-password "$NAME" \
    psql -X -h 127.0.0.1 -U test_app -d role_test -v ON_ERROR_STOP=1 -Atc "$1"
}
runtime_sql "SELECT NOT (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls) AND NOT EXISTS (SELECT 1 FROM pg_auth_members WHERE member = r.oid) FROM pg_roles r WHERE rolname = current_user" | grep -qx t
runtime_sql "CREATE TABLE role_check(id BIGSERIAL PRIMARY KEY, value TEXT); INSERT INTO role_check(value) VALUES ('ok'); ALTER TABLE role_check ADD COLUMN migrated BOOLEAN; SELECT value FROM role_check" | grep -qx ok
for statement in "CREATE ROLE forbidden_role" "CREATE DATABASE forbidden_db" "SELECT pg_read_file('/etc/passwd')"; do
  if runtime_sql "$statement" >/dev/null 2>&1; then
    echo "Runtime role unexpectedly allowed: $statement" >&2
    exit 1
  fi
done
# Exercise the existing-volume migration and prove DDL still works on objects
# created by the old bootstrap account, including their owned sequences.
docker exec "$NAME" psql -X -U test_admin -d postgres -v ON_ERROR_STOP=1 -c 'CREATE DATABASE legacy_test' >/dev/null
docker exec "$NAME" psql -X -U test_admin -d legacy_test -v ON_ERROR_STOP=1 -c 'CREATE TABLE legacy(id BIGSERIAL PRIMARY KEY, value TEXT)' >/dev/null
docker exec -i "$NAME" psql -X -U test_admin -d legacy_test -v app_db=legacy_test -v app_user=legacy_app < "$ROOT/scripts/migrate-database-runtime-role.sql" >/dev/null
docker exec "$NAME" psql -X -U legacy_app -d legacy_test -v ON_ERROR_STOP=1 -c "ALTER TABLE legacy ADD COLUMN migrated BOOLEAN; INSERT INTO legacy(value) VALUES ('ok')" >/dev/null
if docker exec -i "$NAME" psql -X -U test_admin -d legacy_test -v app_db=wrong_database -v app_user=unexpected_app < "$ROOT/scripts/migrate-database-runtime-role.sql" >/dev/null 2>&1; then
  echo 'Wrong-target migration unexpectedly succeeded' >&2
  exit 1
fi
# psql does not support `quit <exit-code>`; every guarded failure must fail
# the caller too. Execute the failure bodies against this disposable cluster.
for script in migrate-production-active-config-set verify-production-active-config-set migrate-keycloak-post-logout-policy verify-keycloak-post-logout-policy; do
  if { printf '\\set ON_ERROR_STOP on\n'; awk '/^\\else$/ { branch=1; next } /^\\endif$/ { branch=0 } branch' "$ROOT/scripts/$script.sql"; } |
    docker exec -i "$NAME" psql -X -U test_admin -d role_test >/dev/null 2>&1; then
    echo "Operator failure did not propagate: $script" >&2
    exit 1
  fi
done
echo 'PostgreSQL 18 fresh runtime role and existing-volume migration: PASS'
