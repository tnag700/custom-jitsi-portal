# ВНИМАНИЕ: это эксперимент по работе ИИ-агентов, возможен "ИИ слоп"; ответственный за код слоператор не является профессиональным программистом.

## Jitsi видеоконференция с веб-порталом

Монорепозиторий для разработки собственного портала вокруг сценариев видеоконференций на базе Jitsi:
- backend API на Spring Boot 4;
- frontend на Qwik City с SSR;
- локальная инфраструктура и интеграции через Docker Compose;
- observability и alerting-сценарии для локального self-hosted контура.

## Зачем этот проект

Проект используется как полигон для:
- разработки API и UI в домене видеоконференций;
- проверки архитектурных решений и quality gates;
- практики разработки с поддержкой ИИ-агентов;
- локального self-hosted сценария вместо внешних провайдеров видеосвязи.

## Технологический стек

- Backend: Java 25, Spring Boot 4.0.3, Gradle.
- Data: PostgreSQL 18, Redis 8, Flyway.
- Security/SSO: Spring Security, OAuth2 client/resource server, Keycloak.
- Frontend: Qwik City, Vite 7, TypeScript 5.9, ESLint 9, Vitest 3.
- API contract: `openapi.yaml` и сгенерированный `openapi.generated.json`.
- Observability: Spring Boot Actuator, OpenTelemetry, Prometheus, Alertmanager, Grafana.
- Architecture governance: ArchUnit, PMD, CPD.
- Local environment: Docker Compose.

## Структура репозитория

- `backend/` - REST API, доменные модули, безопасность, миграции, архитектурные и интеграционные тесты.
- `frontend-qwik/` - SSR-веб-клиент, маршруты, shared API client, UI и frontend-тесты.
- `openapi.yaml` - канонический контракт API.
- `openapi.generated.json` - повторяемо сгенерированный runtime-снимок OpenAPI.
- `docker-compose.yml` - локальный запуск frontend, backend, Keycloak, Postgres, Redis и Jitsi stack.
- `docker-compose.production.yml` - production-oriented perimeter baseline с trust-zone сетями и закрытыми internal surfaces.
- `docker-compose.production.monitoring.yml` - private-only monitoring overlay для production baseline.
- `deploy/runtime/production-runtime-policy-matrix.md` - service-by-service runtime hardening matrix для least-privilege, read-only и writable exceptions.
- `.env.example` - обязательные переменные окружения для docker-compose.

## Быстрый старт

1. Подготовьте окружение:
   - скопируйте `.env.example` в `.env`;
   - при необходимости переопределите `KEYCLOAK_ADMIN` и `DEV_VAULT_*` значения для локального Vault bootstrap.
2. Frontend dev-режим без контейнеров (из корня):
   - `npm run frontend:install`
   - `npm run frontend:dev`
3. Локальная production-like проверка frontend (из корня):
   - `npm run frontend:build`
   - `npm run frontend:start`
4. Полная локальная среда в контейнерах (из корня):
   - `npm run prod:up`
   - команда автоматически поднимет локальный Vault, выполнит bootstrap dev-секретов и только затем запустит остальной стек.
5. Полная локальная среда + monitoring overlay (из корня):
   - `npm run prod:up:monitoring`
6. Проверка dev-конфигурации перед пересборкой стека:
   - `npm run stack:validate`
7. Проверка production perimeter baseline без запуска стенда:
   - `npm run prod:baseline:validate`
   - `npm run prod:baseline:config`
8. Проверка production runtime baseline без запуска стенда:
   - `npm run prod:runtime:baseline:validate`
9. Проверка Ubuntu host/control-plane baseline без запуска стенда:
   - `npm run prod:host:baseline:validate`
   - `npm run prod:host:baseline:simulate`
10. Проверка Vault secret-plane baseline без запуска стенда: `npm run prod:secret:baseline:validate`

Все validator/smoke entrypoints из `package.json` теперь вызывают Python-скрипты из `scripts/`. Если удобнее обходиться без npm-обёрток, используйте прямой кроссплатформенный запуск через `python scripts/...`, например:

- `python scripts/validate-dev-stack-config.py`
- `python scripts/validate-production-perimeter.py`
- `python scripts/run-observability-live-drill.py`

Важно: Postgres-данные в Docker Compose сохраняются в volume `pgdata`. Если раньше среда запускалась с некорректным маппингом volume и после перезапуска пропадали профили или другие данные, пересоздайте stack после исправления compose-конфигурации, чтобы Postgres 18 использовал ожидаемый mount path `/var/lib/postgresql` и image-managed data layout.

