# Jitsi portal refactoring implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task by task. Steps use checkboxes for tracking.

**Goal:** Устранить подтверждённые ошибки из [ревью 2026-09-28](../../review-2026-09-28.md), затем уменьшить измеренные задержки без смены стека.

**Architecture:** Сохранять существующие порты Spring Modulith и контракты OpenAPI. Исправлять один пользовательский или эксплуатационный сценарий за раз; для SQL сначала снять исходный план и число запросов, для мониторинга проверить поведение отказа, а для Jitsi выполнить реальный внешний media smoke.

**Tech Stack:** Java 25 / Spring Boot 4.1 / PostgreSQL 18, Qwik 2 beta / TypeScript, Jitsi, Prometheus, Gradle, Vitest.

**Spec:** [Ревью кода и узких мест](../../review-2026-09-28.md).

**Выпуск 2026-09-28:** кодовые изменения Tasks 1–5, 7 и 10, а также workflow
из Task 9 выпущены из
`aa78b3f`. Полный backend gate на Docker-хосте: 852 теста без ошибок и
пропусков; frontend gate: 556 тестов. Перед сменой room key на стенде
наблюдались ноль активных JVB-конференций и отсутствие выдачи access token
за 25 минут при TTL 20 минут. Production preflight и внутренние HTTPS smoke
прошли. Алерт Task 5 проверен остановкой backend и доставкой `firing` и
`resolved` в локальный тестовый приёмник. Task 4 остаётся частичным:
фильтры статуса комнат, встреч и инвайтов работают на текущей странице.
Task 0 частичен без SQL/SSR/JVB baseline, Task 6 ждёт измерений, Task 8 —
исследование. GitHub workflow прошёл на `c55285f` через ручной
`workflow_dispatch`; внешний WebRTC и запуск workflow на PR ещё не проверены.

**Validation:** 2026-09-28. Пункт о недолговечности meeting audit снят:
JDBC registry уже подключён. Task 8 проверяет восстановление существующего
механизма. SQL count — оценка пути; ручной GitHub gate прошёл, но событие PR
не проверено, а у `main` нет branch protection или required check.
Результаты production smoke описаны в
[ревью](../../review-2026-09-28.md).

## Общие ограничения

- Сохранить модель ролей, tenant boundaries, атомарность расхода приглашения и запрет публичного создания Jitsi-конференций.
- Не менять generated OpenAPI/TypeScript вручную. При изменении API выполнить `npm run openapi:generate` и `npm run frontend:api-types:generate`, просмотреть diff, затем `npm run contracts:check` (он тоже регенерирует файлы и сигнализирует о расхождении).
- Не добавлять зависимости и новые сервисы без подтверждённой необходимости; использовать имеющиеся API, PostgreSQL, Prometheus и тестовые инструменты.
- Не смешивать HTTP/3 UDP 443 и JVB media UDP 10000. Проверка HTTP не доказывает работоспособность медиа.
- Не применять миграцию room identity к идущей конференции без плана сохранения текущего room key.
- Production deployment, DNS, router, firewall и секреты — отдельный release gate; этот план описывает код и проверку, но не разрешает публикацию.

## Приоритет и выбор подхода

| Вариант | Результат | Решение |
| --- | --- | --- |
| Точечные исправления с регрессиями | Закрывают P1, используют существующие слои и дают небольшие проверяемые изменения | Выбран |
| Переписать backend/frontend или перейти на новый стек | Большая миграция без доказательства, что текущий стек ограничивает пользователей | Отложен до измерений |
| Сначала оптимизировать все запросы и добавить инфраструктуру | Увеличит объём до исправления ошибок изоляции и утечки памяти | Отложен; perf-срез только после baseline |

