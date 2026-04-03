# Vault secret plane and scoped auth baseline

Этот каталог задаёт repo-kept baseline для Stories 19.1-19.4: internal-only secret plane, reviewable auth/policy mapping, bounded secret delivery model и governance layer для rotation/break-glass. Это всё ещё не delivery redesign, не automated incident response и не HA/TLS redesign.

## Scope и non-goals

- Story 19.1 вводит Vault как internal-only service в `secret_net` и `ops_net` без public ingress.
- Story 19.2 добавляет scoped AppRole/OIDC baseline, service-to-Vault mapping и least-privilege policy templates.
- Story 19.3 переводит фактическую delivery модель production secrets off repo-managed `.env.production*` и compose secret placeholders.
- Story 19.4 добавляет governance layer для secret handling, rotation policy matrix и break-glass separation без открытия нового delivery baseline.
- Epic 20 остаётся отдельным scope для restore drills, Vault outage response, containment automation и incident-response orchestration.
- HA/TLS redesign для Vault listener, browser Vault access и перевод всех сервисов на direct Vault clients здесь не открываются.

## Canonical artifacts

- `Dockerfile` - container runtime, собираемый из exact approved mirror artifact path.
- `config/vault.hcl.example` - single-node Vault server config с integrated Raft storage.
- `bootstrap/enable-audit-file.sh.example` - audit device enablement и verify path.
- `bootstrap/issue-backend-wrapped-secret-id.sh.example` - operator-side example для response-wrapped backend handoff.
- `bootstrap/issue-backup-wrapped-secret-id.sh.example` - operator-side example для backup runner handoff.
- `auth/README.md` - canonical auth model, path taxonomy и evidence path.
- `auth/backend/*` - backend AppRole startup-fetch baseline.
- `auth/backup/*` - separate backup runner machine identity baseline и startup-fetch example.
- `auth/operators/*` - named operator OIDC baseline.
- `delivery/README.md` - service-by-service secret delivery matrix и runtime contracts для Story 19.3.
- `secret-governance-matrix.md` - canonical secret rotation governance matrix for Story 19.4.
- `break-glass-runbook.md` - canonical break-glass and recovery separation runbook for Story 19.4.
- `policies/*.hcl.example` - service-specific least-privilege policies.
- `local/` - private operator overrides и rollout notes без коммита host-specific recovery material.

## Story 19.4 governance layer

- `secret-governance-matrix.md` фиксирует secret classes, owners, canonical Vault paths/engines, rotation triggers и runtime contracts так, чтобы single source of truth remained inside Vault-driven paths.
- `break-glass-runbook.md` фиксирует approval path, evidence, cleanup и post-use rotation для privileged recovery actions.
- Governance layer усиливает baseline Stories 19.1-19.3, но не подменяет delivery surfaces, не создаёт второй secret-delivery baseline и не превращает Story 19.4 в HA/TLS redesign.

## Topology baseline

- Service `vault` живёт только в `secret_net` и `ops_net`.
- Host-published `8200/tcp` отсутствует. Public ingress через nginx запрещён.
- Operator path идёт только через private path, bastion or VPN. Public internet access к Vault не допускается.
- `backend-vault-bootstrap` живёт в `secret_net` как backend-scoped bootstrap helper и остаётся единственным default application-side Vault client даже после Story 19.3.
- `frontend`, browser runtime, `keycloak` и Jitsi stack не получают direct Vault client baseline по умолчанию.
- Для current single-host stage допустим single Vault node c integrated storage Raft. Это intentional non-HA compromise, а не финальная topology.

## Scoped auth model

- Workloads используют `auth/approle-workloads`.
- Named operators используют `auth/oidc-operators`.
- Operator OIDC role должна быть ограничена explicit group binding `vault-operators`, а не только trusted audience.
- Canonical backend baseline: identity `backend-service`, AppRole pull mode, response-wrapped `secret_id`, short-lived batch token и backend-scoped startup-fetch helper.
- Story 19.4 закрепляет для этого пути governance rules: one-use wrapped handoff, bounded TTL, cleanup of temporary sinks и audit-backed custody.
- Backup runner использует отдельный AppRole, отдельную policy и отдельный startup-fetch example; shared machine identity запрещён.
- Keycloak и Jitsi имеют committed mapping rules и policy shape, но не становятся direct Vault clients автоматически.
- Root token не используется для day-2 operations.

