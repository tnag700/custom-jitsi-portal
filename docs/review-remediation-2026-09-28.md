# Исправления ревью от 28 сентября 2026 года

База: `77013b7`. Рабочая ветка: `codex/review-fixes-2026-09-28`.
Подтверждённые замечания R01–R16 исправлены в отдельной ветке; ошибочная часть исходного R01 отозвана. Дополнительные подтверждённые дефекты также исправлены. Production не изменялся.
Исходный отчёт: `X:/LLM/jitsi-mgorka/review-2026-09-28/code-review.md`.

## Решения и обоснование

| Пункт | Что изменено | Почему именно так |
|---|---|---|
| R01 — переходы конфигураций | Update, activation, deactivation, rollout и rollback блокируют существующую строку координации, перечитывают актуальные данные; прежняя ACTIVE-конфигурация сбрасывается и flush выполняется до активации новой. Новая миграция V22 добавляет только строку блокировки. | Реальный дефект связан с порядком Hibernate flush и конкурентными изменениями. **Исходное утверждение о неисправленном индексе было ошибочным: Java-миграция V14 уже исправляет V12.** Повторная замена индекса не нужна. Глобальная блокировка оправдана редкими административными изменениями; её предел и возможный переход к блокировкам по окружению отмечены в коде. |
| R02 — устаревшие room/meeting snapshots | Внутри транзакции use case берут pessimistic lock и перечитывают текущую сущность; create/update встречи согласованы с блокировкой комнаты. | Повторная проверка состояния после ожидания блокировки не позволяет старому запросу восстановить отменённую встречу или закрытую комнату. Проверка только объекта, ранее полученного контроллером, не защищала от гонки. |
| R03 — отзыв OIDC-сессии | Подключены штатные Spring Security OIDC session registry и backchannel logout; внутренний callback использует фиксированный loopback и фактический порт сервера. Добавлены настройки realm, nginx и миграция существующего клиента Keycloak. | Нативная реализация проверяет подпись и обязательные claims logout token, связывает `sid`/`sub` с локальными сессиями. Фиксированный callback не зависит от присланного Host/Forwarded и не требует выхода через внешний proxy. |
| R04 — права PostgreSQL | Bootstrap-администраторы отделены от `jitsi_app`/`keycloak_app`; runtime-роли имеют собственную схему, но не SUPERUSER/CREATEDB/CREATEROLE/REPLICATION/BYPASSRLS и не владеют БД. Добавлены guarded SQL migration и инструкция для существующих volumes. | Flyway/Liquibase нужны права на DDL своей схемы, но административные права кластера им не нужны. Простая смена env на существующем volume не меняет пользователей SQL, поэтому необходим отдельный проверяемый переход. |
| R05 — refresh lifecycle | Отзыв проверяет subject и не создаёт неизвестные записи. Ротация сохраняет family_id; reuse любого USED-предка и явный logout отзывают всё семейство. DB использует блокировку корня, memory — существующий монитор, Redis — атомарный Lua. USED-записи хранятся до абсолютного срока. V24 отзывает старые состояния и сдвигает persisted cutoff. | Отзыв одного предка оставлял действующим его потомка. Удаление tombstone по idle expiry позволяло потерять доказательство reuse. Семейство закрывает оба разрыва; cutoff запрещает повторную регистрацию старого подписанного JWT, даже если его ещё не было в хранилище. |
| R06 — SSR-файлы | Серверная сборка перемещена из public `dist` в `server/build`, Docker-копирование скорректировано; старый `/server` закрыт. | Граница файловой системы надёжнее фильтра по расширениям: static middleware вообще не видит server bundle. HTTP smoke проверяет 404 приватных путей и 200 клиентского chunk. |
| R07 — камера и микрофон | Поздний результат getUserMedia освобождает все tracks, даже после timeout; отказ в разрешениях не маскируется timeout-веткой. | Timeout прекращает ожидание, но не отменяет браузерный запрос устройства. Очистка привязана к фактическому завершению запроса, иначе камера могла остаться включённой. |
| R08 — join/retry | Browser preflight перенесён в document-ready lifecycle; общий синхронный guard покрывает join и retry, сохраняется meetingId для повтора, блокировка снимается в finally. | Один вход в поток устраняет двойное получение токенов/создание iframe при быстрых кликах; браузерные API больше не вызываются в SSR lifecycle. |
| R09 — HTTP | Общий `fetchWithTimeout` объединяет caller signal и deadline 10 секунд; адаптер сохраняет стандартные Request options. Обновлены прямые backend-fetch вызовы; безусловный response.clone удалён. | Проверка отмены только в одном клиенте оставляла admin/CSRF/logout/readiness без срока. Общий helper ограничивает все эти ожидания, а удаление clone исключает лишнюю непрочитанную ветку потока ответа. |
| R10 — ближайшие встречи | Фильтр актуальных встреч выполняется в SQL; данные комнат читаются пакетом. Ошибка хранилища больше не преобразуется в пустой список. | Убираются round trips на каждый элемент, а отказ БД отличается от корректного отсутствия встреч. На сценарии со 100 карточками измерено два Hibernate statement. |
| R11 — идемпотентность | Redis marker заменён таблицей PostgreSQL V25: marker и изменение данных входят в одну транзакцию. Ключ ограничен subject, HTTP method и URI, хранится SHA-256. Повтор возвращает 409, response/токены не кешируются. | Одна БД устраняет окно «данные committed, Redis не обновлён». Для текущего API достаточно duplicate guard; кеширование response потребовало бы хранить выданные секреты. Контракт частичного успеха bulk invitations и записанного неуспешного rollout сохранён явно. |
| R12 — Vault | Зафиксирован фактический контракт: legacy mount `database/` — static KV v1 с ручной ротацией. Удалён неподдерживаемый dynamic fallback; bridge публикуется атомарно, старый контракт отклоняется. | Название пути не превращает KV в database secrets engine. Автоматическая ротация без полного lease lifecycle была бы ложной гарантией. Существующий механизм сохранён и документирован честно. |
| R13 — восстановление аудита | Используется имеющийся Modulith event_publication: повтор незавершённых публикаций старше пяти минут, фиксация audit row и completion в одной DB-транзакции, удаление completed publications старше 30 дней. | Новая очередь/outbox не нужна. Общая транзакция закрывает окно дублирования между audit commit и отдельным native completion; pending записи при уборке сохраняются. |
| R14 — общий gate | В `npm run verify` включены существующие Python эксплуатационные тесты и Node-тест egress proxy через `test:operations`. | Регрессионные проверки уже есть; достаточно действительно запускать их в используемой точке входа CI. |
| R15 — зависимости | Обновлены существующие зависимости и lockfiles, включая Vitest 4.1.11, sharp и уязвимые транзитивные пакеты; чистая установка не требует force/legacy-peer-deps. | Устраняются конкретные advisory без необязательного перевода Qwik/Vite на новые major. Override Vitest согласован с фактической поддержкой Qwik и пояснён в README. |
| R16 — доверие к Vault | SHA-256 Vault 1.21.4 закреплён из официального источника отдельно от зеркала, с которого загружается архив. | Архив и checksum с одного недоверенного зеркала не дают независимой проверки. Теперь подмена зеркала не позволяет одновременно подменить ожидаемый digest. |

