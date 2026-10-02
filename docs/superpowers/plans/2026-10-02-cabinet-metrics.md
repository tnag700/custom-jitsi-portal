# Метрики личных кабинетов — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for native execution, or superpowers:subagent-driven-development if the user selects that method. Implement task-by-task; track steps with checkboxes.

**Goal:** Добавить безопасную статистику сервера во все кабинеты и подробный личный дашборд `admin` с выбором показателей из расширяемого каталога.

**Architecture:** Использовать существующий Prometheus, добавить закрытый node_exporter и один серверный каталог фиксированных запросов. Общий блок получает отдельную безопасную проекцию, административный раздел — ограниченные временные ряды и личные настройки PostgreSQL. Переиспользовать авторизацию, CSRF, Qwik layout и API-обвязку; без нового бизнес-модуля и библиотеки графиков.

**Tech Stack:** Java 25 / Spring Boot 4.1.1 / Spring Security / Spring JDBC / Jackson 3; PostgreSQL / Flyway; Prometheus / node_exporter; Qwik 2 beta.45 / TypeScript / SVG; JUnit / Vitest / Python unittest.

**Spec:** [2026-10-01-cabinet-metrics-design.md](../specs/2026-10-01-cabinet-metrics-design.md), согласована 2026-10-02. База плана: `aacee8f`. Исправление регистра authority на существующий `ROLE_admin` уточняет код, не меняет согласованную роль.

## Global Constraints

- Общая сводка доступна всем вошедшим пользователям; гости приглашений и анонимные страницы её не получают.
- Подробности и настройки — только `admin`; backend использует `hasRole(PortalRole.ADMIN.claimValue())`, то есть `ROLE_admin`.
- Четыре общих показателя: доступность backend, CPU и RAM с округлением до ближайших 5 процентных пунктов, качественное состояние диска.
- Никаких внутренних адресов, имён хостов, версий, объёмов ресурсов, путей, labels, PromQL, событий и чужих данных в общей сводке или её ошибках.
- Все новые API: `Cache-Control: private, no-store`; подробные ответы не загружаются и не сериализуются для других ролей.
- До 12 неповторяющихся виджетов/id; периоды `15m`, `1h`, `6h`, `24h`, `7d`; конец диапазона задаёт сервер.
- До 300 точек на ряд, до 2 заранее разрешённых рядов на метрику; предел HTTP-ответа источника 1 MiB, входных настроек 16 KiB.
- Соединение 1 с, запрос 2 с включая чтение тела, общий бюджет выдачи 3 с, до 4 запросов Prometheus одновременно на экземпляр backend; без редиректов и повторов.
- Кэш текущих значений 30 с с одним совместным обновлением; кэш графиков 60 с, максимум 60 комбинаций id/period.
- Свежесть не более 90 с от исходного sample; SSR общего блока не задерживается более чем на 1 с; видимая страница обновляет данные раз в 60 с.
- CPU: rate за 5 минут; диск: предупреждение от 90%, критическое состояние подробной карточки от 95%.
- Настройки принадлежат `tenant_id + subject_id` из principal; JSONB, уникальный ключ и ревизия; конфликт записи — 409.
- Источник задаётся конфигурацией, запросы — каталогом; админ не меняет URL, PromQL, labels или правила раскрытия.
- Exporter: закрытая `ops_net`, без published ports, host networking, Docker socket, privileged и дополнительных capabilities; только проверенные read-only mounts, collectors CPU/meminfo/filesystem.
- Существующие зависимости и доменные границы сохраняются. Grafana, реальные оповещения, JVB/media, Loki/Tempo — последующие самостоятельные этапы.
- Код сначала готовится и проверяется в изолированном checkout по using-git-worktrees. Изменения production выполняются отдельным явно разрешённым выпуском; firewall/NAT не входят в работу.
- Каждый коммит следует Lore из `AGENTS.md`: смысл решения, релевантные `Constraint`, `Tested`, `Not-tested`; только явно перечисленные файлы задачи.

## Review Focus

