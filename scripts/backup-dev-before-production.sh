#!/bin/sh
set -eu

# Capture the mutable development state before the production cutover. The
# script never removes containers or volumes and always restarts Keycloak when
# it was running on entry.

umask 077

BACKUP_ROOT="${1:-/var/backups/jitsi-preproduction}"
DEV_KEYCLOAK_CONTAINER="${DEV_KEYCLOAK_CONTAINER:-jitsi-dev-codex-keycloak-1}"
DEV_DB_CONTAINER="${DEV_DB_CONTAINER:-jitsi-dev-codex-db-1}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
BACKUP_DIR="$BACKUP_ROOT/$STAMP"
KEYCLOAK_EXPORT_DIR="$BACKUP_DIR/keycloak-export"
KEYCLOAK_DATA_DIR="$BACKUP_DIR/keycloak-data"
BACKUP_IMAGE="jitsi-keycloak-preproduction-backup:$STAMP"
KEYCLOAK_WAS_RUNNING=false

restart_keycloak() {
  if [ "$KEYCLOAK_WAS_RUNNING" = true ]; then
    docker start "$DEV_KEYCLOAK_CONTAINER" >/dev/null 2>&1 || true
  fi
}

cleanup() {
  restart_keycloak
  docker image rm "$BACKUP_IMAGE" >/dev/null 2>&1 || true
}

trap cleanup EXIT INT TERM

docker inspect "$DEV_KEYCLOAK_CONTAINER" >/dev/null
docker inspect "$DEV_DB_CONTAINER" >/dev/null

mkdir -p "$KEYCLOAK_EXPORT_DIR" "$KEYCLOAK_DATA_DIR"

docker exec "$DEV_DB_CONTAINER" sh -ec \
  'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump --format=custom --no-owner --no-acl --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"' \
  > "$BACKUP_DIR/application.pgdump"
test -s "$BACKUP_DIR/application.pgdump"
docker exec -i "$DEV_DB_CONTAINER" pg_restore --list < "$BACKUP_DIR/application.pgdump" >/dev/null

if [ "$(docker inspect --format '{{.State.Running}}' "$DEV_KEYCLOAK_CONTAINER")" = true ]; then
  KEYCLOAK_WAS_RUNNING=true
  docker stop --time 60 "$DEV_KEYCLOAK_CONTAINER" >/dev/null
fi

docker commit --pause=false "$DEV_KEYCLOAK_CONTAINER" "$BACKUP_IMAGE" >/dev/null
docker cp "$DEV_KEYCLOAK_CONTAINER:/opt/keycloak/data/." "$KEYCLOAK_DATA_DIR"

# The official image runs as uid 1000. Give only that uid access to the private
# export directory, then return ownership to the invoking operator afterwards.
docker run --rm --user 0 \
  --volume "$KEYCLOAK_EXPORT_DIR:/export" \
  --entrypoint /bin/sh "$BACKUP_IMAGE" \
  -ec 'chown 1000:0 /export && chmod 0700 /export'

docker run --rm --network none \
  --volume "$KEYCLOAK_EXPORT_DIR:/export" \
  "$BACKUP_IMAGE" export --dir /export --realm jitsi-dev --users realm_file >/dev/null

docker run --rm --user 0 \
  --volume "$KEYCLOAK_EXPORT_DIR:/export" \
  --entrypoint /bin/sh "$BACKUP_IMAGE" \
  -ec 'chown -R '"$(id -u):$(id -g)"' /export && chmod -R go-rwx /export'

if ! find "$KEYCLOAK_EXPORT_DIR" -type f -name '*.json' -size +0c | grep -q .; then
  echo "Keycloak export did not produce a non-empty JSON file." >&2
  exit 1
fi

if ! grep -R -q '"realm"[[:space:]]*:[[:space:]]*"jitsi-dev"' "$KEYCLOAK_EXPORT_DIR"; then
  echo "Keycloak export does not identify the expected jitsi-dev realm." >&2
  exit 1
fi

restart_keycloak
KEYCLOAK_WAS_RUNNING=false

{
  printf 'created_utc=%s\n' "$STAMP"
  printf 'source_keycloak_container=%s\n' "$DEV_KEYCLOAK_CONTAINER"
  printf 'source_keycloak_image=%s\n' "$(docker inspect --format '{{.Config.Image}}' "$DEV_KEYCLOAK_CONTAINER")"
  printf 'source_db_container=%s\n' "$DEV_DB_CONTAINER"
  printf 'source_db_image=%s\n' "$(docker inspect --format '{{.Config.Image}}' "$DEV_DB_CONTAINER")"
} > "$BACKUP_DIR/MANIFEST.txt"

(
  cd "$BACKUP_DIR"
  find . -type f ! -name SHA256SUMS -print0 \
    | sort -z \
    | xargs -0 sha256sum > SHA256SUMS
  sha256sum --check SHA256SUMS >/dev/null
)

printf 'Pre-production backup verified: %s\n' "$BACKUP_DIR"