Важно: `npm run prod:up` и `npm run prod:up:monitoring` теперь сначала валидируют dev-конфигурацию. Проверка останавливает запуск, если в `pilot/keycloak/realm/jitsi-dev-realm.json` у seeded users нет явных `id` или если в `docker-compose.yml` снова появится некорректный Postgres volume mount.

Docker Compose теперь следует воспринимать как production-like сценарий локальной проверки: frontend и backend собираются из исходников внутри Docker, без требования заранее готовить локальные `dist/` или `build/libs/` артефакты.

Production perimeter baseline вынесен отдельно в `docker-compose.production.yml` и не заменяет локальный bootstrap из `docker-compose.yml`. Это осознанное разделение: локальный self-hosted контур остаётся удобным для разработки, а production baseline фиксирует закрытую сеть и минимальную публичную поверхность.

Host/control-plane baseline для Ubuntu 24 вынесен отдельно в `deploy/host/`. Он дополняет perimeter baseline требованиями к SSH, UFW, named admin accounts, AppArmor, time sync и audit retention, а не подменяет production compose или nginx source of truth.

Vault secret-plane baseline для Story 19.1 вынесен в `deploy/vault/` и добавляет internal-only secret zone поверх уже существующих trust zones. Story 19.2 добавляет сверху scoped auth model: backend-scoped helper `backend-vault-bootstrap` реализует response-wrapped AppRole startup fetch, operators используют named OIDC identities с explicit group binding, а frontend SSR по умолчанию не становится Vault client. Story 19.3 переводит delivery off repo-managed secret env values: `.env.production*` теперь должны содержать только non-secret config и path hints к private Vault delivery surfaces, а service-specific rendered env files и backend runtime bridge остаются вне git. Story 19.4 добавляет governance layer через `deploy/vault/secret-governance-matrix.md` и `deploy/vault/break-glass-runbook.md`: rotation остаётся Vault-driven, а break-glass path отделяется от day-2 deploy/operator access.

## Сценарии запуска

### 1. Frontend dev build

Используйте этот режим, когда нужен быстрый цикл разработки UI на Vite dev server.

- `npm run frontend:dev`

Это эквивалент `npm --prefix frontend-qwik run dev`. Режим использует Vite SSR dev server и не отражает production-поведение загрузки ассетов.

### 2. Frontend production build локально

Используйте этот режим, когда нужно проверить реальный SSR-бандл frontend без полного Docker-окружения.

- `npm run frontend:build`
- `npm run frontend:start`

Полезные варианты:

- `npm run frontend:preview` - локальный preview production-бандла через Vite preview.
- `npm run frontend:verify:ssr` - build + smoke-проверка SSR/resumability.

### 3. Full stack в контейнерах

Используйте этот режим, когда нужна prod-like интеграция frontend, backend, Postgres, Redis, Keycloak и Jitsi.

- `npm run prod:up`
- `npm run prod:down`

Технические aliases `stack:*` сохранены как низкоуровневые обёртки над Docker Compose, но для повседневной работы ориентируйтесь на `prod:*`.

### 4. Full stack с monitoring

Используйте этот режим, когда нужно локально проверить observability и alerting поверх основного контура.

- `npm run prod:up:monitoring`
- `npm run prod:down:monitoring`

Для предварительной валидации compose-конфигурации:

- `npm run prod:config`
- `npm run prod:config:monitoring`

### 5. Production perimeter baseline

Используйте этот режим, когда нужно проверить hardened production topology, не смешивая её с локальным dev bootstrap.

- `npm run prod:baseline:validate`
- `npm run prod:baseline:config`
- `npm run prod:baseline:config:monitoring`

`prod:baseline:validate` теперь проверяет не только perimeter/trust-zone membership, но и strict edge hardening: overwrite `X-Forwarded-*`, correlation-friendly access logging, rate/connection limits, блокировку public debug/management surfaces и Keycloak trusted proxy policy с pinned nginx reverse-proxy address вместо broad private-range defaults.

Runtime hardening вынесен в отдельный validator `prod:runtime:baseline:validate`, а `prod:baseline:validate` теперь запускает perimeter guardrails и runtime guardrails вместе. Каноническая service-by-service матрица исключений и writable paths находится в `deploy/runtime/production-runtime-policy-matrix.md`.

Если подготовлены реальные `.env.production`, nginx-конфиг и сертификаты для стенда:

- `npm run prod:baseline:up`
- `npm run prod:baseline:up:monitoring`
- `npm run prod:baseline:down`
- `npm run prod:baseline:down:monitoring`

`prod:baseline:config*` использует committed `.env.production.example`, чтобы guardrails и compose rendering можно было проверить без приватных секретов. `prod:baseline:up*` по-прежнему ожидает реальный `.env.production`.

Для реального запуска hardened baseline нужно дополнительно указать:

- `NGINX_PORTAL_CONFIG_PATH` - путь до реального nginx-конфига стенда;
- `NGINX_CERTS_PATH` - путь до каталога, который монтируется в контейнер как `/etc/letsencrypt`.

По умолчанию production compose закрепляет nginx на `identity_net` с адресом `172.28.240.10`, и именно этот адрес попадает в `KC_PROXY_TRUSTED_ADDRESSES`. Если меняете сетевую схему, обновляйте это значение точечно, а не расширяйте доверие до `10.0.0.0/8`, `172.16.0.0/12` или `192.168.0.0/16`.

Для long-term production baseline используйте hostname-based template из `deploy/nginx/portal.conf.example`. `deploy/nginx/portal-ip.conf.example` остаётся bootstrap-only fallback без права диктовать trusted proxy policy и final edge hardening.

### 6. Ubuntu host control plane baseline

Используйте этот режим, когда нужно проверить repo-kept baseline для Ubuntu 24 host и operator access path.

- `npm run prod:host:baseline:validate`
- `npm run prod:host:baseline:simulate`

Прямые Python entrypoints для тех же проверок:

- `python scripts/validate-production-host-baseline.py`
- `python scripts/run-production-host-baseline-container-smoke.py`

Baseline artifacts находятся в `deploy/host/` и фиксируют:

- SSH key-only posture через `sshd_config.d` snippet;
- UFW default-deny baseline c placeholder для operator allowlist;
- named admin accounts + sudo model вместо shared root practices;
- AppArmor/time sync checks и evidence retention для SSH, sudo, container restarts и firewall changes.

Этот validator не заменяет `prod:baseline:validate`: perimeter и host guardrails должны оставаться зелеными вместе.

`prod:host:baseline:simulate` полезен, когда целевой Ubuntu host ещё не готов. Он поднимает `ubuntu:24.04` container, прогоняет `sshd -t`, исполняет committed `ufw --dry-run` preview для allow rules и отдельно валидирует committed default policy команды в disposable окружении. Это только partial smoke: AppArmor, journald, `timedatectl` и реальная host auditability всё равно требуют проверки на реальном сервере.

### 7. Container runtime baseline

Используйте этот режим, когда нужно проверить least-privilege posture production-контейнеров поверх perimeter и host baseline.

- `npm run prod:runtime:baseline:validate`

Прямой Python entrypoint:

- `python scripts/validate-production-runtime-baseline.py`

Baseline фиксирует и проверяет:

- `cap_drop: [ALL]` и `security_opt: ["no-new-privileges:true"]` как default runtime posture;
- запрет `privileged`, `use_api_socket`, `network_mode: host`, `pid: host`, `ipc: host` и Docker socket mounts;
- `read_only: true` для stateless и runtime-configured сервисов, где writable paths вынесены в явные targets (`nginx`, `frontend`, `backend`, `redis`, `keycloak`, `jitsi-web`, `jitsi-prosody`, `jitsi-jicofo`, `jitsi-jvb`, `mock-alert-receiver`);
- runtime limits для critical services: `frontend`, `backend`, `keycloak`, `jitsi-web`, `jitsi-prosody`, `jitsi-jicofo`, `jitsi-jvb`.

Documented exceptions не держатся в голове: они зафиксированы в `deploy/runtime/production-runtime-policy-matrix.md`. Сейчас blanket writable rootfs остаётся только у PostgreSQL и части monitoring tooling, а Redis, Keycloak и critical Jitsi services переведены на `read_only: true` с явными writable targets через `tmpfs` и named volumes.

Runtime images frontend и backend также запускаются не под root user, чтобы least-privilege baseline не ограничивался только Linux capabilities.

Минимальный smoke path после изменения runtime baseline:

- `npm run prod:runtime:baseline:validate`
- `npm run prod:baseline:config`
- `npm run prod:baseline:config:monitoring`
- `docker inspect <container>` или эквивалентный evidence path для `CapDrop`, `SecurityOpt`, `ReadonlyRootfs` и mounts после реального rollout.

### 8. Vault secret plane baseline