1. Source вернул HTTP-заголовки и перестал присылать тело: таймаут освобождает соединение и лимит параллельности, кабинет продолжает работать — задача 2.
2. Prometheus вычисляет свежий timestamp поверх старого sample или exporter уже down: значение не обозначается свежим/нормальным — задача 2.
3. Пользователь с operational-ролью открывает URL/API вручную: подробности запрещены до загрузки и отсутствуют в HTML/Qwik — задачи 3 и 5.
4. Два первых сохранения настроек одновременно или одинаковый subject в разных tenants: ровно одна ревизия побеждает, чужая строка не меняется — задача 4.
5. Сессия истекла либо браузерная вкладка скрыта во время обновления: нет бесконечных запросов, сохранённые данные не изображаются свежими — задачи 3 и 5.

## Общие контракты

Пути ниже относительно корня репозитория. `J` = `backend/src/main/java/com/acme/jitsi`, `T` = `backend/src/test/java/com/acme/jitsi`, `F` = `frontend-qwik/src`; это только сокращение списка файлов.

DTO сервиса задаются вложенными records в `ServerMetricsService`, без интерфейсов с единственной реализацией:

- `MetricPeriod`: пять значений периодов выше, `parse(String)` отвергает остальные.
- `MetricDescriptor(String id, String title, String description, String unit, String scope, List<String> views)`; `scope=SYSTEM`, unit=`percent|bytes|per_minute|milliseconds|boolean`, views=`card|line`.
- `MetricPoint(Instant time, Double value)`; `null` обозначает разрыв.
- `MetricSeries(String name, List<MetricPoint> points)`; name — фиксированный безопасный псевдоним, не label источника.
- `MetricReading(String id, Double value, String state, Instant measuredAt, List<MetricSeries> series)`; state = `ok|no_data|no_traffic|stale|unavailable|partial`.
- `MetricsSnapshot(Instant generatedAt, List<MetricReading> metrics)`.
- `Summary(String backendState, Integer cpuPercent, Integer memoryPercent, String diskState, Instant measuredAt, boolean stale, boolean monitoringConfigured)`; backendState = `working|problem|unknown`, diskState = `sufficient|low|unknown`.
- `Widget(String metricId, String view)`; `Dashboard(long revision, String period, List<Widget> widgets)` — вложенные records `AdminMetricsService`.

Публичные определения не содержат query/source. Только `MetricCatalog` хранит внутренние определения. `Summary` создаётся явным перечислением четырёх показателей; каталог никогда не сериализуется напрямую.

### Task 1: Закрытый источник ресурсов хоста

**Files:** Modify `docker-compose.production.monitoring.yml`, `docker-compose.monitoring.yml`, `pilot/monitoring/prometheus/prometheus.yml`, `scripts/validate-production-runtime-baseline.py`, `scripts/validate-production-perimeter.py`; Create `scripts/tests/test_cabinet_metrics_monitoring.py`; Modify `docs/deployment-production.md`.

**Interfaces:** Производит job `jitsi-node` с единственным target `node-exporter:9100`; существующий `jitsi-backend` сохраняется. Monitoring overlay задаёт backend `APP_METRICS_PROMETHEUS_BASE_URL=http://prometheus:9090`; без overlay URL пустой.

- [ ] **Step 1 — написать проверки конфигурации.** `test_exporter_has_no_host_port_socket_or_privilege` проверяет отсутствие ports/privileged/host network/socket, `cap_drop=ALL`, `no-new-privileges`, UID без root и read-only mounts. `test_only_host_collectors_enabled` проверяет `--collector.disable-defaults`, CPU/meminfo/filesystem и корневой mountpoint; `test_prometheus_has_both_scrape_jobs` проверяет два target. Переиспользовать `_python_guardrails.get_service_block/get_list_section_items` и importlib-паттерн текущих unittest; не добавлять YAML parser.

