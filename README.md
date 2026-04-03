# Custom Jitsi Portal

Монорепозиторий для портала видеоконференций на базе Jitsi.

## Что внутри

- `backend/` - Spring Boot 4 API (Java 25, Gradle, PostgreSQL, Redis, Keycloak).
- `frontend-qwik/` - SSR frontend на Qwik City.
- `scripts/` - валидаторы и операционные утилиты.
- `openapi.generated.json` - актуальный API-снимок.
- `docker-compose*.yml` - локальный и production-like запуск.

## Быстрый старт

1. Скопируйте `.env.example` в `.env`.
2. Frontend dev:
   - `npm run frontend:install`
   - `npm run frontend:dev`
3. Полный локальный стек:
   - `npm run prod:up`

## Частые команды

- `npm run frontend:build` - production build frontend.
- `npm run frontend:start` - запуск собранного frontend.
- `npm run prod:down` - остановка локального стека.
- `npm run prod:baseline:validate` - проверки baseline (perimeter/runtime/secret).
- `npm run observability:drill` - проверка observability сценария.

## Важно

- Папки `.codex/`, `.gradle-copilot-dashboard/`, `deploy/` исключены из git и не должны пушиться.
- `deploy/` может существовать локально для окружения, но не является частью репозитория.
