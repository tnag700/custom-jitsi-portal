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
- `.env.example` - обязательные переменные окружения для docker-compose.

## Быстрый старт

1. Подготовьте окружение:
   - скопируйте `.env.example` в `.env`;
   - заполните значения для `SPRING_DATASOURCE_PASSWORD`, `POSTGRES_PASSWORD`, `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD`.
2. Frontend dev-режим без контейнеров (из корня):
   - `npm run frontend:install`
   - `npm run frontend:dev`
3. Локальная production-like проверка frontend (из корня):
   - `npm run frontend:build`
   - `npm run frontend:start`
4. Полная локальная среда в контейнерах (из корня):
   - `npm run prod:up`
5. Полная локальная среда + monitoring overlay (из корня):
   - `npm run prod:up:monitoring`
6. Проверка dev-конфигурации перед пересборкой стека:
   - `npm run stack:validate`

Важно: Postgres-данные в Docker Compose сохраняются в volume `pgdata`. Если раньше среда запускалась с некорректным маппингом volume и после перезапуска пропадали профили или другие данные, пересоздайте stack после исправления compose-конфигурации, чтобы Postgres начал писать в `/var/lib/postgresql/data`.

Важно: `npm run prod:up` и `npm run prod:up:monitoring` теперь сначала валидируют dev-конфигурацию. Проверка останавливает запуск, если в `pilot/keycloak/realm/jitsi-dev-realm.json` у seeded users нет явных `id` или если в `docker-compose.yml` снова появится некорректный Postgres volume mount.

Docker Compose теперь следует воспринимать как production-like сценарий локальной проверки: frontend и backend собираются из исходников внутри Docker, без требования заранее готовить локальные `dist/` или `build/libs/` артефакты.

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

### Root-скрипты observability

- `npm run observability:alerting:validate` - валидация Prometheus и Alertmanager артефактов.
- `npm run observability:drill` - одиночный synthetic drill для backend и alerting-контура.
- `npm run observability:drill:extended` - расширенный drill с несколькими traffic cycles.
- `npm run observability:alerting:smoke` - smoke-проверка полного firing/resolved цикла для alerting.

## Текущее состояние

Проект находится в активной экспериментальной разработке. Структура модулей, API и инфраструктурные сценарии продолжают эволюционировать, поэтому обратная совместимость между коммитами не гарантируется.
