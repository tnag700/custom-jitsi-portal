import { component$ } from "@qwik.dev/core";
import { routeLoader$, useLocation, type DocumentHead } from "@qwik.dev/router";
import {
  AdminDashboardServiceError,
  buildAdminOverviewHref as buildOverviewHref,
  buildAdminQueryHref as buildQueryHref,
  buildAdminRoleHistoryFilters,
  buildAdminRoleHistoryPageQueryUpdates,
  buildAdminRoleHistoryResetQueryUpdates,
  fetchAdminRoleHistory,
  hasAdminRoleHistoryPrimaryFilter,
  normalizeNonNegativeAdminInteger as normalizeNonNegativeInteger,
  normalizePositiveAdminInteger as normalizePositiveInteger,
  resolveIncidentReturnTo,
  type AdminDashboardErrorPayload,
  type AdminRoleHistory,
  type AdminRoleHistoryFilters,
} from "~/lib/domains/admin";
import { resolveAuthRecoveryRedirectPath } from "~/lib/domains/auth";
import { ApiErrorAlert, RequestStatePanel } from "~/lib/shared";
import { buildServerRequestContext } from "~/lib/shared/routes/server-handlers";

interface RoleHistoryLoaderData {
  history: AdminRoleHistory | null;
  loadError: AdminDashboardErrorPayload | null;
  hasPrimaryFilter: boolean;
  filters: AdminRoleHistoryFilters;
}

export const useAdminRoleHistory = routeLoader$(async ({ sharedMap, cookie, query, redirect, url }) => {
  const requestContext = buildServerRequestContext({ sharedMap, cookie });
  const filters = buildAdminRoleHistoryFilters(query);
  const returnTo = `${url.pathname}${url.search}`;

  if (!hasAdminRoleHistoryPrimaryFilter(filters)) {
    return { history: null, loadError: null, hasPrimaryFilter: false, filters } satisfies RoleHistoryLoaderData;
  }

  const page = normalizeNonNegativeInteger(filters.page, 0);
  const pageSize = normalizePositiveInteger(filters.pageSize, 20);

  try {
    const history = await fetchAdminRoleHistory(requestContext, {
      environment: filters.environment || undefined,
      q: filters.q || undefined,
      from: filters.from || undefined,
      to: filters.to || undefined,
      actionType: filters.actionType || undefined,
      role: filters.role || undefined,
      actorId: filters.actorId || undefined,
      subjectId: filters.subjectId || undefined,
      roomId: filters.roomId || undefined,
      meetingId: filters.meetingId || undefined,
      page,
      pageSize,
    });
    return { history, loadError: null, hasPrimaryFilter: true, filters } satisfies RoleHistoryLoaderData;
  } catch (error) {
    if (error instanceof AdminDashboardServiceError) {
      if (error.payload.errorCode === "AUTH_REQUIRED") {
        throw redirect(302, resolveAuthRecoveryRedirectPath(error, returnTo));
      }
      if (error.payload.errorCode === "ACCESS_DENIED") {
        throw redirect(302, "/");
      }
      return { history: null, loadError: error.payload, hasPrimaryFilter: true, filters } satisfies RoleHistoryLoaderData;
    }
    throw error;
  }
});