```python
exporter = get_service_block(monitoring_text, "node-exporter")
self.assertEqual(get_list_section_items(exporter, "ports"), [])
self.assertNotIn("/var/run/docker.sock", exporter)
```
- [ ] **Step 2 — получить красный результат.** `python -m unittest discover -s scripts/tests -p test_cabinet_metrics_monitoring.py`: FAIL, node-exporter/job отсутствуют.
- [ ] **Step 3 — добавить источник и guards.** Перед изменением image проверить официальный релиз и manifest digest node_exporter, закрепить один образ в overlays и инвентаре `docs/deployment-production.md`; существующий dependency-audit не расширять на образы. На Linux сначала проверить cpu/meminfo через read-only host `/proc`, filesystem через read-only системный раздел и явные `--path.procfs` / `--path.rootfs`; filesystem include только `^/$`. Не монтировать host `/sys`, если выбранные collectors его не используют. Root bind нерекурсивный; production не публикует новые порты. В dev новый exporter также закрыт, существующие debug-порты других инструментов не расширяются. Если минимальные mounts не дают реальные данные VM, остановить этот пункт с доказательством и исправить конфигурацию до UI-приёмки; не добавлять privilege как автоматический обход.
- [ ] **Step 4 — подтвердить результат.** Повторить Python-тест, `npm run prod:runtime:baseline:validate`, `python scripts/validate-production-perimeter.py`, `npm run stack:versions:validate`. В изолированном Linux кандидате сопоставить exporter CPU/RAM и `statfs` системного раздела с данными VM; записать target UP, hostname namespace и отсутствие опубликованного 9100 в `docker inspect`. Windows/Docker Desktop сам по себе не доказывает статистику production VM.
- [ ] **Step 5 — коммит.** `Keep host resource collection private and attributable to the VM`; `Not-tested` отражает Linux-проверку, если её ещё нельзя выполнить.

### Task 2: Каталог, клиент Prometheus и ограниченные snapshots

**Files:** Create `J/shared/observability/MetricCatalog.java`, `J/shared/observability/PrometheusMetricsClient.java`, `J/shared/observability/ServerMetricsService.java`; Modify `backend/src/main/resources/application.yml`; Create `T/shared/observability/ServerMetricsServiceTest.java`, `T/shared/observability/PrometheusMetricsClientTest.java`.

**Interfaces:** `MetricCatalog.descriptors(): List<MetricDescriptor>`, `MetricCatalog.require(String id): MetricCatalog.Definition` (внутренняя запись источника/запроса); `ServerMetricsService.catalog(): List<MetricDescriptor>`, `summary(): Summary`, `query(List<String> ids, MetricPeriod period): MetricsSnapshot`. Внутренние методы клиента: `queryInstant(String fixedQuery, Instant evaluationTime): CompletableFuture<JsonNode>` и `queryRange(String fixedQuery, Instant start, Instant end, long stepSeconds): CompletableFuture<JsonNode>`, Jackson 3 JsonNode. Получают строки только из Definitions, не HTTP-ввод. Shared-код не импортирует domains/security.

| id | Фиксированная семантика / единица |
| --- | --- |
| `host.cpu` | `100 * (1 - avg(rate(node_cpu_seconds_total{job="jitsi-node",mode="idle"}[5m])))`; % |
| `host.memory` | `100 * (1 - node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)` для `jitsi-node`; % |
| `host.disk` | `100 * (1 - node_filesystem_avail_bytes / node_filesystem_size_bytes)` для `jitsi-node`, mountpoint `/`; % |
| `backend.available` | `up{job="jitsi-backend"}`; состояние 0/1, только доступность scrape |
| `backend.join-ready` | `jitsi_service_join_readiness_ready{job="jitsi-backend"}`; готовность выдачи JWT 0/1 |
| `jvm.heap` | Сумма `jvm_memory_used_bytes{job="jitsi-backend",area="heap"}`; bytes использованной heap |
| `jdbc.pool` | `100 * sum(hikaricp_connections_active) / sum(hikaricp_connections_max)` для `jitsi-backend`; % |
| `jwt.issued` | `60 * sum(rate(jitsi_join_success_total{job="jitsi-backend"}[5m]))`; успешных выдач / мин |
| `jwt.error-ratio` | `100 * sum(rate(jitsi_join_failure_total[5m])) / sum(rate(jitsi_join_attempts_total[5m]))` для `jitsi-backend`; %, без трафика `no_traffic` |
| `jwt.latency-p95` | `1000 * histogram_quantile(0.95, sum by (le)(rate(jitsi_join_latency_seconds_bucket{job="jitsi-backend",result="success"}[5m])))`; мс, без успешных выдач `no_traffic` |