## Дополнительные дефекты, найденные в ходе исправлений

- **SQL-скрипты сообщали успех при ошибке.** `psql \quit 3` в проверенной версии завершался с кодом 0. Отказы заменены на SQL exception под `ON_ERROR_STOP`; десять ветвей проверены по ненулевому коду. Это важно для deploy guard: текст ошибки при exit 0 не останавливает автоматизацию.
- **Join мог остаться заблокированным после ошибки readiness.** Ошибка теперь проходит через общий finally; исходная ошибка action не заменяется сообщением о неверном формате ответа. Иначе пользователь лишался работающего retry и полезной диагностики.
- **Просроченный по idle USED-предок обходил отзыв потомков.** Проверка USED выполняется до idle expiry. Абсолютно истёкшие JWT всё равно отвергаются parser; семейство к этому моменту также истекло.
- **Неизвестный/повреждённый Redis state принимался слишком мягко.** Нет перехода в in-memory режим или восстановления ACTIVE из неполной записи. Неизвестные истёкшие состояния не записываются.
- **Коллизия successor могла выглядеть как reuse или частично менять состояние.** Теперь она выбрасывает отдельную внутреннюю ошибку до изменения обоих токенов; существующий владелец и текущий токен сохраняются.
- **JWT decoder error классифицировался по подстроке `exp`.** `unexpected` ошибочно попадал в «сессия истекла». Проверка сужена до сообщений об expiry; добавлена регрессия.
- **Общая транзакция могла нарушить bulk partial success.** Для результата с корректными и ошибочными строками сохраняются корректные приглашения и marker; повтор даёт 409. Если вложенная транзакция уже помечена rollback-only, исходная ошибка сохраняется, а не превращается в HTTP 500 из-за UnexpectedRollbackException. Дефект обнаружен независимым ревью самого исправления и воспроизведён до правки.