export default component$(() => {
  const loader = useAdminRoleHistory();
  const location = useLocation();
  const { history, loadError, hasPrimaryFilter, filters } = loader.value;
  const triageReturnHref = resolveIncidentReturnTo(location.url, filters.environment);
  const overviewHref = buildOverviewHref(filters.environment);

  if (loadError) {
    return (
      <ApiErrorAlert
        title={loadError.title}
        message={loadError.detail}
        errorCode={loadError.errorCode}
        traceId={loadError.traceId}
      />
    );
  }

  return (
    <div class="space-y-6">
      <section class="rounded-3xl border border-border bg-surface px-6 py-5 shadow-sm">
        <nav aria-label="Контекст разбора" class="flex flex-wrap items-center gap-2 text-sm text-muted">
          <a href={overviewHref} class="underline">Сводка</a>
          <span>/</span>
          <a href={triageReturnHref} class="underline">Инциденты</a>
          <span>/</span>
          <span class="text-text">Вторичный модуль</span>
        </nav>
        <a href={triageReturnHref} class="mt-3 inline-block text-sm text-muted underline">Вернуться к очереди инцидентов</a>
        <p class="mt-2 text-xs uppercase tracking-[0.22em] text-muted">Вторичный модуль</p>
        <h2 class="mt-2 text-xl font-semibold text-text">История ролей и назначений</h2>
        <form method="get" class="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {filters.returnTo ? <input type="hidden" name="returnTo" value={filters.returnTo} /> : null}
          <label class="flex flex-col gap-1 text-sm text-muted">
            Поиск пользователя
            <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" type="text" name="q" value={filters.q} placeholder="имя или ID субъекта" />
          </label>
          <label class="flex flex-col gap-1 text-sm text-muted">
            ID субъекта
            <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" type="text" name="subjectId" value={filters.subjectId} />
          </label>
          <label class="flex flex-col gap-1 text-sm text-muted">
            ID оператора
            <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" type="text" name="actorId" value={filters.actorId} />
          </label>
          <label class="flex flex-col gap-1 text-sm text-muted">
            ID комнаты
            <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" type="text" name="roomId" value={filters.roomId} />
          </label>
          <label class="flex flex-col gap-1 text-sm text-muted">
            ID встречи
            <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" type="text" name="meetingId" value={filters.meetingId} />
          </label>
          <label class="flex flex-col gap-1 text-sm text-muted">
            Окружение
            <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" type="text" name="environment" value={filters.environment} placeholder="dev/test/prod" />
          </label>
          <label class="flex flex-col gap-1 text-sm text-muted">
            Тип действия
            <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" type="text" name="actionType" value={filters.actionType} placeholder="assign/update/unassign" />
          </label>
          <label class="flex flex-col gap-1 text-sm text-muted">
            Роль
            <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" type="text" name="role" value={filters.role} placeholder="moderator" />
          </label>
          <label class="flex flex-col gap-1 text-sm text-muted">
            С
            <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" type="text" name="from" value={filters.from} placeholder="2026-03-19T09:00:00Z" />
          </label>
          <label class="flex flex-col gap-1 text-sm text-muted">
            По
            <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" type="text" name="to" value={filters.to} placeholder="2026-03-19T10:00:00Z" />
          </label>
          <input type="hidden" name="page" value="0" />
          <input type="hidden" name="pageSize" value={filters.pageSize} />
          <div class="sm:col-span-2 xl:col-span-3 flex gap-2">
            <button type="submit" class="rounded-2xl border border-text bg-text px-4 py-2 text-sm font-medium text-bg">
              Показать историю
            </button>
            <a
              href={buildQueryHref(location.url, buildAdminRoleHistoryResetQueryUpdates())}
              class="rounded-2xl border border-border px-4 py-2 text-sm text-text"
            >
              Сбросить
            </a>
          </div>
        </form>
      </section>

      {!hasPrimaryFilter ? (
        <RequestStatePanel title="Задайте фильтр" detail="Укажите поиск, ID субъекта, ID комнаты или ID встречи, чтобы загрузить ограниченную историю ролей и назначений." />
      ) : null}

      {history ? (
        <section class="rounded-3xl border border-border bg-surface p-5 shadow-sm">
          <div class="flex items-center justify-between gap-3">
            <div>
              <h3 class="text-lg font-semibold text-text">Лента событий</h3>
              <p class="text-sm text-muted">тенант: {history.tenantId} · окружение: {history.environment} · всего: {history.totalElements}</p>
            </div>
            <p class="text-sm text-muted">страница {history.page + 1} / {Math.max(history.totalPages, 1)}</p>
          </div>

          {history.content.length > 0 ? (
            <div class="mt-4 space-y-3">
              {history.content.map((entry) => (
                <article key={`${entry.occurredAt}-${entry.traceId ?? entry.meetingId ?? entry.roomId ?? entry.subjectReference ?? entry.actionType}`} class="rounded-2xl border border-border bg-bg p-4">
                  <div class="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                    <div>
                      <p class="text-sm font-medium text-text">{entry.actionLabel}</p>
                      <p class="text-xs text-muted">{entry.subjectLabel ?? "субъект не указан"}{entry.subjectReference ? ` · ${entry.subjectReference}` : ""}</p>
                      <p class="text-xs text-muted">Оператор: {entry.actorLabel ?? "не указан"}{entry.actorReference ? ` · ${entry.actorReference}` : ""}</p>
                    </div>
                    <p class="text-xs text-muted">{entry.occurredAt}</p>
                  </div>
                  <div class="mt-3 grid gap-1 text-xs text-muted sm:grid-cols-2 xl:grid-cols-4">
                      <p>Роль: {entry.oldRole ?? "не задана"} → {entry.newRole ?? "не задана"}</p>
                      <p>ID комнаты: {entry.roomId ?? "не указан"}</p>
                      <p>ID встречи: {entry.meetingId ?? "не указан"}</p>
                      <p>Trace ID: {entry.traceId ?? "не указан"}</p>
                  </div>
                </article>
              ))}
            </div>
          ) : (
            <RequestStatePanel title="История не найдена" detail="Измените фильтры или временное окно." />
          )}

          {history.totalPages > 1 ? (
            <div class="mt-4 flex gap-2">
              {history.page > 0 ? (
                <a href={buildQueryHref(location.url, buildAdminRoleHistoryPageQueryUpdates(history.page - 1))} class="rounded-2xl border border-border px-4 py-2 text-sm text-text">
                  Предыдущая страница
                </a>
              ) : null}
              {history.page + 1 < history.totalPages ? (
                <a href={buildQueryHref(location.url, buildAdminRoleHistoryPageQueryUpdates(history.page + 1))} class="rounded-2xl border border-border px-4 py-2 text-sm text-text">
                  Следующая страница
                </a>
              ) : null}
            </div>
          ) : null}
        </section>
      ) : null}
    </div>
  );
});

export const head: DocumentHead = {
  title: "История ролей — Jitsi Portal",
  meta: [
    {
      name: "description",
      content: "История ролей и назначений по пользователю, комнате и встрече внутри административной консоли.",
    },
  ],
};