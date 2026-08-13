#!/bin/sh
set -eu

# Verify the private production service plane without publishing the edge or
# starting Jitsi media services. This script never reads or prints secrets.

PROJECT_NAME="${COMPOSE_PROJECT_NAME:-jitsi-prod}"
EXPECTED_OIDC_ISSUER="${EXPECTED_OIDC_ISSUER:-https://auth.jitsi-mgorka.top/realms/jitsi}"

container_id() {
  docker ps \
    --filter "label=com.docker.compose.project=$PROJECT_NAME" \
    --filter "label=com.docker.compose.service=$1" \
    --format '{{.ID}}' \
    | head -n 1
}

require_healthy() {
  service="$1"
  container="$(container_id "$service")"
  test -n "$container" || {
    echo "$service is not running." >&2
    exit 1
  }
  status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container")"
  test "$status" = "healthy" || {
    echo "$service is not healthy: $status" >&2
    exit 1
  }
}

for service in db redis vault keycloak-db keycloak osv-egress-proxy backend frontend; do
  require_healthy "$service"
done

backend="$(container_id backend)"
frontend="$(container_id frontend)"

docker exec "$backend" sh -ec \
  'curl --noproxy "*" -fsS http://127.0.0.1:8080/actuator/health | grep -q '"'"'"status":"UP"'"'"''
docker exec "$frontend" sh -ec \
  'wget -q -O /dev/null http://127.0.0.1:3000/healthz'

actual_issuer="$({
  docker exec "$backend" sh -ec \
    'curl --noproxy "*" -fsS http://keycloak:8080/realms/jitsi/.well-known/openid-configuration'
} | sed -n 's/.*"issuer":"\([^"]*\)".*/\1/p')"
test "$actual_issuer" = "$EXPECTED_OIDC_ISSUER" || {
  echo "Unexpected OIDC issuer: $actual_issuer" >&2
  exit 1
}

docker exec "$backend" sh -ec '
  printf "%s" '"'"'{"version":"5.2.1","package":{"name":"express","ecosystem":"npm"}}'"'"' >/tmp/osv-query.json
  curl --noproxy "" --proxy http://osv-egress-proxy:3128 \
    -fsS \
    -H "Content-Type: application/json" \
    --data-binary @/tmp/osv-query.json \
    https://api.osv.dev/v1/query >/tmp/osv-response.json
  grep -Eq '"'"'"vulns"|^\{\}$'"'"' /tmp/osv-response.json
  rm -f /tmp/osv-query.json /tmp/osv-response.json
'

published="$(
  docker ps \
    --filter "label=com.docker.compose.project=$PROJECT_NAME" \
    --format '{{.Ports}}' \
    | grep -E '(^|, )[0-9.\[\]:]+->[0-9]+/(tcp|udp)' \
    || true
)"
test -z "$published" || {
  echo "The private production plane unexpectedly publishes host ports:" >&2
  printf '%s\n' "$published" >&2
  exit 1
}

echo "Production private plane health, OIDC, controlled OSV egress, and closed host perimeter are verified."