## Проверка

Окончательный `npm run verify` завершился успешно. Серверные проверки выполнялись на отдельной копии этих же исходников, без замены работающих образов и без подключения к production-базам.

| Проверка | Результат |
|---|---|
| Backend `test` | Локально и на сервере под Linux/JDK 25: 844 PASS, 0 failures; 66 SKIP из 910 обнаруженных тестов. Из пропусков 18 PostgreSQL-тестов выполнены отдельно на целевом PostgreSQL 18; часть Docker-тестов остаётся непрогнанной. |
| Architecture + quality | 21 архитектурная проверка PASS; блокирующий PMD — 0 нарушений; CPD gate PASS. |
| PostgreSQL 12, отдельная disposable БД | 18/18 PASS: config lifecycle/rollout 6, refresh family/expiry/races 12. Каждая test class использует свою схему с очисткой после закрытия context. |
| Миграция V21 → V24 | 2/2 PASS на H2: пустое и заполненное хранилище, отзыв исторических записей и повышение cutoff. |
| Native backchannel HTTP | 4/4 PASS: signed sid/sub logout, повтор, неверные issuer/audience/signature, malformed token. Тест создаёт OIDC-сессию локально; полный redirect-login через настоящий Keycloak не выполнялся. |
| Audit recovery | 4/4 PASS: fault после SQL flush для configsets/meetings/auth, rollback, native retry без дубля, сохранение pending при cleanup. Вызван scheduled entrypoint; ожидание реальных пяти минут не требуется. |
| Idempotency | 10/10 unit-тестов PASS с настоящими H2-транзакциями; bulk partial-success HTTP regression также PASS. |
| Frontend | 77 файлов, 568 тестов PASS; production build, typecheck, lint и architecture boundaries PASS. |
| SSR HTTP | PASS: закрытые серверные пути, клиентский chunk, SSR/resumability. Дополнительно проверялся запуск из изолированного runtime с production dependencies. |
| Operations | 31 Python + 1 Node regression PASS; dev/prod/stack baseline validators PASS. |
| PostgreSQL runtime privileges | Изолированный PG12 SQL proof PASS: runtime DDL, отказ повышенных операций, смена владельцев существующих объектов, ошибочные guard-ветви с ненулевым exit. На сервере fresh entrypoint и миграция существующего volume на PostgreSQL 18 PASS в disposable контейнере. |
| PostgreSQL 18, сервер | 18/18 PASS на временной БД: config lifecycle/rollout 6, refresh family/expiry/races 12; production PostgreSQL не использовался. |
| Redis 7, сервер | Семь сценариев точных Lua-тел из `RedisRefreshTokenStore.java` PASS на временном Redis 7 без опубликованного порта: replay предка, owner-only revoke, collision, expiry, legacy/missing family, срок жизни и параллельная ротация. Java/Testcontainers интеграция с Redis отдельно не запускалась. |
| Dependency audit | Frontend и server lockfile: 0 известных advisory по двум выполненным `npm audit`. |
| Гигиена изменений | `git diff --check` PASS; изменённые текстовые файлы валидны UTF-8, shell-файлы имеют LF; staged-файлов нет. Исходный checkout остался чистым на 77013b7. Временная PostgreSQL остановлена. |

Расширенный **advisory** PMD-профиль содержит 2259 эвристических предупреждений и требует отдельной приоритизации; это не 2259 подтверждённых дефектов. Настройки правил не ослаблялись. Итоговый gate использует существующий блокирующий профиль.

Логи и машинная сводка: `X:/LLM/jitsi-mgorka/review-2026-09-28/remediation-verify.log`, `remediation-postgres-tests.log`, `remediation-ssr.log`, `remediation-results.json`. Серверные доказательства: `server-backend-test-results/` (910 тестов), `postgres18-tests.log`, `database-roles-pg18.log`, `redis7.log`. Первоначальные RED-запуски сохранены отдельно; их нельзя путать с итоговым PASS.

Команды воспроизведения из корня worktree (Node 24.18.0 и JDK 25):

