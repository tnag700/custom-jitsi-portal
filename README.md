# ВНИМАНИЕ: это эксперимент по работе ИИ-агентов, возможен "ИИ слоп"; ответственный за код слоператор не является профессиональным программистом.

## Jitsi видеоконференция с веб-порталом

Монорепозиторий для разработки собственного портала вокруг сценариев видеоконференций на базе Jitsi:
- backend API на Spring Boot 4;
- frontend на Qwik City с SSR;
- локальная инфраструктура и интеграции через Docker Compose;
- контрактно-ориентированная разработка с OpenAPI, архитектурными правилами и observability.

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
- Observability: Spring Boot Actuator, OpenTelemetry, JDBC/Redis tracing.
- Architecture governance: ArchUnit, PMD, CPD.
- Local environment: Docker Compose.

## Структура репозитория

- `backend/` - REST API, доменные модули, безопасность, миграции, архитектурные и интеграционные тесты.
- `frontend-qwik/` - SSR-веб-клиент, маршруты, shared API client, UI и frontend-тесты.
- `docs/` - проектная и архитектурная документация.
- `_bmad-output/` - артефакты планирования и реализации.
- `openapi.yaml` - канонический контракт API.
- `openapi.generated.json` - повторяемо сгенерированный runtime-снимок OpenAPI.
- `docker-compose.yml` - локальный запуск frontend, backend, Keycloak, Postgres, Redis и Jitsi stack.
- `.env.example` - обязательные переменные окружения для docker-compose.

## Быстрый старт

1. Подготовьте окружение:
   - скопируйте `.env.example` в `.env`;
   - заполните значения для `SPRING_DATASOURCE_PASSWORD`, `POSTGRES_PASSWORD`, `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD`.
2. Backend (`backend/`):
   - `./gradlew.bat build`
   - `./gradlew.bat test`
3. Frontend (`frontend-qwik/`):
   - `npm install`
   - `npm run dev`
4. Полная локальная среда (из корня):
   - `docker compose up --build`

## Основные адреса локальной среды

- Frontend: `http://localhost:3000`
- Backend API: `http://localhost:8080/api/v1`
- Actuator health: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8082`
- Keycloak: `http://localhost:8081`
- Jitsi Web: `https://localhost:8443`
- Jitsi Web HTTP: `http://localhost:8000`

## Команды разработки

### Backend

- `./gradlew.bat build` - полная сборка backend.
- `./gradlew.bat test` - весь backend test suite.
- `./gradlew.bat testSmoke` - быстрые тесты без integration tag.
- `./gradlew.bat testUnit` - unit-only тесты.
- `./gradlew.bat testSlice` - slice-тесты Spring.
- `./gradlew.bat testIntegration` - integration тесты без container tag.
- `./gradlew.bat testContainer` - container-backed тесты.
- `./gradlew.bat generateOpenApiSpec` - генерация runtime OpenAPI snapshot.

### Frontend и контракт

- `npm run openapi:generate` - генерация `openapi.generated.json` из backend runtime-контракта.
- `npm run openapi:check` - проверка дрейфа OpenAPI артефакта.
- `npm --prefix frontend-qwik run generate:api` - генерация frontend API types.
- `npm run frontend:api-types:check` - проверка актуальности frontend API types.
- `npm --prefix frontend-qwik run lint`
- `npm --prefix frontend-qwik run test`
- `npm --prefix frontend-qwik run test:coverage`
- `npm --prefix frontend-qwik run verify:architecture`

## Quality gates

- Backend:
  - JUnit 5 тесты разных уровней;
  - JaCoCo coverage report;
  - PMD blocking profile и CPD duplicate detection;
  - ArchUnit правила на слои и доменные границы;
  - контрактные тесты для OpenAPI и Problem Details.
- Frontend:
  - ESLint и архитектурные lint-проверки;
  - Vitest;
  - типобезопасная генерация API-клиента из OpenAPI.

## Текущее состояние

Проект находится в активной экспериментальной разработке. Структура модулей, API и инфраструктурные сценарии продолжают эволюционировать, поэтому обратная совместимость между коммитами не гарантируется.
