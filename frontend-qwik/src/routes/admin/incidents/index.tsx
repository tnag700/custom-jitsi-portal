import { component$ } from "@qwik.dev/core";
import { routeLoader$, useLocation, type DocumentHead } from "@qwik.dev/router";
import {
  AdminDashboardServiceError,
  buildAdminQueryHref as buildQueryHref,
  buildAdminSecondaryHref,
  buildIncidentDetailHref,
  buildIncidentQueueDerivedState,
  buildIncidentQueueFacetQueryUpdates,
  buildIncidentQueueFilters,
  buildIncidentQueueResetFiltersQueryUpdates,
  buildIncidentQueueReturnHref,
  buildIncidentQueueViewQueryUpdates,
  fetchAdminIncidents,
  hasIncidentSearchQuery,
  normalizeNonNegativeAdminInteger as normalizeNonNegativeInteger,
  normalizePositiveAdminInteger as normalizePositiveInteger,
  resolveIncidentRelativeTimeLabel,
  searchAdminIncidents,
  type AdminDashboardErrorPayload,
  type AdminIncidentList,
  type AdminIncidentSearch,
  type IncidentQueueFilters,
} from "~/lib/domains/admin";
import { resolveAuthRecoveryRedirectPath } from "~/lib/domains/auth";
import { ApiErrorAlert, RequestStatePanel } from "~/lib/shared";
import { buildServerRequestContext } from "~/lib/shared/routes/server-handlers";

interface IncidentsLoaderData {
  incidents: AdminIncidentList | null;
  searchResult: AdminIncidentSearch | null;
  loadError: AdminDashboardErrorPayload | null;
  filters: IncidentQueueFilters;
}

const ENVIRONMENT_OPTIONS = ["", "dev", "test", "prod"] as const;
const PERIOD_OPTIONS = ["15m", "1h", "24h"] as const;

export const useAdminIncidents = routeLoader$(async ({ sharedMap, cookie, query, url, redirect }) => {
  const requestContext = buildServerRequestContext({ sharedMap, cookie });
  const filters = buildIncidentQueueFilters(query);
  const returnTo = `${url.pathname}${url.search}`;
  const limit = normalizePositiveInteger(filters.limit, 50);
  const offset = normalizeNonNegativeInteger(filters.offset, 0);

  try {
    const incidents = await fetchAdminIncidents(requestContext, {
      period: filters.period,
      environment: filters.environment,
      view: filters.view || undefined,
      facet: filters.facet || undefined,
      roomId: filters.roomId || undefined,
      meetingId: filters.meetingId || undefined,
      subjectId: filters.subjectId || undefined,
      errorCode: filters.errorCode || undefined,
      category: filters.category || undefined,
      severity: filters.severity || undefined,
      limit,
      offset,
    });
    const queueState = buildIncidentQueueDerivedState(incidents, filters);

    const hasSearch = hasIncidentSearchQuery({
      traceId: filters.traceId,
      requestId: filters.requestId,
      errorCode: filters.errorCode,
      from: filters.from,
      to: filters.to,
      meetingId: filters.meetingId,
    });
    const searchResult = hasSearch
      ? await searchAdminIncidents(requestContext, {
          environment: filters.environment,
          traceId: filters.traceId || undefined,
          requestId: filters.requestId || undefined,
          errorCode: filters.errorCode || undefined,
          from: filters.from || undefined,
          to: filters.to || undefined,
          meetingId: filters.meetingId || undefined,
        })
      : null;

    if (searchResult?.outcome === "exact-match" && searchResult.incidentId) {
      throw redirect(
        302,
        buildIncidentDetailHref(
          url,
          searchResult.incidentId,
          queueState.effectiveEnvironment,
          buildIncidentQueueReturnHref(url),
        ),
      );
    }

    return {
      incidents,
      searchResult,
      loadError: null,
      filters,
    } satisfies IncidentsLoaderData;
  } catch (error) {
    if (error instanceof AdminDashboardServiceError) {
      if (error.payload.errorCode === "AUTH_REQUIRED") {
        throw redirect(302, resolveAuthRecoveryRedirectPath(error, returnTo));
      }
      if (error.payload.errorCode === "ACCESS_DENIED") {
        throw redirect(302, "/");
      }
      return {
        incidents: null,
        searchResult: null,
        loadError: error.payload,
        filters,
      } satisfies IncidentsLoaderData;
    }
    throw error;
  }
});