```powershell
npm run openapi:generate
npm run frontend:api-types:generate
npm run verify
npm run frontend:verify:ssr
npm --prefix frontend-qwik audit --json
npm --prefix frontend-qwik/server audit --json
```

Дополнительные проверки из `backend/`:

```powershell
.\gradlew.bat test --tests '*OidcBackchannelIntegrationTest' --tests '*AuditRecoveryIntegrationTest'
# Только с JITSI_TEST_POSTGRES_URL/USER/PASSWORD, указывающими на отдельную disposable БД:
.\gradlew.bat testContainer --tests '*ConfigSetPostgresLifecycleIntegrationTest' --tests '*RefreshTokenStoreNativePostgresIntegrationTest'
# На машине с работающим Docker:
.\gradlew.bat testContainer
# Из корня проекта, с POSIX shell и Docker:
npm run test:database-roles
```

## Основные изменённые файлы

- Данные: `domains/configsets` repositories/usecases, `domains/rooms` repositories/usecases, `domains/meetings` repositories/usecases/upcoming; V22 и регрессии lifecycle/query count.
- Аутентификация: [SecurityConfig.java](../backend/src/main/java/com/acme/jitsi/security/SecurityConfig.java), [DatabaseRefreshTokenStore.java](../backend/src/main/java/com/acme/jitsi/domains/auth/infrastructure/DatabaseRefreshTokenStore.java), соседние memory/Redis stores, service/parser/controller; V24, native logout и family tests.
- Транзакции: [IdempotencyAspect.java](../backend/src/main/java/com/acme/jitsi/infrastructure/idempotency/IdempotencyAspect.java), V25, [DurableAuditAspect.java](../backend/src/main/java/com/acme/jitsi/infrastructure/audit/DurableAuditAspect.java), AuditPublicationMaintenance, listeners и fault-injection tests.
- Frontend: [client.ts](../frontend-qwik/src/lib/shared/api/client.ts), backend API services, join-page/preflight, Express entry/Dockerfile, lockfiles и runtime tests.
- Эксплуатация: compose, nginx/Keycloak realm, `deploy/postgres`, Vault bootstrap/bridge/checksum, validators, `scripts/verify-repository.mjs`, SQL migration scripts; [инструкция смены DB-ролей](database-runtime-roles.md).

## Эксплуатационные границы

- Production не изменялся. На работающем сервере `/healthz`, Jitsi `/config.js` и Keycloak OIDC metadata вернули HTTP 200 с корректным TLS, а основные контейнеры остались healthy. Это проверка текущего релиза, а не новых образов. Нет утверждения о проверенной нагрузочной ёмкости, качестве реального звонка или фактическом принудительном logout через установленный Keycloak.
- V24 намеренно требует новой refresh-сессии через SSO: восстановить родственные связи исторических токенов достоверно нельзя. Двухэтапная процедура согласования cutoff с существующим startup guard описана в `docs/deployment-production.md`.
- Для существующих баз runtime-роли переводятся по `docs/database-runtime-roles.md`; новый init-скрипт срабатывает только на пустом volume. Нужен проверенный backup/restore и окно обслуживания, а не удаление volumes.
- OIDC registry соответствует текущей одной backend instance с локальными HTTP sessions. Горизонтальное масштабирование потребует общей session infrastructure; оно не добавлялось без такой задачи.
- Гарантия повторной записи без дублей относится к DB audit listeners configsets/meetings/auth. Остальные log-only listeners сохраняют нативную at-least-once семантику.
- Существующие требования endpoints к Idempotency-Key сохранены: configsets требует заголовок, а там, где он допускает отсутствие, действует обычный API-контракт. Повтор принятого ключа — 409, а не воспроизведение прежнего response. TTL marker — 24 часа; это не вечная дедупликация.
- Локальный Docker daemon недоступен; целевые PostgreSQL 18 и Redis 7 проверены изолированно на сервере. Java/Testcontainers интеграция Redis и оставшиеся пропущенные Docker-тесты требуют отдельного прогона перед релизом. На серверном разделе осталось около 1,7 ГБ свободного места (95% занято); перед сборкой и развёртыванием новых образов требуется освободить пространство штатной процедурой.

## Упрощения

Сохранён модульный монолит и существующие интерфейсы. Не добавлены новый брокер, второй outbox, response cache, distributed lock service или dynamic Vault lease manager. Удалены Redis из idempotency guard, неподдерживаемый dynamic secrets fallback и лишнее клонирование HTTP response. Новые продуктовые зависимости не добавлялись.