Числители и знаменатели относятся к одному target; недопустимый/нулевой знаменатель — `no_data`, не ноль. Имена реально экспортируемых series сверить в candidate до закрепления запросов. Каждый descriptor — SYSTEM, безопасная подпись и разрешённые виды; текущие источники имеют один target.

- [ ] **Step 1 — написать содержательные тесты.** `summaryIsAnExplicitRoundedProjection`: CPU 63.1 → 65, RAM 42.2 → 40, диск 90 → low; сериализованный Summary не содержит `instance`, `query`, bytes или hostname. `sourceTimestampControlsFreshness`: sample старше 90 с → stale при свежем timestamp ответа; up=0 → unavailable для ресурсов. `boundsAndCachePreventFanout`: 13 id/дубликаты/неизвестный id/неизвестный период отклоняются до сети; 7d даёт ≤300 точек; 20 одновременных summary используют один refresh, активных HTTP ≤4, history-cache ≤60. `noTrafficAndBadValuesAreNotHealthyZero`: пустота, NaN/Inf, лишние ряды, counter reset и warnings имеют правильное состояние. HTTP-тест через JDK HttpServer: 302 не посещается, >1 MiB отклоняется до JSON, остановка тела после headers завершается в 2 с и возвращает permit.

```java
assertThat(service.summary().cpuPercent()).isEqualTo(65);
assertThat(service.summary().memoryPercent()).isEqualTo(40);
assertThat(service.summary().diskState()).isEqualTo("low");
```
- [ ] **Step 2 — получить красный результат.** Из `backend`: `.\gradlew.bat test --tests '*ServerMetricsServiceTest' --tests '*PrometheusMetricsClientTest'`; FAIL: контракт отсутствует.
- [ ] **Step 3 — реализовать минимум.** Использовать JDK HttpClient и `BodyHandlers.limiting(BodyHandlers.ofByteArray(), 1_048_576)`, Jackson 3; ограничить connect/request/полное чтение тела и отменять незавершённый future. Конфигурация `app.metrics.prometheus-base-url: ${APP_METRICS_PROMETHEUS_BASE_URL:}`: пустая отключает мониторинг, непустая допускает только настроенный HTTP(S) origin без userinfo/query/fragment; не следует редиректам. Semaphore 4 с ограниченным ожиданием без безграничной очереди. TTL-кэши используют фиксированные id/period и single-flight; 60 history entries с удалением старейшей. Для range `step=max(15, ceil(durationSeconds/299))`. Проверять up и `timestamp` исходных series до агрегирования; metadata cache не делает sample свежим. Текущие DTO содержат только конечные числа и безопасные псевдонимы. Summary никогда не возвращает unrounded cache object. Добавить `ponytail:` комментарий о кэше на один backend и переходе к общему кэшу только при нескольких репликах.
- [ ] **Step 4 — подтвердить результат.** Повторить два теста плюс `.\gradlew.bat test --tests '*BackendArchitectureTest' --tests '*BackendModulithVerificationTest'`; PASS. Подтвердить API ограничения по [Prometheus HTTP API](https://prometheus.io/docs/prometheus/latest/querying/api/) и штатный byte limit по [Java 25 BodyHandlers](https://docs.oracle.com/en/java/javase/25/docs/api/java.net.http/java/net/http/HttpResponse.BodyHandlers.html#limiting(java.net.http.HttpResponse.BodyHandler,long)).
- [ ] **Step 5 — коммит.** `Bound metric queries before serving dashboard data`.

### Task 3: Безопасная сводка во всех кабинетах

**Files:** Create `J/domains/health/api/SystemStatisticsController.java`, `F/lib/domains/statistics/index.ts`, `F/lib/domains/statistics/statistics.service.ts`, `F/lib/domains/statistics/types.ts`, `F/lib/domains/statistics/SystemStatistics.tsx`; Modify `J/security/SecurityConfig.java`, `F/routes/layout.tsx`, `F/__tests__/layout-shell.runtime.test.ts`, `docs/access-control.md`; Create `T/domains/health/api/SystemStatisticsSecurityIntegrationTest.java`, `F/__tests__/system-statistics.runtime.test.ts`.

**Interfaces:** Backend `GET /api/v1/system/statistics` → `Summary`; controller `@RequestMapping(value="/system/statistics", version="v1")`. Frontend `fetchSystemStatistics(context: ServerRequestContext|string, signal?: AbortSignal): Promise<Summary>`; `SystemStatistics({initial: Summary|null, isAdmin: boolean})`. Как в fetchJoinReadiness, string означает настроенный публичный API base; браузер использует `VITE_API_URL` или `/api/v1`, credentials include, без передачи Cookie вручную. Общий root loader `useSystemStatistics` возвращает только Summary или null; внутренний API_URL не сериализуется. Новых proxy routes и изменений Nginx нет.

- [ ] **Step 1 — написать тесты.** Security: anonymous 401, все пять портальных ролей 200 и точный safe JSON schema, `Cache-Control` private/no-store. Layout: authenticated routes `/`, `/profile`, `/rooms`, `/meetings`, `/admin` содержат один блок, auth/invite ветки не выполняют metrics fetch. Timeout loader через fake timer 1000 мс возвращает null и не блокирует Slot. Poll hidden → 0 вызовов, visible → один вызов за 60 с; уничтожение компонента отменяет timer/request; 401 очищает показания и останавливает poll. Fake подробный объект не попадает в rendered HTML/Qwik — фронт-схема отвергает лишние поля.

```typescript
expect(pollingFetch).not.toHaveBeenCalled(); // скрытая вкладка
expect(serializedSummary).not.toContain("instance");
expect(serializedSummary).not.toContain("query");
```
- [ ] **Step 2 — получить красный результат.** `.\gradlew.bat test --tests '*SystemStatisticsSecurityIntegrationTest'` в backend; `npm --prefix frontend-qwik test -- src/__tests__/system-statistics.runtime.test.ts src/__tests__/layout-shell.runtime.test.ts`: FAIL, отсутствует endpoint/блок.
- [ ] **Step 3 — реализовать вертикальный сценарий.** Добавить точный authenticated matcher; health controller отдаёт `ServerMetricsService.summary()` с no-store и общими состояниями без исключения источника. Root loader проверяет user и public auth/invite route до запроса, передаёт `AbortSignal.timeout(1000)` существующему fetchWithTimeout; timeout не превращается в redirect или отказ страницы. Один компонент расположен над Slot только в authenticated ветке. Четыре карточки с текстовыми состояниями, приблизительным `%`, временем, кнопкой обновления; `Нет данных` вместо нулей. Poll обращается к существующему публичному API, в production через уже настроенный `/api/v1` reverse proxy; backend URL/cookie не сериализуются. Для локального разных origin использовать существующие CORS/credential настройки; ссылка `/admin/metrics` только при hasPlatformAdminAccess.
- [ ] **Step 4 — подтвердить результат.** Повторить тесты, `npm --prefix frontend-qwik run verify:architecture` и build.types; PASS. В tests проверить UTC metadata + локализованное отображение времени, тёмную тему, отсутствие color-only состояния, клавиатурную кнопку. Обновить access-control только для нового API.
- [ ] **Step 5 — коммит.** `Show a safe server summary without blocking personal cabinets`.

### Task 4: Admin API и личные настройки дашборда

**Files:** Create `J/domains/admin/service/AdminMetricsService.java`, `J/domains/admin/api/AdminMetricsController.java`, `backend/src/main/resources/db/migration/V26__Create_metric_dashboard_preferences.sql`, `T/domains/admin/api/AdminMetricsSecurityIntegrationTest.java`, `T/domains/admin/service/AdminMetricsServiceTest.java`, `T/domains/admin/service/MetricDashboardPostgresIntegrationTest.java`; Modify `J/security/SecurityConfig.java`, `T/security/SecurityConfigRouteMatrixTest.java`, `backend/src/main/resources/db/migration/CHANGELOG.md`, `docs/access-control.md`.

**Interfaces:** `AdminMetricsService.catalog(): List<MetricDescriptor>`, `query(List<String> ids,String period): MetricsSnapshot`, `loadDashboard(String tenantId,String subjectId): Dashboard`, `saveDashboard(String tenantId,String subjectId,Dashboard input): Dashboard`. Controller root `/admin/metrics`, version v1: GET catalog/query/dashboard, PUT dashboard. Использовать ServerMetricsService из задачи 2; tenant — TenantAccessGuard, subject — проверенный `sub` из OAuth2User, не displayName и не поля тела.

- [ ] **Step 1 — написать тесты.** Route matrix для корня, trailing slash, catalog/dashboard: anonymous 401, participant и три operational роли 403, admin 200. PUT без CSRF 403. `preferencesAreOwnedByPrincipal`: совпадающий sub в разных tenants и разные sub в одном tenant имеют отдельные строки. `concurrentFirstSaveHasOneWinner`: два PUT с revision=0 дают успех и 409; последующие одинаковые revision также дают один успех. `boundedPreferencesRejectUnknownFields`: 13 виджетов, дубликат, неизвестный id/view/period, поля tenantId/subjectId/query и chunked body >16 KiB отвергаются до DB/Prometheus. Catalog JSON не содержит source/query. PostgreSQL-проверка подтверждает JSONB и уникальный ключ, а не только H2 поведение.

```java
mvc.perform(get("/api/v1/admin/metrics/catalog").with(oidcLogin()
    .authorities(new SimpleGrantedAuthority("ROLE_support-engineer"))))
    .andExpect(status().isForbidden());
```
- [ ] **Step 2 — получить красный результат.** `.\gradlew.bat test --tests '*AdminMetricsSecurityIntegrationTest' --tests '*AdminMetricsServiceTest' --tests '*SecurityConfigRouteMatrixTest'`: FAIL, endpoints/table отсутствуют.
- [ ] **Step 3 — реализовать API и persistence.** Сначала recheck максимального Flyway номера; V26 используется, если по-прежнему свободен. Таблица `metric_dashboard_preferences(tenant_id text, subject_id text, revision bigint, layout jsonb, updated_at timestamptz)`, PK `(tenant_id,subject_id)`, revision≥1, layout object. В AdminMetricsService использовать уже предоставленный Spring JDBC `JdbcClient` без нового порта/адаптера/Entity: SELECT по обоим ключам; первая запись `INSERT ... ON CONFLICT DO NOTHING`, обновление `UPDATE ... WHERE tenant_id=? AND subject_id=? AND revision=?`; affectedRows=0 →409. Транзакция не делает HTTP-запросов. Без строки GET возвращает revision=0 и начальный набор `host.cpu,host.memory,host.disk,jvm.heap,jdbc.pool,jwt.latency-p95`, period=1h; сохранённый пустой набор остаётся пустым. PUT читает не более 16_385 байт до JSON, проверяет только `revision,period,widgets` и поля widget `metricId,view`; нарушения →400, размер →413. Использовать существующий ProblemResponseFacade, не отдавать SQL/Prometheus detail. Строгие matchers exact-root + `/api/v1/admin/metrics/**` ставятся до широкого admin GET matcher; они используют существующий PortalRole.
- [ ] **Step 4 — подтвердить результат.** Повторить тесты и архитектурные gates; PASS. `MetricDashboardPostgresIntegrationTest` использует существующий `PostgresRedisContainerIntegrationTestSupport`, @Tag("container"); запуск `.\gradlew.bat testContainer --tests '*MetricDashboardPostgresIntegrationTest'` проверяет два конкурентных первых INSERT, CAS UPDATE, JSONB и Flyway на изолированной схеме. При недоступном Docker skipped не считается PostgreSQL PASS. Проверить no-store headers и то, что no-monitoring не мешает load/saveDashboard. Удалённый из будущего каталога id игнорируется при чтении с безопасным предупреждением; не принимается при новом сохранении.
- [ ] **Step 5 — коммит.** `Keep detailed metrics and dashboard preferences scoped to their owners`.

### Task 5: Выбор метрик и графики в админке

**Files:** Create `F/lib/domains/admin/admin-metrics.service.ts`, `F/lib/domains/admin/admin-metrics.types.ts`, `F/lib/domains/admin/components/AdminMetricsDashboard.tsx`, `F/lib/domains/admin/components/MetricChart.tsx`, `F/routes/admin/metrics/index.tsx`; Modify `F/lib/domains/admin/index.ts`, `F/lib/domains/admin/admin-layout.route-helpers.ts`, `F/__tests__/admin-layout.route.runtime.test.ts`; Create `F/__tests__/admin-metrics.runtime.test.ts`, `F/__tests__/metric-chart.test.ts`.

**Interfaces:** `fetchMetricsCatalog(context: ServerRequestContext): Promise<MetricDescriptor[]>`, `fetchMetrics(context: ServerRequestContext|string,ids: string[],period: string): Promise<MetricsSnapshot>`, `fetchMetricDashboard(context: ServerRequestContext): Promise<Dashboard>`, `saveMetricDashboard(context: MutationRequestContext,input: Dashboard): Promise<Dashboard>`; context types из shared. `MetricChart({series: MetricSeries[],unit:string,title:string})`; `AdminMetricsDashboard({catalog,dashboard,snapshot})`. Poll идёт к публичному backend GET `/api/v1/admin/metrics`, credentials include; backend проверяет роль на каждом запросе, frontend проверяет hasPlatformAdminAccess до fetch. Сохранение — Qwik routeAction с существующей CSRF-обвязкой.

- [ ] **Step 1 — написать тесты.** Loader для non-admin не вызывает подробные API и redirect на `/`; в SSR нет detailed fixtures. Каталог дополнительной метрики появляется в выборе без новой ветки компонента; добавление/удаление/кнопки порядка/смена period сохраняют формат Dashboard. Пустой набор → empty-state и 0 query calls. 409 оставляет локальные изменения, предлагает перечитать, не выполняет silent overwrite. Chart: null создаёт разрыв, 300 точек укладываются в SVG viewBox, один sample отображает точку, одинаковые значения/нулевой диапазон/нечисловые входы не дают NaN path. Hidden tab/401 останавливают poll, повторный visible не создаёт второй timer. Навигация «Метрики» видна только admin, текущие environment/return links других разделов сохраняются.

```typescript
expect(metricsFetch).not.toHaveBeenCalled(); // loader non-admin
expect(renderedChart).not.toContain("NaN");
expect(renderedChart).not.toContain("Infinity");
```
- [ ] **Step 2 — получить красный результат.** `npm --prefix frontend-qwik test -- src/__tests__/admin-metrics.runtime.test.ts src/__tests__/metric-chart.test.ts src/__tests__/admin-layout.route.runtime.test.ts`: FAIL, новый route/компонент отсутствует.
- [ ] **Step 3 — реализовать интерфейс.** Новый пункт в «Обзор и диагностика», строгое frontend guard перед любым metrics fetch. Все route imports через public domain index. Нативные select/search/button для каталога, period и порядка; максимум 12 виджетов. SVG без библиотеки, aria-label и текстовое значение/единица рядом; фиксированные безопасные подписи, gaps отдельными path. Пустота/неполнота/отказ одного виджета не удаляют остальные. 60-секундный visible poll и ручное обновление; при сохранении invalid-CSRF/auth ошибки переходят в существующий auth recovery. Подписать системную область текущего развёртывания; environment из URL не передавать в Prometheus query. При новой неизвестной карточке из будущего каталога применяется общий renderer, не специальная ветка id.
- [ ] **Step 4 — подтвердить результат.** Повторить тесты; `npm --prefix frontend-qwik run build`, `npm --prefix frontend-qwik run verify:architecture`: PASS. В браузере admin добавляет показатель, меняет порядок, сохраняет и снова входит; participant/operational роли получают только общий блок. Проверить узкий viewport, клавиатуру, тёмную тему и Network/SSR без приватных labels. Если корпоративная browser policy блокирует тест, зафиксировать пробел, не обходить ограничение и не заявлять browser acceptance.
- [ ] **Step 5 — коммит.** `Let administrators compose a bounded dashboard from reviewed metrics`.

### Task 6: Контракты, полный gate и кандидат выпуска

**Files:** Regenerate `openapi.generated.json` and `F/lib/shared/api/generated/api-types.ts`; Modify `docs/deployment-production.md`; Create `docs/cabinet-metrics.md` с каталогом, правилами расширения, закреплёнными версией/digest node_exporter и инструкцией проверки/отката.

**Interfaces:** Кандидат включает backend, frontend, migration и monitoring overlay одного SHA; полная приёмка не зависит от запущенной Grafana. Существующий механизм deployment используется без нового release runner.

- [ ] **Step 1 — проверить контрактный gate до генерации.** `npm run contracts:check` должен обнаружить новые endpoints/DTO в несинхронизированных generated files. Не добавлять тесты, проверяющие только текст документации.
- [ ] **Step 2 — завершить API и инструкции.** `npm run openapi:generate`, `npm run frontend:api-types:generate`; документация показывает добавление определения/теста/collector, затем выбор админом, без возможности раскрыть его всем настройкой. Записать semantics JWT, source freshness, режим monitoring off, глобальную область, ограничения и последующие этапы Grafana/alerts/JVB.
- [ ] **Step 3 — выполнить финальную проверку.** `npm run verify`, `npm run frontend:verify:ssr`, `git diff --check`; PASS. В Linux candidate проверить target UP, сравнение ресурсов VM, реальные endpoint role matrix, сохранение/revision conflict, остановку source и скорость кабинетов. Повторить только gates, затронутые последующими исправлениями. Внешняя WebRTC-проверка не подменяется этими метриками.
- [ ] **Step 4 — подготовить конкретный выпуск и откат.** Записать SHA, image digests, Compose config и сохранённый предыдущий release; перед изменением DB нужен проверенный backup. Новая таблица additive: при возврате прежнего backend/frontend её сохранить, не удалять данные и не откатывать Flyway destructive SQL. Monitoring collector можно отключить возвратом предыдущего overlay; кабинеты остаются доступными. После явно разрешённого выпуска проверить HTTPS, роли, PostgreSQL prefs, fresh samples, no-public-port и render; до этого не обозначать изменения внедрёнными.
- [ ] **Step 5 — итоговый коммит и review.** `Make the metric rollout verifiable and recoverable`; review всего diff по spec, особенно raw data leaks, casing authority, CAS races, body timeout и host namespace. Отчёт отделяет локальный gate, Linux candidate, браузер и production proof.

## Проверка полноты и порядок выполнения

Порядок: 1 → 2 → 3 → 4 → 5 → 6. Задача 3 даёт законченный общий блок; задачи 4–5 дают законченную настройку admin. Shared контракты и security изменения связаны, поэтому для этого плана рекомендуется Native: один исполнитель сохраняет контекст, отдельный итоговый review проверяет всю ветку.

Сверка spec: сбор/сеть — 1; каталог/расширение/лимиты/свежесть — 2; все кабинеты/общая приватность — 3; admin role/CSRF/ownership/revision — 4; добавление виджетов/история/доступность UI — 5; OpenAPI/gates/выпуск/откат — 6. Пункты Review Focus закреплены тестами в соответствующих задачах. В этом плановом этапе тесты приложения не запускались, продуктовый код не изменялся.