## Policy taxonomy and database decision

- KV prefixes разделены по сервисным контурам: `app`, `identity`, `realtime`, `backup`.
- Backend OIDC-related material живёт внутри `identity/backend`, а не в отдельном top-level prefix.
- Для `kv-v2` policy templates всегда использовать корректные `data/` и `metadata/` endpoints.
- Preferred target для DB credentials - `database` secrets engine, а не broad `kv-v2` fallback.
- Если dynamic DB credentials пока не включены для long-lived consumers, transitional `database/static-creds/*` path должен иметь documented rationale и restart-based runtime contract.

## Approved source policy

- Approved mirror root: `https://mirror.yandex.ru/mirrors/releases.hashicorp.com/vault/`
- Pinned stable variant для Linux amd64 baseline: `https://mirror.yandex.ru/mirrors/releases.hashicorp.com/vault/1.21.4/vault_1.21.4_linux_amd64.zip`
- Checksum source: `https://mirror.yandex.ru/mirrors/releases.hashicorp.com/vault/1.21.4/vault_1.21.4_SHA256SUMS`
- Container baseline собирается локально через `deploy/vault/Dockerfile` из exact approved mirror artifact path и checksum source выше.
- Runtime image tag `jitsi-vault:1.21.4` допустим только как локальный результат этого build path, а не как отдельный source of truth.
- Pre-release variants (`rc`, `beta`) не использовать для production baseline без отдельного решения.

## Audit and sensitive artifacts

- Audit device обязателен. Это не optional follow-up.
- Baseline path использует file audit device в dedicated path `/vault/audit/vault-audit.log`.
- Secret access должен трассироваться отдельно от application logs и не должен содержать secret values, token material, rendered payloads или recovery notes.
- Не коммитить `role_id`, `secret_id`, wrapping token, operator OIDC client secret, leases и rendered secret files.
- Commit-допустимы только non-secret placeholder files из `delivery/examples/`, которые нужны для `docker compose config` и не являются production source of truth.

## Operator separation

- Deploy access не равен доступу к Vault unseal or recovery material.
- Unseal/recovery material не хранить в repo, `.env.production*`, compose workflow или обычных deploy notes.
- Keycloak admin path, backend deploy path и Vault recovery path не смешивать в один default operator contour.
- Day-2 operators работают по routine path, а break-glass path уходит в `break-glass-runbook.md` с отдельным approval path, cleanup и post-use rotation.

## Minimal smoke / evidence

- `npm run prod:secret:baseline:validate`
- `npm run prod:secret:auth:validate`
- `npm run prod:secret:delivery:validate`

Для кроссплатформенного прямого запуска без npm wrappers:

- `python scripts/validate-production-secret-plane.py`
- `python scripts/validate-production-vault-auth-baseline.py`
- `python scripts/validate-production-secret-delivery-baseline.py`
- `docker compose --env-file .env.production.example -f docker-compose.production.yml config`
- `docker compose --env-file .env.production -f docker-compose.production.yml up -d vault`
- `docker compose --env-file .env.production -f docker-compose.production.yml port vault 8200` должно завершиться ошибкой, подтверждая отсутствие public ingress.
- `docker compose --env-file .env.production -f docker-compose.production.yml exec vault vault status`
- `docker compose --env-file .env.production -f docker-compose.production.yml exec vault sh /vault/bootstrap/enable-audit-file.sh.example`
- `docker compose --env-file .env.production -f docker-compose.production.yml exec vault vault audit list`
- `docker compose --env-file .env.production -f docker-compose.production.yml up backend-vault-bootstrap`
- successful render/read of service-specific env file for PostgreSQL, Redis, Keycloak or Jitsi from private operator path
- successful operator login через named identity на `auth/oidc-operators`
- successful backend login через response-wrapped AppRole handoff на `auth/approle-workloads`
- denied read на соседнем service prefix

Важно: после Story 19.3 repo-managed `.env.production*` больше не должны нести реальные production secrets. Они сохраняют только non-secret config и path hints к private delivery surfaces.
