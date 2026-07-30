# Vault auth and policy baseline

Этот каталог задаёт reviewable baseline для Story 19.2 и остаётся канонической auth/policy опорой после Story 19.3, когда доставка production secrets уже переведена на bounded runtime surfaces.

## Scope и non-goals

- Story 19.2 фиксирует auth/policy baseline и rollout evidence path.
- Story 19.3 поверх этого baseline переводит delivery off repo-managed secret envs, но не переоткрывает auth mounts и policy taxonomy как отдельный redesign.
- Story 19.2 не подменяет Story 19.4 и Epic 20: rotation, lease cleanup, break-glass, restore drills и incident containment остаются отдельным scope.

## Canonical auth mounts

- Workloads используют `auth/approle-workloads`.
- Named operators используют `auth/oidc-operators`.
- OIDC role для day-2 operators должна быть ограничена group claim `vault-operators`, а не только trusted audience.
- Root token не используется для day-2 operations.

## Path taxonomy

- `kv/data/app/backend/*` и `kv/metadata/app/backend/*` - backend app secrets.
- `kv/data/identity/backend/*` и `kv/metadata/identity/backend/*` - backend OIDC/identity-related material.
- `kv/data/identity/keycloak/*` и `kv/metadata/identity/keycloak/*` - Keycloak-specific identity/bootstrap material.
- `kv/data/app/redis/*` и `kv/metadata/app/redis/*` - Redis runtime password contour для bounded consumer delivery.
- `kv/data/realtime/jitsi/*` и `kv/metadata/realtime/jitsi/*` - Jitsi JWT/shared-secret contour.
- `kv/data/backup/runner/*` и `kv/metadata/backup/runner/*` - backup destination and snapshot material.
- `kv/data/backup/backend/*` и `kv/metadata/backup/backend/*` - backend-facing backup integration material.
- `database/creds/*` - preferred target там, где consumer действительно может жить с lease rotation.
- `database/static-creds/*` - transitional target для current long-lived consumers с documented controlled restart/redeploy contract.

Важно: для `kv-v2` policy templates всегда использовать корректные `data/` и `metadata/` endpoints. Generic `kv/*` или broad `secret/*` access здесь не является baseline.

## Canonical backend baseline

- Backend остаётся primary Vault client по умолчанию.
- Канонический путь один: startup fetch через AppRole pull mode.
- В Story 19.2 этот path исполняется через backend-scoped bootstrap helper container, который использует Vault CLI, response-wrapped handoff files и named runtime sink без изменения backend application image.
- `secret_id` передаётся через response wrapping и unwrap на стороне workload-а.
- Получаемый Vault token должен быть short-lived и `batch`, если приложению не нужны child tokens.
- Governance rule для Story 19.4: `secret_id_num_uses=1`, wrap TTL 5m и bounded custody остаются обязательными для machine-auth handoff.
- Vault Agent path для backend не является вторым равноправным baseline в Story 19.2.

## Handling guardrails

- day-2 operators не получают implicit access к sys/seal, raw recovery material или equivalent break-glass capability только потому, что они обслуживают auth mounts и policy templates.
- recovery actor остаётся отдельной ролью из `../break-glass-runbook.md`; routine OIDC/AppRole maintenance не смешивается с recovery workflows.
- Wrapped handoff values, token sinks и leases считаются sensitive artifacts и не должны попадать в repo-kept notes, pasted snippets или long-lived local convenience files.

## Service mapping rules

- Backend читает только свой `app/backend`, `identity/backend`, `backup/backend`, bounded `app/redis` contour и `database/static-creds/backend-app` либо `database/creds/backend-app` по explicit runtime contract.
- Backup runner получает отдельный AppRole, отдельную policy и отдельный startup-fetch example; shared machine identity запрещён.
- Keycloak и Jitsi не становятся direct Vault clients автоматически. Для них в репозитории зафиксированы mapping rules и enablement criteria, а не обязательный runtime wiring.
- Frontend SSR и browser runtime не становятся Vault clients по умолчанию.

## Database engine decision

- Preferred target для DB credentials - `database` secrets engine.
- Если dynamic DB credentials ещё не включены, transitional `database/static-creds/*` допустим только с явным documented rationale и controlled restart/redeploy contract, а не как silent rollback в static `kv-v2`.
- Policy examples здесь фиксируют shape и target path, но не коммитят реальные DB usernames/passwords.

## Sensitive artifacts

- Не коммитить `role_id`, `secret_id`, wrapping token, operator OIDC client secret, leases или rendered secret files.
- Для локальных operator inputs использовать `deploy/vault/local/`.
- Bootstrap helper mounts и runtime sinks считаются sensitive delivery surface и должны заполняться только из private/local paths вне git.
- Service-specific Vault Agent-rendered env files для PostgreSQL, Redis, Keycloak и Jitsi также считаются sensitive delivery surface и не должны жить в repo-kept operator workflow.

## Minimal smoke / evidence

- successful operator login через named identity на `auth/oidc-operators`
- successful backend login через response-wrapped AppRole handoff на `auth/approle-workloads`
- successful read только своего path
- denied read на соседнем service prefix
- подтверждённый decision по `database` secrets engine и transitional `database/static-creds/*` path для long-lived consumers
- подтверждение, что frontend SSR и browser runtime не получили direct Vault access по умолчанию