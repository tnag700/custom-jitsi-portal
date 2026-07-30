# Production Container Runtime Policy Matrix

Этот файл фиксирует repo-kept baseline для Story 18.4 и дополняет perimeter baseline из `docker-compose.production.yml`, `docker-compose.production.monitoring.yml` и host baseline из `deploy/host/`.

## Scope

- Это container runtime и service-isolation baseline поверх Stories 18.1-18.3.
- Файл не переносит scope в Vault secret delivery, backup/restore, incident response taxonomy или host SSH/UFW/AppArmor controls.
- Default posture: `cap_drop: [ALL]`, `security_opt: ["no-new-privileges:true"]`, запрет `privileged`, `use_api_socket`, `network_mode: host`, `pid: host`, `ipc: host` и Docker socket mounts.

## Service Matrix

| Service | Least-Privilege | Read-only rootfs | Writable paths / exception | Runtime limits | Notes |
| --- | --- | --- | --- | --- | --- |
| nginx | `cap_drop: [ALL]`, `cap_add: [NET_BIND_SERVICE]`, `no-new-privileges` | yes | `tmpfs`: `/var/cache/nginx`, `/var/run`, `/var/log/nginx` | no fixed critical requirement in validator | `NET_BIND_SERVICE` нужен из-за bind на 80/443 внутри контейнера. |
| frontend | `cap_drop: [ALL]`, `no-new-privileges` | yes | `tmpfs`: `/tmp` | `cpus`, `mem_limit`, `pids_limit` required | Stateless SSR runtime, writable rootfs не нужен. |
| backend | `cap_drop: [ALL]`, `no-new-privileges` | yes | `tmpfs`: `/tmp` | `cpus`, `mem_limit`, `pids_limit` required | Spring Boot runtime не должен получать broad writable rootfs. |
| backend-vault-bootstrap | `cap_drop: [ALL]`, `no-new-privileges` | yes | named volume `/vault/runtime`, `tmpfs`: `/tmp` | optional for one-shot bootstrap helper | Одноразовый helper для startup-fetch AppRole token delivery; writable scope ограничен runtime sink volume. |
| redis | `cap_drop: [ALL]`, `no-new-privileges` | yes | `tmpfs`: `/data`, `/tmp` | optional | Current production command disables Redis persistence, so writable rootfs is unnecessary. |
| db | `cap_drop: [ALL]`, `no-new-privileges` | no | named volume `/var/lib/postgresql/data` | optional | Stateful service, read-only без отдельного redesign не вводится. |
| keycloak | `cap_drop: [ALL]`, `no-new-privileges` | yes | named volume `/opt/keycloak/data`, `tmpfs`: `/tmp` | `cpus`, `mem_limit`, `pids_limit` required | Realm import stays read-only, while runtime state is isolated to the named data volume. |
| jitsi-web | `cap_drop: [ALL]`, `cap_add: [NET_BIND_SERVICE]`, `no-new-privileges` | yes | named volume `/config`, `tmpfs`: `/tmp`, `/var/cache/nginx`, `/var/run`, `/var/log/nginx` | `cpus`, `mem_limit`, `pids_limit` required | `NET_BIND_SERVICE` нужен для listen на low port внутри контейнера. Writable config generation is isolated to `/config`. |
| jitsi-prosody | `cap_drop: [ALL]`, `no-new-privileges` | yes | named volume `/config`, `tmpfs`: `/tmp` | `cpus`, `mem_limit`, `pids_limit` required | Runtime config generation is isolated to `/config`. |
| jitsi-jicofo | `cap_drop: [ALL]`, `no-new-privileges` | yes | named volume `/config`, `tmpfs`: `/tmp` | `cpus`, `mem_limit`, `pids_limit` required | Runtime config generation is isolated to `/config`. |
| jitsi-jvb | `cap_drop: [ALL]`, `no-new-privileges` | yes | named volume `/config`, `tmpfs`: `/tmp` | `cpus`, `mem_limit`, `pids_limit` required | Runtime config generation is isolated to `/config`. |
| prometheus | `cap_drop: [ALL]`, `no-new-privileges` | no | `tmpfs`: `/tmp`, TSDB path inside container | bounded limits present | Monitoring overlay не получает лишних привилегий, но TSDB path остаётся writable. |
| alertmanager | `cap_drop: [ALL]`, `no-new-privileges` | no | `tmpfs`: `/tmp` | bounded limits present | Runtime-generated rendered config пишет во временный путь. |
| mock-alert-receiver | `cap_drop: [ALL]`, `no-new-privileges` | yes | `tmpfs`: `/tmp` | bounded limits present | Node-based helper может работать без writable rootfs. |
| grafana | `cap_drop: [ALL]`, `no-new-privileges` | no | `tmpfs`: `/tmp`, image/runtime data paths | bounded limits present | Writable exception документирована, чтобы не маскировать image behavior. |

## Canonical Validation Path

- `npm run prod:runtime:baseline:validate`
- `npm run prod:baseline:validate`
- `npm run prod:baseline:config`
- `npm run prod:baseline:config:monitoring`

Прямой validator entrypoint без npm wrapper:

- `python scripts/validate-production-runtime-baseline.py`

## Runtime Smoke Evidence

После rollout сохранить evidence как минимум для `nginx`, `frontend`, `backend`, `keycloak`, `jitsi-web`, `jitsi-prosody`, `jitsi-jicofo`, `jitsi-jvb`:

- `docker inspect <container> --format '{{json .HostConfig.CapDrop}}'`
- `docker inspect <container> --format '{{json .HostConfig.SecurityOpt}}'`
- `docker inspect <container> --format '{{json .HostConfig.ReadonlyRootfs}}'`
- `docker inspect <container> --format '{{json .Mounts}}'`
- `docker ps --format 'table {{.Names}}\t{{.Status}}'`

Если service меняет read-only posture, cap-add policy или writable targets, сначала обновлять этот matrix и validator, и только потом compose baseline.
