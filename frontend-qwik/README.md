# Frontend Package

Этот пакет не является основным входом для разработчика монорепозитория. Обычная работа должна идти из корня проекта через root-скрипты в [package.json](../package.json).

Канонические команды:

```shell
npm run frontend:install
npm run frontend:dev
```

Для локальной production-проверки frontend:

```shell
npm run frontend:build
npm run frontend:start
```

Для production-like контейнерного запуска всего проекта:

```shell
npm run prod:up
```

Команды в этом package.json нужны в основном как внутренний контракт frontend-пакета, для Docker build и для точечных локальных проверок.

`npm --prefix frontend-qwik run verify:architecture` из корня запускает ESLint и
проверку разрешённых TypeScript-зависимостей по `tsconfig.json`, включая относительные
импорты, реэкспорты, динамические импорты с литералом и зависимости типов.
Маршруты используют только `index.ts` доменов. Домены используют публичные shared API:
`shared/index.ts`, `shared/api/index.ts`, `shared/security/index.ts` и контракт
`shared/routes/server-handlers.ts`. Shared не зависит от доменов или маршрутов.
Meeting-сервисы принимают готовый `ServerRequestContext` или `MutationRequestContext`;
cookies, CSRF и ключ идемпотентности формируются общими обработчиками запроса.

Публичная сборка находится в `dist/`, серверная — отдельно в `server/build/`.
`npm run verify:ssr` проверяет SSR, клиентские chunks и отсутствие HTTP-доступа к серверным модулям.

Vitest закреплён на исправленной версии 4.1.11 (GHSA-82fw-gwwq-j7x9).
Override `vitest: $vitest` заменяет устаревший optional peer `<4` у Qwik beta.38;
совместимость проверяется полным `npm test`, сборкой и SSR smoke. Удалить override,
когда плановое обновление Qwik объявит поддержку Vitest 4; менять Qwik/Vite для этого security patch не требуется.

Если вам нужен общий сценарий запуска, ориентируйтесь на корневой README: [README.md](../README.md).
