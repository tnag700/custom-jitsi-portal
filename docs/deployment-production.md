# Production deployment

This guide prepares the portal for a hostname-based deployment on one Ubuntu 24
host. Replace `portal.example.com`, `auth.example.com`, and `meet.example.com`
with the real portal, identity, and conference hostnames before deployment.

The canonical public perimeter is intentionally small:

- TCP 80 and 443 terminate on Nginx.
- UDP 10000 is forwarded directly to Jitsi Videobridge.
- PostgreSQL, Redis, backend, frontend, Keycloak, Vault, Prometheus,
  Alertmanager, Grafana, and their management endpoints are not published.

## DNS and router prerequisites

Create public DNS records for the three hostnames pointing to the router's
public address. If the ISP equipment and the site router both perform NAT,
configure forwarding on both devices or switch the ISP device to bridge mode.

Forward these ports to the application VM:

| External port | Protocol | VM target | Purpose |
| --- | --- | --- | --- |
| 80 | TCP | `10.10.100.29:80` | ACME challenge and HTTPS redirect |
| 443 | TCP | `10.10.100.29:443` | Portal, Keycloak, and Jitsi web traffic |
| 10000 | UDP | `10.10.100.29:10000` | Jitsi media |

Do not forward SSH globally. Restrict TCP 22 to an operator VPN or a known
source CIDR. Test UDP 10000 from a genuinely external network; a TCP port
scanner cannot validate the media path. Set `JVB_ADVERTISE_IPS` to the public
NAT address, not to `10.10.100.29`.

## Repository preparation

1. Install Docker Engine with the Compose plugin and Git.
2. Clone the repository onto a filesystem with at least 20 GiB free before
   pulling images and building both applications.
3. Copy `.env.production.example` to `.env.production`.
4. Replace every `*.example.com` URL and the documentation address
   `203.0.113.10`.
5. Keep secret values out of `.env.production`; repo-managed env files contain
   only non-secret config и path hints to private, Vault-rendered delivery
   files.
6. Replace hostname and certificate paths in
   `deploy/nginx/portal.conf.example`, preferably by copying it into the
   ignored `deploy/nginx/local/` directory and setting
   `NGINX_PORTAL_CONFIG_PATH`.

Before any deployment:

```bash
npm run stack:validate
npm run prod:baseline:validate
npm run prod:host:baseline:validate
docker compose --env-file .env.production \
  -f docker-compose.production.yml config >/tmp/jitsi-compose.yml
```

The configuration render must show only `80/tcp`, `443/tcp`, and
`10000/udp` as host-published ports. It must also contain a non-empty
`JVB_ADVERTISE_IPS`.

## Certificates

Obtain certificates for all three hostnames before starting the hardened Nginx
service. The directory referenced by `NGINX_CERTS_PATH` is mounted read-only
as `/etc/letsencrypt`.

The `deploy/nginx/portal-ip.conf.example` file is a bootstrap-only fallback. It
does not provide the hostname-separated Keycloak and Jitsi production policy
and is not the production source of truth.

## Keycloak

Production imports `pilot/keycloak/realm/production/jitsi-realm.json`. It has
no seeded users. The OIDC client secret is substituted from
`SSO_CLIENT_SECRET` in the Keycloak service-specific Vault delivery file.
Keycloak realm import supports environment placeholders; never replace the
placeholder with a committed secret.

The backend's public redirect URI is:

```text
https://portal.example.com/login/oauth2/code/keycloak
```

Nginx forwards `/login/oauth2/` and `/oauth2/` to the backend. Create production
users through the Keycloak administrative workflow, set their `tenantId`
attribute, and assign only the required realm role (`admin`,
`support-engineer`, or `participant`; elevated platform roles require a
separate approval).

## Vault internal-only secret zone

Vault is an internal-only secret zone attached only to `secret_net` and
`ops_net`; it has no host-published port. The committed
`deploy/vault/Dockerfile` uses an exact stable artifact path from the approved
mirror and verifies it against the matching checksum source.

Validate the secret plane:

```bash
npm run prod:secret:baseline:validate
npm run prod:secret:auth:validate
npm run prod:secret:delivery:validate
```

The canonical backend flow is a response-wrapped AppRole handoff to
`backend-vault-bootstrap`. Workload roles enforce `secret_id_num_uses=1`.
Database credentials use the Vault database secrets engine, with a documented
static-role transition for the current long-lived JVM runtime.

Operator access to Vault is allowed only through a private path, bastion или VPN.
Enable the file audit device using
`deploy/vault/bootstrap/enable-audit-file.sh.example`, then capture evidence
that `docker compose port vault 8200` returns no published port.

Vault unseal/recovery material is held by the recovery actor and must not be
placed in repository env files, Compose mounts, or ordinary operator notes.
Ownership and rotation governance are defined in
`deploy/vault/secret-governance-matrix.md`; emergency access and post-use
rotation are defined in `deploy/vault/break-glass-runbook.md`.

## Ubuntu 24 host control plane baseline

Apply the Ubuntu 24 host control plane baseline from `deploy/host/` only after
replacing the SSH allowlist placeholder and verifying an independent recovery
console. Validate it with:

```bash
npm run prod:host:baseline:validate
```

The baseline requires named administrators, key-only SSH, UFW default-deny
inbound policy, time synchronization, bounded journald retention, and an
explicit break-glass path. Do not reload SSH or enable UFW until the dry-run
and recovery checks in `deploy/host/README.md` pass.

## Start and migration

Initialize Vault and render the service-specific secret files through the
private operator workflow. Then start the stack:

```bash
npm run prod:baseline:up
```

Flyway migrations run as part of backend startup. Do not run multiple backend
instances against an unverified migration state. Wait for health checks rather
than treating container `running` state as readiness.

To enable the private monitoring overlay:

```bash
npm run prod:baseline:up:monitoring
```

Grafana and Alertmanager remain on `ops_net`; access them through an operator
tunnel or a separately reviewed private reverse-proxy path.

## Production smoke checklist

1. `curl -I http://portal.example.com` returns an HTTPS redirect.
2. `curl -fsS https://portal.example.com/api/v1/health` returns healthy JSON.
3. The browser login flow returns through
   `/login/oauth2/code/keycloak` and `/api/v1/auth/me` contains the expected
   `tenantId` and role.
4. An administrator can create a room and meeting, and a participant can use a
   generated invite.
5. Two clients on different external networks can exchange audio and video.
6. `docker compose ps` reports healthy application services.
7. No host port other than TCP 80/443 and UDP 10000 is reachable externally.
8. Prometheus evaluates the committed rules and Alertmanager sends both firing
   and resolved notifications during the controlled drill.

For rollback, preserve PostgreSQL, Keycloak, and Vault data volumes, stop the
new application containers, restore the previously tested image/tag set, and
rerun the smoke checklist. Never remove persistent volumes as an application
rollback step.
