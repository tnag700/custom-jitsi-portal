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
5. Локальный monitoring stack поверх основной среды (из корня):
   - `docker compose -f docker-compose.yml -f docker-compose.monitoring.yml up --build`

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