Используйте этот режим, когда нужно проверить, что Vault добавлен в production topology как internal-only secret zone, а не как новый публичный control-plane surface.

Committed runtime definition остаётся в `deploy/vault/Dockerfile`: Vault image собирается локально из approved Yandex mirror artifact path, а не тянется как отдельный unmanaged runtime source.

- `npm run prod:secret:baseline:validate`
- `npm run prod:secret:auth:validate`
- `npm run prod:secret:delivery:validate`

Прямые Python entrypoints:

- `python scripts/validate-production-secret-plane.py`
- `python scripts/validate-production-vault-auth-baseline.py`
- `python scripts/validate-production-secret-delivery-baseline.py`

### 9. Vault auth/policy baseline

Используйте этот режим, когда нужно проверить scoped workload/operator auth model поверх уже введённого secret plane.

- `npm run prod:secret:auth:validate`

Baseline фиксирует и проверяет:

- backend-scoped helper `backend-vault-bootstrap` как единственный default application-side Vault client с `secret_net` membership и canonical AppRole startup-fetch baseline;
- least-privilege policy templates для backend, backup runner, Keycloak, Jitsi и operators без broad `kv/*`, `secret/*`, `sys/*` для workload-ов;
- `auth/approle-workloads` для machine identities и `auth/oidc-operators` для named operators;
- explicit decision, что `database` secrets engine остаётся preferred target для DB credentials;
- отсутствие direct Vault client model по умолчанию для frontend SSR и browser runtime.

Focused auth validator не означает, что migration secrets уже завершена. Он подтверждает только repo-kept auth/policy baseline, backend-scoped bootstrap helper path и service boundary guardrails.  

### 10. Vault secret delivery baseline

Используйте этот режим, когда нужно проверить, что реальные production secrets больше не ожидаются через global `.env.production`, а delivery идёт через backend startup fetch и service-specific Vault-rendered files.

- `npm run prod:secret:delivery:validate`

Baseline фиксирует и проверяет:

- отсутствие critical secret values в `.env.production.example`;
- backend pre-start env bridge поверх `backend-vault-bootstrap` и named runtime volume;
- service-specific env files для PostgreSQL, Redis, Keycloak и Jitsi вместо repo-managed secret env placeholders;
- отсутствие direct Vault access у frontend/browser path и сохранение `backend-vault-bootstrap` как канонического backend consumer-а.

Baseline фиксирует и проверяет:

- наличие `vault` service только в `secret_net` и `ops_net`, без host-published `8200/tcp`;
- repo-kept Vault baseline artifacts в `deploy/vault/`, включая build-from-mirror `Dockerfile`;
- pinned stable release policy через approved Yandex mirror path `https://mirror.yandex.ru/mirrors/releases.hashicorp.com/vault/1.21.4/vault_1.21.4_linux_amd64.zip` и checksum source `https://mirror.yandex.ru/mirrors/releases.hashicorp.com/vault/1.21.4/vault_1.21.4_SHA256SUMS`;
- обязательный audit device baseline и private-only operator path через bastion, VPN или equivalent private path;
- явное разделение scope: Story 19.1 не реализует service-to-Vault auth model из Story 19.2, migration app secrets из repo-managed env files из Story 19.3 и backup/restore or incident response redesign из Epic 20.

Для контейнерного baseline используется локально собираемый runtime image `jitsi-vault:1.21.4`, но его build path жёстко привязан к approved mirror artifact path и checksum source того же stable release. Это убирает отдельный неуправляемый runtime source и делает mirror policy частью реального deploy path.

## Основные адреса локальной среды

- Frontend: `http://localhost:3000`
- Backend API: `http://localhost:8080/api/v1`
- Actuator health: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8082`
- Keycloak: `http://localhost:8081`
- Jitsi Web: `https://localhost:8443`
- Jitsi Web HTTP: `http://localhost:8000`

Если поднят monitoring overlay через `docker-compose.monitoring.yml`:
- Prometheus: `http://localhost:9090`
- Alertmanager: `http://localhost:9093`
- Mock alert receiver: `http://localhost:9080/notifications`
- Grafana: `http://localhost:3001`

## Команды разработки

### Root-скрипты запуска

