# Flyway Migration Changelog

Этот файл документирует SQL- и Java-миграции Flyway в проекте.

## Где находятся миграции

- SQL-миграции: `backend/src/main/resources/db/migration/`
- Java-миграции: `backend/src/main/java/db/migration/`

## Список миграций V1-V19

| Version | File | Type | Summary |
|---|---|---|---|
| V1 | `V1__Create_rooms_table.sql` | SQL | Создает таблицу `rooms` и базовые индексы. |
| V2 | `V2__Create_meetings_table.sql` | SQL | Создает таблицу `meetings` и связь с `rooms`. |
| V3 | `V3__Create_meeting_audit_events_table.sql` | SQL | Создает аудит-таблицу событий встреч. |
| V4 | `V4__Create_meeting_participant_assignments_table.sql` | SQL | Создает назначения участников встречи. |
| V5 | `V5__Add_subject_id_to_meeting_audit_events.sql` | SQL | Добавляет `subject_id` в аудит-события. |
| V6 | `V6__Create_meeting_invites_table.sql` | SQL | Создает таблицу инвайтов на встречи. |
| V7 | `V7__Restrict_meeting_invite_roles.sql` | SQL | Ограничивает допустимые роли в инвайтах. |
| V8 | `V8__Add_recipient_columns_to_meeting_invites.sql` | SQL | Добавляет recipient-колонки в инвайты. |
| V9 | `V9__Add_invite_version_column.sql` | SQL | Добавляет optimistic locking (`version`) для инвайтов. |
| V10 | `V10__Add_single_host_partial_index.java` | Java | Dialect-aware миграция single-host unique индекса для assignments. |
| V11 | `V11__Add_soft_delete_columns.sql` | SQL | Добавляет `deleted BOOLEAN` в `rooms` и `meetings`. |
| V12 | `V12__Create_config_sets_table.sql` | SQL | Создает `config_sets`, включая initial unique индекс по `(environment_type, tenant_id, status, deleted)`. |
| V13 | `V13__Create_config_set_audit_events_table.sql` | SQL | Создает аудит-таблицу для config sets. |
| V14 | `V14__Align_config_sets_active_unique_index.java` | Java | Dialect-aware выравнивание unique ограничения только для ACTIVE config sets. |
| V15 | `V15__Create_config_set_rollouts_table.sql` | SQL | Создает таблицу rollout-операций config sets. |
| V16 | `V16__Create_config_set_compatibility_checks_table.sql` | SQL | Создает таблицу compatibility checks. |
| V17 | `V17__Create_user_profiles_table.sql` | SQL | Создает таблицу user profiles. |
| V18 | `V18__Create_event_publication_table.sql` | SQL | Создает таблицу публикации modulith-событий. |
| V19 | `V19__Create_auth_audit_events_table.sql` | SQL | Создает аудит-таблицу auth/token событий. |

## V10: Add single host partial index (Java migration)

- Файл: `backend/src/main/java/db/migration/V10__Add_single_host_partial_index.java`
- Причина Java-миграции: требуется dialect-aware логика для PostgreSQL и H2.
- Что делает:
  - удаляет старый индекс `uk_meeting_single_host` при наличии;
  - очищает дубликаты host-назначений в `meeting_participant_assignments`;
  - PostgreSQL: создает partial unique index `uk_meeting_single_host` на `(meeting_id) WHERE role = 'host'`;
  - H2: создает computed column `host_meeting_id` и unique index по ней;
  - fallback для иных БД: обычный индекс по `meeting_id`.

## V14: Align config sets active unique index (Java migration)

- Файл: `backend/src/main/java/db/migration/V14__Align_config_sets_active_unique_index.java`
- Причина Java-миграции: требуется dialect-aware SQL для PostgreSQL partial index и H2 computed column.
- Что делает:
  - удаляет старые индексы `uq_config_sets_active_env_tenant` и `uq_config_sets_env_tenant_status_deleted`;
  - PostgreSQL: создает `uq_config_sets_active_env_tenant` только для `status = 'ACTIVE' AND deleted = false`;
  - H2: использует computed column `active_env_tenant_key` и unique index по ней;
  - fallback для иных БД: создает широкий unique индекс по `(environment_type, tenant_id, status, deleted)`.

## DRAFT policy for config_sets

- Исторически V12 ограничивал `DRAFT` через индекс `uq_config_sets_env_tenant_status_deleted`.
- После V14 в PostgreSQL/H2 уникальность ограничена только ACTIVE-конфигурациями.
- В fallback-ветке для иных БД (`V14__Align_config_sets_active_unique_index.java`) создается широкий индекс `uq_config_sets_env_tenant_status_deleted`; в этой ветке ограничение DRAFT может сохраняться.
- Подтвержденное бизнес-решение для текущего проекта: несколько `DRAFT` config sets в одном `tenant + environment` допустимы.
- Если в будущем потребуется правило "только 1 DRAFT", нужно добавить отдельную миграцию (например V18) с DRAFT-specific partial unique index.

## Soft-delete audit (rooms, meetings, config_sets)

Результат аудита на текущем коде:

- `RoomEntity`: `@SQLRestriction("deleted = false")` присутствует.
- `MeetingEntity`: `@SQLRestriction("deleted = false")` присутствует.
- `ConfigSetEntity`: `@SQLRestriction("deleted = false")` присутствует.
- `RoomJpaRepository`: native SQL-запросы отсутствуют.
- `MeetingJpaRepository`: native SQL-запросы отсутствуют.
- `ConfigSetJpaRepository`: native SQL-запросы отсутствуют.

Вывод: soft-delete фильтрация на JPA-уровне настроена консистентно; дополнительных правок репозиториев не требуется.
