# Исправления полного ревью от 28.09.2026

> Исполнение по разрешению пользователя «займись правками всех выявленных проблем». Сохранить модульный монолит. Применяются systematic-debugging, test-driven-development и verification-before-completion. Независимые области поручаются native subagents согласно AGENTS.md; общая интеграция и ревью у ведущего агента.

**Цель:** закрыть R01–R16 из X:/LLM/jitsi-mgorka/review-2026-09-28/code-review.md и проверить соседние сценарии. Внешние production-действия, коммит и деплой в этот этап не входят.
**База:** 77013b7, ветка codex/review-fixes-2026-09-28.
**Ограничения:** нет новых product dependencies без необходимости и отдельного разрешения; использовать уже имеющиеся Java/Spring/PostgreSQL/Qwik/Vitest/JUnit/stdlib. Не менять действующие секреты и рабочую production-БД. Новые схемы — новыми Flyway-миграциями, не редактированием старых. Сохранить tenant isolation, CSRF и проверку JWT. Не сокращать проверки доступа ради простоты.
**Проверка соседних сценариев:** cancel/update, close/create, несколько config rollouts, чужой tenant/subject, повтор после commit, поздний media response, timeout/reject, повторный logout, runtime vs bootstrap DB role, восстановление audit без дублей.

- [x] A — данные (R01/R02/R10): config_sets lifecycle и unique invariant; защита конкурентных room/meeting updates; upcoming query без N+1 и без проглатывания отказов БД.
  Область: backend domains/configsets (кроме audit listener/log), domains/meetings/rooms (CRUD/entity/repository/upcoming; audit files исключены), соответствующие тесты. Миграция V22: только singleton lock. V23 не понадобилась: V14 уже исправляет индекс; исходная формулировка R01 уточнена.
  Проверки: активация второй конфигурации и rollback; stale cancel/update и room close/update; 100 upcoming одной комнаты с ограниченным числом SQL/порт-вызовов; ошибки хранилища не дают пустой список.
- [x] B — frontend (R06/R07/R08/R09/R15): отдельная граница public assets, media lifecycle, SSR/browser task, single-flight join/retry, Request options/deadline, контролируемое обновление lockfiles.
  Область: frontend-qwik/**. Без изменений root package.json/verify scripts (координировать команды).
  Проверки: HTTP 404 для server bundle; поздний stream stopped; permission denial сохранён; concurrent retries bounded; реальный localhost abort; build/types/lint/tests/SSR. Аудит обоих dependency trees.
- [x] C — эксплуатация (R04/R12/R14/R16): минимальные runtime DB roles, честный static KV/manual rotation контракт без неподдерживаемого dynamic fallback, trusted Vault digest, запуск существующих security/deployment tests в общем CI.
  Область: deploy/**, pilot/**, docker-compose*, .env*example, scripts/**, root package.json, документация deploy. Не менять backend Java/frontend исходники.
  Проверки: fresh bootstrap roles/permissions и безопасная migration процедура для существующих installations, validators+unit scripts, полная работа существующих guardrails. Pin Vault digest из независимого trusted источника.
- [x] D — auth/session (R03/R05): штатный OIDC backchannel и revocation acceptance; subject-scoped refresh revoke; безопасная реакция reuse, cleanup.
  Область: security/**, domains/auth/**, соответствующие тесты. Миграция V24 зарезервирована этому пакету. Согласовать backchannel URL в nginx и realm с пакетом C.
  Сначала проверить реальные потребители refresh. Сохранить функцию при наличии явно опубликованного API; закрыть дефекты владения и family lifecycle вместо неподтверждённого удаления контракта.
- [x] E — идемпотентность (R11): один проверяемый контракт для повтора и scoped key; устранить split commit между DB и marker.
  Область: infrastructure/idempotency/**, соответствующие тесты, миграция V25. Совместить транзакцию с mutating use case; не сохранять plaintext секреты/JWT в response cache. Допустим документированный duplicate guard вместо response replay, если fail-closed не допускает повторного эффекта.
- [x] F — audit recovery (R13): retry существующего Modulith registry, идемпотентная DB-запись audit, cleanup завершённых публикаций.
  Область: audit listeners/logs/entities/repositories configsets/meetings/auth, config/**. Новая миграция не нужна: используется существующий Modulith registry. Сначала проверить настоящую семантику registry/transactions, не создавать новый outbox.
- [x] G — соседние замечания и заключительное ревью: проверить diff по цепочкам, исправить найденные дефекты, сгенерировать OpenAPI/types при изменении контракта, выполнить полный repository gate и доступные integration проверки.
- [x] H — отчёт о результатах: таблица R01–R16 (исправлено/проверено/ограничения), новые замечания с аргументами, точные команды и результаты; без заявления о production validation.

Критерий завершения: каждый R имеет конкретное исправление либо проверенное опровержение; соответствующие проверки проходят; остальные проверки не имеют неразобранных ошибок. Если инфраструктура среды блокирует часть интеграции, воспроизвести доступным изолированным способом и прямо указать оставшуюся границу.


Проверено отдельно: PostgreSQL 12 lifecycle/refresh 18 из 18 и migration regressions 2 из 2; audit recovery 4 из 4, bulk/idempotency и auth targeted проходят после исправления H2 fixture. Контракты OpenAPI/types сгенерированы. Общий npm run verify и финальный SSR HTTP smoke прошли. Итог, уточнение R01 и ограничения Docker/production отражены в docs/review-remediation-2026-09-28.md. Временная PostgreSQL остановлена; исходный checkout чистый, коммитов и деплоя нет.