- `npm run frontend:install` - установка зависимостей frontend-qwik из корня репозитория.
- `npm run frontend:dev` - frontend dev server на Vite/Qwik SSR.
- `npm run frontend:build` - production build frontend.
- `npm run frontend:start` - запуск production SSR frontend поверх собранного `dist/`.
- `npm run frontend:preview` - локальный preview production-бандла frontend.
- `npm run frontend:verify:ssr` - production build + smoke-проверка SSR/resumability.
- `npm run prod:up` - основной production-like сценарий контейнерного запуска.
- `npm run prod:down` - остановка основного production-like контейнерного сценария.
- `npm run prod:up:monitoring` - production-like контейнерный запуск с monitoring overlay.
- `npm run prod:down:monitoring` - остановка production-like контейнерного сценария с monitoring overlay.
- `npm run prod:config` - проверка итоговой compose-конфигурации production-like контура.
- `npm run prod:config:monitoring` - проверка compose-конфигурации production-like контура с monitoring overlay.
- `npm run prod:baseline:validate` - guardrail-проверка production perimeter, trust-zone membership, strict forwarded headers, throttling и закрытия debug/management surfaces.
- `npm run prod:runtime:baseline:validate` - guardrail-проверка container runtime baseline: least-privilege posture, read-only/tmpfs expectations, runtime limits и запрет Docker socket/host-mode drift.
- `npm run prod:host:baseline:validate` - guardrail-проверка repo-kept Ubuntu 24 host/control-plane baseline: SSH, UFW, operator model, AppArmor, time sync и journald retention.
- `npm run prod:host:baseline:simulate` - partial container smoke для Ubuntu 24 host baseline: `sshd -t`, committed `ufw --dry-run` preview для allow rules и disposable validation для default policy команд внутри временного `ubuntu:24.04` container.
- `npm run prod:secret:baseline:validate` - guardrail-проверка Vault secret-plane baseline: internal-only topology, approved mirror policy, audit bootstrap и private operator path notes.
- `npm run prod:secret:delivery:validate` - guardrail-проверка bounded secret delivery baseline: backend runtime bridge, service-specific rendered env files и отсутствие long-lived repo-managed secret env delivery.
- `npm run prod:baseline:up` - запуск отдельного production baseline из `docker-compose.production.yml`.
- `npm run prod:baseline:down` - остановка отдельного production baseline.
- `npm run prod:baseline:up:monitoring` - запуск production baseline с private monitoring overlay.
- `npm run prod:baseline:down:monitoring` - остановка production baseline с monitoring overlay.
- `npm run prod:baseline:config` - развёртка и проверка итоговой compose-конфигурации hardened production baseline.
- `npm run prod:baseline:config:monitoring` - проверка compose-конфигурации hardened production baseline с monitoring overlay.
- `npm run stack:up` - поднять полный контейнерный контур.
- `npm run stack:up:monitoring` - поднять полный контейнерный контур с monitoring overlay.
- `npm run stack:down` - остановить основной compose-контур.
- `npm run stack:down:monitoring` - остановить compose-контур вместе с monitoring overlay.
- `npm run stack:config` - развернуть и проверить итоговую compose-конфигурацию.
- `npm run stack:config:monitoring` - развернуть и проверить compose-конфигурацию с monitoring overlay.

### Backend

- `./gradlew.bat build` - полная сборка backend.
- `./gradlew.bat test` - весь backend test suite.
- `./gradlew.bat testSmoke` - быстрые тесты без integration tag.
- `./gradlew.bat testUnit` - unit-only тесты.
- `./gradlew.bat testSlice` - slice-тесты Spring.
- `./gradlew.bat testIntegration` - integration тесты без container tag.
- `./gradlew.bat testContainer` - container-backed тесты.
- `./gradlew.bat generateOpenApiSpec` - генерация runtime OpenAPI snapshot.

Тестовая пирамида в backend зафиксирована как `unit / slice / non-container integration / container`.
Container-сценарии запускаются через `testContainer`, требуют `Docker` и используют `Testcontainers` как канонический baseline для PostgreSQL и Redis.

### Root-скрипты observability

- `npm run observability:alerting:validate` - валидация Prometheus и Alertmanager артефактов.
- `npm run observability:drill` - одиночный synthetic drill для backend и alerting-контура.
- `npm run observability:drill:extended` - расширенный drill с несколькими traffic cycles.

Те же observability-проверки можно запускать напрямую через Python:

- `python scripts/validate-observability-alerting.py`
- `python scripts/run-observability-live-drill.py`
- `npm run observability:alerting:smoke` - smoke-проверка полного firing/resolved цикла для alerting.

## Текущее состояние

Проект находится в активной экспериментальной разработке. Структура модулей, API и инфраструктурные сценарии продолжают эволюционировать, поэтому обратная совместимость между коммитами не гарантируется.