Порядок: воспроизводимый gate из **0**, затем **1/2/3/4/5**, далее **6/7**.
**8** — отдельная проверка восстановления; **9/10** независимы и могут быть
выполнены раньше. Только **6** зависит от SQL baseline из **0**; нагрузочные
замеры SSR/JVB не задерживают исправления P1. Каждый независимый сценарий —
небольшой PR или локальный change set со своей проверкой; в Task 4 UI и
backend-границу `size` отделить друг от друга. DORA рекомендует
[уменьшать размер партий](https://dora.dev/guides/dora-metrics/).

## Review focus

При реализации каждого среза проверить следующие входы в принадлежащем ему
тесте: (1) две встречи с одинаковым заголовком в разных tenants; (2) смена
заголовка при уже открытой конференции; (3) успешный и ошибочный guest exchange
при последнем использовании приглашения; (4) 21-я запись плюс невалидный номер
страницы; (5) администрирование комнат tenant с активным PROD и без DEV.
В просмотренных тестах не найдены полные регрессии этих сочетаний условий;
существующие тесты success/rollback и ошибок API следует переиспользовать.

## Task 0 — воспроизводимый baseline и метрики

**Files:** `scripts/verify-repository.mjs`, `.github/workflows/`, `docs/review-2026-09-28.md` — только если обнаружен дефект gate; конфигурацию TLS не ослаблять.

- [ ] На доверенной Linux/CI машине запустить `npm ci`, `npm --prefix frontend-qwik ci`, `npm run verify`; сохранить commit, версии runtime, время, код возврата и отчёты тестов. На Windows зафиксирован `PKIX path building failed` при загрузке Gradle 9.7. Проверить цепочку сертификатов, используемый JDK trust store и возможный proxy; исправить установленную причину или использовать проверенный Gradle distribution. Проверку TLS не отключать.
- [ ] Для `/meetings/upcoming` на тестовой БД с 1, 20, 100 и 1000 назначениями снять SQL count, p50/p95 и `EXPLAIN (ANALYZE, BUFFERS)` основных запросов. `ANALYZE` выполнять только на тестовых данных.
- [ ] Если планируется оптимизация SSR или изменение ёмкости JVB, отдельно измерить waterfall `/auth/me`, admin versions и CSRF, p95 SSR/TTFB и LCP/INP/CLS; для JVB — RTT, потери, полосу, CPU/RAM и число конференций. Эти исследования не являются предварительным условием исправления room key, очистки registry или UI-пагинации.
- [ ] Сохранить baseline и критерии сравнения в ревью; никакой оптимизации по одним статическим размерам файлов.

**Gate:** перед заявлением об ускорении — соответствующий baseline; перед выпуском — прошедший `npm run verify`. Блокер gate фиксировать отдельно, он не равен успешной проверке и не запрещает подготовить локальное исправление с доступным focused test. [SRE](https://sre.google/sre-book/monitoring-distributed-systems/) и [PostgreSQL](https://www.postgresql.org/docs/18/using-explain.html) дают метод измерения.

## Task 1 — стабильная уникальная Jitsi room identity (P1)

**Files:** `backend/src/main/java/com/acme/jitsi/domains/meetings/service/MeetingJoinPreparationHelper.java`, `MeetingAccessTokenService.java`; test `backend/src/test/java/com/acme/jitsi/domains/meetings/service/MeetingAccessTokenServiceTest.java`. Миграция только если выбран способ хранения ключа для уже созданных встреч.

- [ ] Добавить падающий тест: две встречи с одинаковым названием (в том числе в разных tenants) получают разные URL path и JWT `room`; у одной встречи переименование сохраняет тот же path и claim. Проверить совпадение значения claim с фактическим сегментом URL с учётом кодирования/регистра.
- [ ] Решить судьбу существующих активных встреч до смены алгоритма: либо назначить дату cutover после их завершения и истечения выданных JWT, либо сохранить их прежний room key в БД с явным разбором уже существующих коллизий. Выбрать один вариант по инвентарю активных встреч; не мигрировать вслепую.
- [ ] Для новых встреч получать room key из стабильного уникального ID; оставить title для отображения. URL и JWT должны использовать один и тот же вычисленный key. Удаление несвязанных повторных чтений не включать в этот срез без необходимости для корректности.
- [ ] Проверить guest и authenticated join, rename, отмену, exit; затем два клиента с разных встреч и одинаковым заголовком должны оказаться в разных Jitsi MUC. На стенде проверить токен и реальный WebRTC.

**Gate:** регрессия red/green, backend gate, отсутствие смешения встреч и разрыва активных ссылок. **Rollback:** вернуть предыдущий код только до cutover; при схеме с сохранённым ключом не удалять данные при rollback.

## Task 2 — завершение резервации приглашения (P1)

**Files:** `backend/src/main/java/com/acme/jitsi/domains/invites/service/InviteReservationCapability.java`, `InviteReservationRegistry.java`, `InviteExchangeService.java`, `InviteValidationService.java`, `../infrastructure/DbInviteValidationAdapter.java`; tests `InviteExchangeServiceTest.java`, `InviteReservationRegistryTest.java` и тест DB-mode адаптера.

- [ ] Зафиксировать тестом: после успешного обмена размер обоих in-memory registry возвращается к нулю, использованное приглашение остаётся использованным; после ошибки выпуска JWT rollback разрешён один раз и возвращает использование. Проверить конкурентную последнюю выдачу.
- [ ] Добавить в `InviteReservationCapability` операцию `complete(InviteReservation)`, которая атомарно удаляет запись без изменения usage count. В `InviteExchangeService` вызывать её после успешного выпуска; существующий rollback оставить исключительно для неуспеха.
- [ ] Убедиться, что очистка не удаляет возможность rollback до завершения выпуска и что в логах/метриках нет invite token. Не заменять бессрочную map другой бессрочной map.

**Gate:** focused tests для обоих режимов + backend gate; повторный exchange не превышает лимит приглашения. **Rollback:** прежнее поведение можно вернуть кодом без миграции данных.

## Task 3 — среда конфигурации комнат в production (P1)

**Files:** `frontend-qwik/src/routes/rooms/route-handlers.ts`, `frontend-qwik/src/lib/domains/rooms/components/RoomForm.tsx`, `docker-compose.production.yml`; tests `frontend-qwik/src/__tests__/rooms.route.runtime.test.ts`, `frontend-qwik/src/__tests__/rooms.runtime.test.ts`.

- [ ] Написать failing test: production с активным только `PROD` set загружает экран и создаёт комнату; редактирование существующей комнаты не меняет `configSetId` без явного выбора. DEV-путь остаётся доступным в dev.
- [ ] Использовать существующий источник целевой среды, если он есть в runtime-контракте. Иначе минимально добавить `PORTAL_CONFIG_ENVIRONMENT` (`DEV` локально, явный `PROD` в production Compose), валидировать допустимые значения и запрашивать соответствующий set. Не выводить среду config set только из профиля Spring: это разные понятия.
- [ ] При редактировании оставить текущий config ID доступным и выбранным: signal сейчас содержит ID комнаты, но options приходят только из DEV-loader. Проверить submit в браузере; не утверждать автоматическую подмену без воспроизведения. Запрет других сред на backend не добавлять без установленного бизнес-правила.
- [ ] Проверить реальный production-like Compose config и сценарий без DEV seed.

**Gate:** admin `/rooms` с PROD-only config загружается, редактирование без выбора не меняет set, типы и focused loader/form tests проходят.

## Task 4 — страницы комнат, встреч и приглашений (P1)

**Files:**

- Loaders: `frontend-qwik/src/routes/rooms/route-handlers.ts`, `frontend-qwik/src/routes/meetings/loaders.ts`, `frontend-qwik/src/routes/meetings/meetings-page.tsx`.
- UI: `frontend-qwik/src/lib/domains/rooms/components/RoomList.tsx`, `frontend-qwik/src/lib/domains/meetings/components/MeetingList.tsx`, `frontend-qwik/src/lib/domains/invites/components/InviteList.tsx`.
- Backend: `backend/src/main/java/com/acme/jitsi/domains/rooms/api/RoomsController.java`, `backend/src/main/java/com/acme/jitsi/domains/meetings/api/MeetingsController.java`, `backend/src/main/java/com/acme/jitsi/domains/meetings/api/MeetingInvitesController.java`, `backend/src/main/java/com/acme/jitsi/domains/configsets/api/ConfigSetsController.java`.
- Tests: `frontend-qwik/src/__tests__/rooms.route.runtime.test.ts`, `frontend-qwik/src/__tests__/meetings.route.runtime.test.ts` и соответствующие presentation/API tests.

- [ ] Тестом создать 21 запись и проверить запрос `page=1`, ссылку «следующая/предыдущая», сохранение `roomId`/`meetingId` и фильтров в URL; отрицательная/нечисловая страница должна приводить к безопасной первой странице.
- [ ] Использовать уже существующие `page/size/totalPages` в API и сервисах; выбрать URL-параметры `roomsPage`, `meetingsPage`, `invitesPage`, чтобы независимые списки не сбрасывали друг друга. Не загружать весь каталог в браузер.
- [ ] Сохранить прямой переход по `roomId` и проверить выбранный `meetingId` за пределами текущей страницы: загрузить detail существующим endpoint по ID или обеспечить явный переход на содержащую его страницу. Не выдавать отсутствие в текущем массиве за отсутствие ресурса.
- [ ] Фильтровать статус так, чтобы глобальный результат не зависел от одной произвольной страницы; при необходимости расширить серверный query по статусу с OpenAPI-регенерацией. Показать действительный total/count.
- [ ] Отдельным срезом ограничить `size` во всех четырёх list endpoints. Предлагаемый максимум — 100, UI остаётся на 20; перед фиксацией проверить callers на больший размер. Сохранить существующий fallback 20 для `size <= 0`, для превышения максимума выбрать и документировать единый ответ (предпочтительно 400), проверить 0, 20, 100 и 101. OpenAPI обновить для изменённого контракта.

**Gate:** пользователь открывает 21-ю запись и может создать встречу в комнате с поздней страницы; после reload остаётся на выбранной странице.

## Task 5 — алерт на недоступный backend и границы SLI (P1)

**Files:** `pilot/monitoring/prometheus/alert-rules.yml`, `pilot/monitoring/prometheus/prometheus.yml`, `scripts/validate-observability-alerting.py`, `docs/runbook.md`; новый fixture для `promtool test rules` рядом с правилами.

- [ ] Добавить failing rule test: при `up{job="jitsi-backend"}=0` алерт срабатывает после заданного `for`; при `up=1` не срабатывает. Проверить также отсутствие серии, если конфигурация scrape job ошибочно исчезла.
- [ ] Добавить отдельное правило для scrape failure с `up{job="jitsi-backend"} == 0` и обработкой отсутствующей серии через `absent(up{job="jitsi-backend"})`; существующий `jitsi_service_backend_available` оставить для логического состояния, переименовав его смысл/описание, если нужно. Не смешивать его с фактической доступностью процесса.
- [ ] В дашборде и runbook различить выдачу JWT и реальное подключение/медиа. Внешний тест с тремя участниками вынести в проверку JVB; он не является условием исправления scrape alert. Проверка одного `up` не обнаруживает остановку самого Prometheus.
- [ ] Проверить правило `promtool check rules`, live stop backend на dev-стенде, firing и resolved notification. [Prometheus](https://prometheus.io/docs/prometheus/latest/configuration/alerting_rules/) использует `up == 0` для недоступного target.

**Gate:** `promtool test rules` проверяет выражение и `for`, а отдельный live smoke — scrape и доставку firing/resolved уведомлений. Эти проверки не заменяют друг друга. Метрика логической совместимости продолжает работать.

## Task 6 — список ближайших встреч без N+1 (P2, после Task 0)

**Files:** `backend/src/main/java/com/acme/jitsi/domains/meetings/service/ConfiguredUpcomingMeetingsService.java`, `backend/src/main/java/com/acme/jitsi/domains/meetings/service/MeetingParticipantAssignmentRepository.java`, `backend/src/main/java/com/acme/jitsi/domains/meetings/infrastructure/JpaMeetingParticipantAssignmentRepository.java`, room adapter при необходимости пакетного чтения; test `backend/src/test/java/com/acme/jitsi/domains/meetings/service/ConfiguredUpcomingMeetingsServiceTest.java` плюс container integration test; SQL-миграция только по результату EXPLAIN.

- [ ] Зафиксировать существующий порядок, статус, окно доступности входа и fallback из `MeetingTokenProperties` тестами на 0/1/100 назначений.
- [ ] Сравнить минимальное пакетное чтение через существующие порты с projection в infrastructure; выбрать вариант по baseline и архитектурным тестам. Учесть дополнительную проверку config set в `RoomServiceMeetingRoomsAdapter`, не переносить межмодульные зависимости в domain service. Убрать запрос на каждую строку без требования ровно одного SQL.
- [ ] Сохранить текущий состав ответа, порядок и fallback. Не добавлять произвольный `LIMIT`: endpoint возвращает список, и его пагинация требует отдельного изменения API/UI. Если реальные объёмы требуют ограничения, выделить это решение и контракт отдельно.
- [ ] Добавить индекс под фактический WHERE/ORDER BY только если `EXPLAIN (ANALYZE, BUFFERS)` показывает выигрыш на репрезентативных данных; сопоставить SQL count и p95 до/после.

**Gate:** число round trips ограничено количеством пакетных выборок/чанков, а не отдельной загрузкой каждой строки; результат и порядок совпадают, SQL count и p95 сопоставлены с baseline. **Rollback:** код отдельным change set, индекс откатывать только после анализа нагрузки.

## Task 7 — административный каталог конфигураций (P2)

**Files:** `frontend-qwik/src/routes/admin/config-sets/route-handlers.ts`, `frontend-qwik/src/lib/domains/admin/components/AdminConfigSetList.tsx`, `backend/src/main/java/com/acme/jitsi/domains/configsets/api/ConfigSetsController.java` и repository только для глобальных server-side фильтров; test `frontend-qwik/src/__tests__/admin-config.route-handlers.runtime.test.ts`.

- [ ] Тест: набор на второй странице доступен; фильтр не сообщает «пусто», когда совпадение лежит на другой странице; URL страницы переживает reload.
- [ ] Использовать page metadata, вывести навигацию. Если фильтр должен искать во всём tenant-каталоге, применить его до пагинации в backend; не скачивать все страницы в SSR. При изменении API обновить OpenAPI и generated types.
- [ ] Сохранить уже работающую загрузку detail по прямому `?configSetId=` за пределами текущей страницы.

**Gate:** 21-й config set находится и открывается, фильтр глобален и UI показывает правильный total.

## Task 8 — проверка восстановления существующего аудита (исследование)

**Files:** `backend/src/main/java/com/acme/jitsi/domains/meetings/listener/MeetingAuditListener.java`, `backend/src/main/java/com/acme/jitsi/domains/meetings/infrastructure/JpaMeetingAuditLog.java`, `backend/src/main/resources/application.yml`; образец теста — `backend/src/test/java/com/acme/jitsi/domains/configsets/infrastructure/ConfigSetRolloutDurableAuditIntegrationTest.java`. Production-код менять только по воспроизведённому пробелу.

- [ ] Интеграционно подтвердить запись meeting event в существующий JDBC publication registry при бизнес-коммите и сохранение незавершённой публикации при ошибке listener. Проверить actual runtime overrides и схему V18; одного наличия `@Async` недостаточно для вывода о потере события.
- [ ] Проверить восстановление после перезапуска и через существующий API повторной доставки. В репозитории автоматический replay встреч не настроен; установить эксплуатационное требование к задержке восстановления. Не добавлять второй outbox и не переносить listeners только ради `@ApplicationModuleListener`.
- [ ] Проверить повторную доставку после уже записанного аудита, включая сбой до отметки completion. При обнаружении дублей определить идемпотентный ключ события и минимальное исправление; не обещать exactly-once по факту наличия registry.
- [ ] Только по результатам этих проверок добавить необходимую настройку retry, контроль backlog и инструкцию оператора.

**Gate:** документировано, как сохранённое событие восстанавливается, и выполнен тест повторной доставки; найденные дефекты оформлены отдельно. Исходный вывод о потере публикации снят. См. [Spring Modulith events](https://docs.spring.io/spring-modulith/reference/events.html).

## Task 9 — PR gate (P2)

**Files:** `.github/workflows/verify.yml` (new, если внешний CI не закрывает gate), `README.md`.

- [ ] Проверить внешний CI и required checks/branch protection. Если gate отсутствует, настроить на `pull_request`: Node по `engines` проекта, JDK 25, `npm ci`, frontend `npm ci`, `npm run verify`; для Docker-backed suite runner должен предоставлять Docker. Предпочесть один workflow без новых custom wrappers.
- [ ] Протестировать workflow на PR и зафиксировать время выполнения. Если полный gate слишком дорог, разделить fast/full после замера, сохранив защиту backend/frontend/contract.
- [ ] Если требуется блокировать merge при падении gate, отдельно настроить его как required check в репозитории: один workflow сам по себе этого не гарантирует.

**Gate:** нарушенный тест/контракт даёт failed check; обязательность для merge проверена отдельно.

## Task 10 — язык SSR-документа (P3)

**Files:** `frontend-qwik/src/entry.ssr.tsx`, существующий SSR test либо один небольшой render test в `frontend-qwik/src/__tests__/`.

- [ ] Изменить default `lang` с `en-us` на `ru`, сохранив override через `opts.containerAttributes`.
- [ ] Проверить обычный SSR HTML и явное переопределение языка. Мультиязычную инфраструктуру не вводить без реальных локалей.

**Gate:** русский UI по умолчанию отдаёт `lang="ru"`, override работает.

## Release verification после каждого затрагивающего production среза

1. `npm run verify`, `npm run prod:baseline:validate`, `npm run prod:preflight` на доверенном runner. Проверить `git diff --check` и отсутствие изменений секретных/operator файлов.
2. На staging подготовить релевантные тестовые данные; применять Flyway только при наличии миграции. Выполнить auth/tenant/guest smoke и регрессии затронутого сценария (page=1, room collision и т. п.).
3. При изменении room key/join/media подтвердить внешние и LAN подключения, изоляцию одинаковых заголовков, JWT `room` и ICE-пару JVB через UDP 10000. HTTP/3 UDP 443 и HTTP/2 fallback проверять при сетевых/edge-изменениях; не требовать этот прогон для правки `lang`.
4. При оптимизации сравнить соответствующие SQL count/p95, SSR p95 или Web Vitals с baseline. При изменении alerting проверить firing/resolved. Не заявлять об ускорении по diff.
5. Подготовить production cutover с backup/rollback по [deployment guide](../../deployment-production.md); выполнять только в отдельно разрешённом выпуске. Выпуск 2026-09-28 разрешён и выполнен с резервными копиями и проверкой восстановления.