export default component$(() => {
  const loader = useAdminIncidents();
  const location = useLocation();
  const { incidents, searchResult, loadError, filters } = loader.value;

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

  if (!incidents) {
    return <RequestStatePanel title="Нет инцидентов" detail="Сервис не вернул список инцидентов." />;
  }

  const queueState = buildIncidentQueueDerivedState(incidents, filters);
  const secondaryModuleLinks = [
    {
      label: "История ролей",
      href: buildAdminSecondaryHref(location.url, "/admin/role-history", queueState.effectiveEnvironment),
    },
    {
      label: "Конфиг-наборы",
      href: buildAdminSecondaryHref(location.url, "/admin/config-sets", queueState.effectiveEnvironment),
    },
  ];

  return (
    <div class="space-y-6">
      <section class="rounded-3xl border border-border bg-surface px-6 py-5 shadow-sm">
        <div class="flex flex-col gap-6">
          <div>
            <p class="text-xs uppercase tracking-[0.22em] text-muted">Очередь инцидентов</p>
            <h2 class="mt-2 text-xl font-semibold text-text">Оперативная очередь инцидентов входа</h2>
            <p class="mt-2 max-w-3xl text-sm text-muted">
              Очередь начинается с сохранённых представлений оператора и быстрых фасетов. Точный поиск по traceId и requestId
              доступен как дополнительный инструмент и не заменяет основной сценарий разбора.
            </p>
          </div>
          <div class="grid gap-4 xl:grid-cols-[minmax(0,2fr)_minmax(20rem,1fr)]">
            <div class="rounded-3xl border border-border bg-bg/60 p-4">
              <div class="flex flex-wrap items-center gap-2">
                <span class="rounded-full border border-border px-3 py-1 text-xs font-medium uppercase tracking-[0.2em] text-muted">
                  Активное представление
                </span>
                <span class="rounded-full bg-text px-3 py-1 text-sm font-medium text-bg">{queueState.activeViewLabel}</span>
                {queueState.activeFacetLabel ? (
                  <span class="rounded-full border border-border px-3 py-1 text-sm text-text">Фасет: {queueState.activeFacetLabel}</span>
                ) : null}
                <span class="rounded-full border border-border px-3 py-1 text-sm text-muted">
                  Сортировка: {incidents.sort.label}
                </span>
              </div>
              <div class="mt-4 grid gap-3 md:grid-cols-2">
                <div class="rounded-2xl border border-border bg-surface p-4">
                  <p class="text-xs uppercase tracking-[0.2em] text-muted">Сохранённые представления</p>
                  <div class="mt-3 flex flex-wrap gap-2">
                    {incidents.availableViews.map((view) => (
                      <a
                        key={view.token}
                        href={buildQueryHref(location.url, buildIncidentQueueViewQueryUpdates(filters, view.token))}
                        class={view.token === incidents.selectedView
                          ? "rounded-full bg-text px-3 py-2 text-sm font-medium text-bg"
                          : "rounded-full border border-border px-3 py-2 text-sm text-text"}
                      >
                        {view.label}
                      </a>
                    ))}
                  </div>
                  <p class="mt-3 text-sm text-muted">
                    {incidents.availableViews.find((view) => view.token === incidents.selectedView)?.summary}
                  </p>
                </div>
                <div class="rounded-2xl border border-border bg-surface p-4">
                  <p class="text-xs uppercase tracking-[0.2em] text-muted">Быстрые фасеты</p>
                  <div class="mt-3 flex flex-wrap gap-2">
                    {incidents.quickFacets.map((facet) => (
                      <a
                        key={facet.token}
                        href={buildQueryHref(
                          location.url,
                          buildIncidentQueueFacetQueryUpdates(
                            filters,
                            incidents.selectedView,
                            incidents.selectedQuickFacet,
                            facet.token,
                          ),
                        )}
                        class={facet.active
                          ? "rounded-full border border-text bg-bg px-3 py-2 text-sm font-medium text-text"
                          : "rounded-full border border-border px-3 py-2 text-sm text-text"}
                      >
                        {facet.label} <span class="text-muted">{facet.count}</span>
                      </a>
                    ))}
                  </div>
                  <p class="mt-3 text-sm text-muted">
                    Быстрые фасеты дополняют активное представление и не заменяют работу с очередью через расширенные фильтры.
                  </p>
                </div>
              </div>
            </div>

            <div class="rounded-3xl border border-border bg-bg/60 p-4">
              <p class="text-xs uppercase tracking-[0.2em] text-muted">Сводка по очереди</p>
              <div class="mt-3 grid gap-3 sm:grid-cols-3 xl:grid-cols-1">
                <div class="rounded-2xl border border-border bg-surface p-4">
                  <p class="text-xs text-muted">Всего в очереди</p>
                  <p class="mt-2 text-2xl font-semibold text-text">{incidents.totalElements}</p>
                </div>
                <div class="rounded-2xl border border-border bg-surface p-4">
                  <p class="text-xs text-muted">Окружение</p>
                  <p class="mt-2 text-lg font-medium text-text">{queueState.selectedEnvironment || "all"}</p>
                </div>
                <div class="rounded-2xl border border-border bg-surface p-4">
                  <p class="text-xs text-muted">Окно наблюдения</p>
                  <p class="mt-2 text-lg font-medium text-text">{incidents.period}</p>
                </div>
              </div>
            </div>
          </div>

          <details class="rounded-3xl border border-border bg-bg/50 p-4" open={queueState.advancedFiltersOpen}>
            <summary class="cursor-pointer list-none text-sm font-medium text-text">
              Расширенные фильтры и точный поиск
            </summary>
            <p class="mt-2 text-sm text-muted">
              Поиск по trace/request и уточняющие поля остаются дополнительным инструментом поверх выбранного представления оператора.
            </p>
            <form method="get" class="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              <input type="hidden" name="view" value={incidents.selectedView} />
              <input type="hidden" name="facet" value={incidents.selectedQuickFacet ?? ""} />
              <input type="hidden" name="limit" value={filters.limit} />
              <input type="hidden" name="offset" value="0" />
              <label class="flex flex-col gap-1 text-sm text-muted">
                Период
                <select class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" name="period" value={filters.period}>
                  {PERIOD_OPTIONS.map((period) => (
                    <option key={period} value={period}>{period}</option>
                  ))}
                </select>
              </label>
              <label class="flex flex-col gap-1 text-sm text-muted">
                Окружение
                <select class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" name="environment" value={filters.environment}>
                  {ENVIRONMENT_OPTIONS.map((environment) => (
                    <option key={environment || "all"} value={environment}>{environment || "все"}</option>
                  ))}
                </select>
              </label>
              <label class="flex flex-col gap-1 text-sm text-muted">
                Категория
                <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" name="category" value={filters.category} />
              </label>
              <label class="flex flex-col gap-1 text-sm text-muted">
                ID комнаты
                <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" name="roomId" value={filters.roomId} />
              </label>
              <label class="flex flex-col gap-1 text-sm text-muted">
                ID встречи
                <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" name="meetingId" value={filters.meetingId} />
              </label>
              <label class="flex flex-col gap-1 text-sm text-muted">
                ID субъекта / пользователя
                <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" name="subjectId" value={filters.subjectId} />
              </label>
              <label class="flex flex-col gap-1 text-sm text-muted">
                Trace ID
                <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" name="traceId" value={filters.traceId} />
              </label>
              <label class="flex flex-col gap-1 text-sm text-muted">
                Request ID
                <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" name="requestId" value={filters.requestId} />
              </label>
              <label class="flex flex-col gap-1 text-sm text-muted">
                Код ошибки
                <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" name="errorCode" value={filters.errorCode} />
              </label>
              <label class="flex flex-col gap-1 text-sm text-muted">
                Severity
                <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" name="severity" value={filters.severity} />
              </label>
              <label class="flex flex-col gap-1 text-sm text-muted">
                С
                <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" name="from" value={filters.from} />
              </label>
              <label class="flex flex-col gap-1 text-sm text-muted">
                По
                <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" name="to" value={filters.to} />
              </label>
              <div class="sm:col-span-2 xl:col-span-3 flex flex-wrap gap-2">
                <button type="submit" class="rounded-2xl border border-text bg-text px-4 py-2 text-sm font-medium text-bg">
                  Применить фильтры
                </button>
                <a
                  href={buildQueryHref(
                    location.url,
                    buildIncidentQueueResetFiltersQueryUpdates(
                      filters,
                      incidents.selectedView,
                      incidents.selectedQuickFacet,
                    ),
                  )}
                  class="rounded-2xl border border-border px-4 py-2 text-sm text-text"
                >
                  Сбросить фильтры
                </a>
              </div>
            </form>
          </details>
        </div>
      </section>

      {searchResult ? (
        <section class="rounded-3xl border border-border bg-surface p-5 shadow-sm">
          <h3 class="text-lg font-semibold text-text">Результат поиска</h3>
          <p class="mt-2 text-sm text-muted">Исход: {searchResult.outcome}</p>
          {searchResult.message ? <p class="mt-2 text-sm text-muted">{searchResult.message}</p> : null}
          {searchResult.candidates.length > 0 ? (
            <div class="mt-4 space-y-2">
              {searchResult.candidates.map((candidate) => (
                <a
                  key={candidate.incidentId}
                  href={buildIncidentDetailHref(location.url, candidate.incidentId, queueState.effectiveEnvironment)}
                  class="block rounded-2xl border border-border bg-bg px-4 py-3 text-sm text-text"
                >
                  <div class="flex items-center justify-between gap-3">
                    <span>{candidate.errorCode}</span>
                    <span class="text-muted">{candidate.occurredAt}</span>
                  </div>
                </a>
              ))}
            </div>
          ) : null}
        </section>
      ) : null}

      <section class="rounded-3xl border border-border bg-surface p-5 shadow-sm">
        <div class="flex items-center justify-between gap-3">
          <h3 class="text-lg font-semibold text-text">Список инцидентов</h3>
          <p class="text-sm text-muted">{incidents.totalElements} инцидентов</p>
        </div>
        {incidents.items.length > 0 ? (
          <div class="mt-4 space-y-3">
            {incidents.items.map((incident) => (
              <a
                key={incident.incidentId}
                href={buildIncidentDetailHref(
                  location.url,
                  incident.incidentId,
                  queueState.effectiveEnvironment,
                )}
                class="block rounded-2xl border border-border bg-bg px-4 py-4"
              >
                <div class="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                  <div class="space-y-2">
                    <div class="flex flex-wrap items-center gap-2">
                      <span class="rounded-full border border-border px-2 py-1 text-xs font-medium uppercase tracking-[0.16em] text-muted">
                        {incident.severity}
                      </span>
                      <p class="text-sm font-medium text-text">{incident.errorCode} / {incident.category}</p>
                    </div>
                    <p class="text-sm text-text">{incident.affectedEntitySummary}</p>
                    <p class="text-xs text-muted">{resolveIncidentRelativeTimeLabel(incident.freshnessHint)}</p>
                  </div>
                  <div class="text-left text-xs text-muted lg:text-right">
                    <p>{incident.occurredAt}</p>
                    <p class="mt-2 text-sm font-medium text-text">Открыть карточку инцидента</p>
                  </div>
                </div>
              </a>
            ))}
          </div>
        ) : (
          <RequestStatePanel
            title="Очередь пуста для выбранного view"
            detail={`Представление ${queueState.activeViewLabel}${queueState.activeFacetLabel ? `, фасет ${queueState.activeFacetLabel}` : ""} не содержит инцидентов в текущем окне.`}
          />
        )}
      </section>

      <section class="rounded-3xl border border-dashed border-border bg-bg/60 p-5 shadow-sm">
        <p class="text-xs uppercase tracking-[0.22em] text-muted">Вторичные модули</p>
        <h3 class="mt-2 text-lg font-semibold text-text">Дополнительные административные разделы</h3>
        <p class="mt-2 text-sm text-muted">
          История ролей и конфиг-наборы сохраняют контекст по окружению и пути возврата, но не занимают место основных элементов очереди.
        </p>
        <div class="mt-4 flex flex-wrap gap-2">
          {secondaryModuleLinks.map((link) => (
            <a key={link.href} href={link.href} class="rounded-full border border-border bg-surface px-3 py-2 text-sm text-text transition-colors hover:bg-surface-alt">
              {link.label}
            </a>
          ))}
        </div>
      </section>
    </div>
  );
});

export const head: DocumentHead = {
  title: "Инциденты администратора — Jitsi Portal",
  meta: [
    {
      name: "description",
      content: "Административная очередь инцидентов для поиска, фильтрации и перехода к карточке инцидента.",
    },
  ],
};