# Dev Examples

Этот каталог хранит учебные и migration-only примеры вне `src/routes`, чтобы они не попадали в production route graph Qwik City.

Canonical references для Qwik patterns:

- `component$`: используется по всему production UI, например в `src/routes/index.tsx`.
- `routeLoader$`: production loaders находятся в `src/routes/index.tsx`, `src/routes/layout.tsx`, `src/routes/auth/index.tsx`.
- `routeAction$` + `Form` + `zod$`: production actions находятся в `src/routes/index.tsx`, `src/routes/invite/[inviteToken]/index.tsx`, `src/routes/rooms/route-handlers.ts`, `src/routes/meetings/route-handlers.ts`.
- `server$`: isolated non-route example хранится в `src/dev-examples/server-function-example.tsx`.