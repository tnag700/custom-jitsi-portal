# Break-Glass Runbook

Этот runbook фиксирует repo-kept baseline для break-glass и recovery separation в Story 19.4. Он описывает approval path, evidence, cleanup и post-use rotation, но не хранит реальные unseal keys, recovery material, bastion routes, hostnames или offline custody details.

## Scope and non-goals

- Break-glass path отделён от обычного day-2 deploy workflow, routine Vault operations и service-specific secret delivery.
- Runbook не заменяет Epic 20 restore drills, Vault outage response automation и incident orchestration.
- Runbook не делает `.env.production*`, compose mounts, shared local override areas или repo-kept notes местом хранения recovery material.

## Role separation model

| Role | Routine responsibility | Explicitly excluded by default |
| --- | --- | --- |
| deploy operator | rollout application changes, run standard compose and validator flows | no automatic access to unseal keys, raw recovery material, emergency root shell or offline custody bundle |
| day-2 Vault operator | routine Vault health/auth/policy checks, audit verification, AppRole/OIDC maintenance | no implicit access to `sys/seal`, unseal shares, raw recovery material or break-glass-only commands |
| bootstrap/init actor | one-time init/bootstrap actions for new environment or explicit re-bootstrap event | not a default day-2 operator; actions require separate approval and evidence |
| recovery actor | approved recovery execution for unseal, recovery material handling and emergency root shell actions | not part of ordinary deploy or routine Vault operator entitlement |

## Approval path

1. Зафиксировать trigger: bootstrap, unseal, recovery or privileged containment event.
2. Получить explicit approval от designated security/platform owners вне обычного deploy channel.
3. Подтвердить, что routine path исчерпан и break-glass действительно требуется.
4. Открыть отдельный incident/recovery record до выполнения privileged действий.

## Allowed command categories

- Проверка состояния: `vault status`, `vault audit list`, `journalctl`, host-console verification commands.
- Recovery-only actions: `vault operator unseal`, `vault operator raft snapshot save <private offline path>`, bounded root-shell diagnostics через out-of-band console.
- Emergency root shell считается отдельным break-glass path и должен выполняться только через approved console path с отдельным evidence trail.

## Evidence to capture

- Кто запросил и кто одобрил break-glass path.
- Какой actor фактически выполнил recovery action.
- Какие команды были разрешены и выполнены.
- Какой private custody surface использовался для recovery material.
- Какие post-use rotation actions запланированы и завершены.

## Storage and custody rules

- Recovery material, unseal shares, emergency root credentials и offline custody bundles живут вне `.env.production*`, compose mounts, shared local override areas и repo-kept operator notes.
- `deploy/vault/local/` и `deploy/host/local/` не являются storage surface для raw recovery material. Они допускаются только для non-secret local notes и environment-specific reminders без чувствительных значений.
- Любой temporary export path должен быть tightly permissioned private storage с documented owner и cleanup deadline.

## Cleanup and post-use rotation

- Cleanup обязателен сразу после завершения privileged action: закрыть console session, удалить temporary exports, зафиксировать evidence, подтвердить возврат material в offline custody.
- Post-use rotation обязательна для всех touched secrets, tokens, certificates или credentials, если break-glass path дал к ним privileged visibility или control.
- Если использовался emergency root shell, post-use rotation и credential review обязательны даже при отсутствии явного compromise proof.

## Operator checklist

- approval path зафиксирован до начала работ;
- recovery actor отличается от обычного deploy operator или имеет отдельное явное назначение на инцидент;
- emergency root shell использован только при подтверждённой необходимости;
- cleanup завершён и подтверждён;
- post-use rotation внесена в evidence и доведена до completion.