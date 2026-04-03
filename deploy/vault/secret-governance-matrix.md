# Secret Governance Matrix

Этот документ задаёт Story 19.4 как governance layer поверх уже существующих Stories 19.1-19.3. Он не создаёт новый delivery baseline, не запускает automated rotation orchestrator и не переносит scope в Epic 20, HA/TLS redesign или перевод всех сервисов на direct Vault clients.

## Core rules

- single source of truth remains Vault path version, transit version or database role version. Repo-managed env files, local pasted notes и rendered runtime files не становятся равноправным source of truth рядом с Vault.
- Rotation workflow не должен требовать ad hoc редактирования host env/config файлов, обычных compose mounts или repo-kept operator notes.
- Overlap windows допустимы только как bounded transition c documented TTL, maintenance window и explicit runtime contract.
- Day-2 operators фиксируют audit evidence, но не получают implicit access к recovery material только потому, что у них есть deploy или routine Vault access.

## Matrix

| Secret class | Owner | Canonical Vault path/engine | Consumer contour | Rotation trigger | Runtime contract | Allowed storage surface |
| --- | --- | --- | --- | --- | --- | --- |
| backend JWT signing secret | Security owner + backend owner | `kv/app/backend/runtime` today; transit-backed strategy only by explicit approved redesign | `backend-vault-bootstrap` -> bounded backend runtime bridge | scheduled cadence, compromise suspicion, personnel change, incident recovery | controlled restart/redeploy of backend after refreshed bridge render | Vault path, private wrapped handoff, bounded runtime bridge `0600`; never repo-kept env files |
| backend OIDC client secret | Identity owner | `kv/identity/backend/oidc` | `backend-vault-bootstrap` -> backend runtime bridge | scheduled cadence, IdP/client rotation, compromise suspicion, personnel change | controlled restart/redeploy of backend after refreshed bridge render | Vault path, private wrapped handoff, bounded runtime bridge `0600`; never pasted troubleshooting notes |
| DB credentials | Platform owner + DB owner | preferred `database/creds/backend-app`, `database/creds/keycloak-app`, `database/creds/backup-job`; transitional `database/static-creds/*` only with explicit rationale | backend runtime bridge, Keycloak service env file, backup runner handoff | scheduled cadence, personnel change, compromise suspicion, incident recovery | controlled restart/redeploy for transitional static-role consumers; lease-aware cutover only where consumer is designed for it | Vault database engine, service-specific rendered file, private operator handoff; no repo-managed secret placeholders |
| Keycloak admin/bootstrap secret | Identity owner | `kv/identity/keycloak/runtime` | Keycloak service-specific rendered env file | scheduled cadence, bootstrap completion, compromise suspicion, personnel change | controlled restart/redeploy after new file render | Vault path plus tightly permissioned rendered env file (`0600` or `0640` with explicit service ownership) |
| Jitsi shared secrets | Realtime owner | `kv/realtime/jitsi/web`, `kv/realtime/jitsi/prosody`, `kv/realtime/jitsi/jicofo`, `kv/realtime/jitsi/jvb` | Jitsi service-specific rendered env files | scheduled cadence, compromise suspicion, node replacement, incident recovery | controlled restart/redeploy of affected Jitsi service | Vault path plus service-specific rendered env file; never compose inline secret values |
| backup-related credentials | Platform owner + recovery owner | `kv/backup/runner/runtime`, `kv/backup/backend/runtime`, preferred `database/creds/backup-job`, transitional `database/static-creds/backup-job` | backup runner handoff and backend backup integration | scheduled cadence, destination change, compromise suspicion, recovery event | controlled restart/redeploy or explicit re-handoff before backup job execution | Vault path, one-use wrapped handoff, tightly permissioned private storage outside git |
| TLS certificates | Edge/platform owner | Vault-driven PKI role such as `pki/issue/public-edge` or approved versioned Vault metadata path for current certificate contour | nginx cert mount as bounded private delivery surface | certificate expiry window, CA rollover, compromise suspicion, edge incident recovery | bounded reload/redeploy of nginx with rollback-safe cert handoff | Vault-driven issuance path plus private cert mount; nginx cert directory is delivery surface, not source of truth |

## Rotation and evidence minimums

- Для каждого cutover сохранять: инициатор, target secret class, canonical Vault path/engine, version or lease reference, выполненный runtime contract и validator/smoke proof.
- Scheduled rotation, personnel change, compromise suspicion, incident recovery и certificate expiry window должны быть видимыми trigger categories в operator evidence.
- Transitional static-role contracts должны иметь явное justification note и bounded exit plan; они не считаются permanent target architecture.

## Non-goals

- Этот matrix не вводит новый delivery redesign поверх `backend-vault-bootstrap` и service-specific rendered env files.
- Этот matrix не заменяет Epic 20 restore drills, Vault outage response, containment automation и incident-response orchestration.
- Этот matrix не превращает Story 19.4 в HA/TLS redesign для Vault listener, browser Vault access или full secret rotation automation.