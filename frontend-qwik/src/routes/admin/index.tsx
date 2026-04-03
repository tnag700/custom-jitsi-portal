import { component$ } from "@qwik.dev/core";
import { routeLoader$, useLocation, type DocumentHead } from "@qwik.dev/router";
import {
  AdminDashboardServiceError,
  buildAdminDashboardActiveIncidentsHref,
  buildAdminDashboardDerivedState,
  buildAdminDashboardFilters,
  buildAdminDashboardSelectionHref,
  buildAdminQueryHref as buildQueryHref,
  fetchAdminDashboard,
  fetchAdminDrillDown,
  resolveAdminDashboardCardTone,
  type AdminDashboardDrillDown,
  type AdminDashboardErrorPayload,
  type AdminDashboardFilters,
  type AdminDashboardSummary,
} from "~/lib/domains/admin";
import { resolveAuthRecoveryRedirectPath } from "~/lib/domains/auth";
import { ApiErrorAlert, RequestStatePanel } from "~/lib/shared";
import { buildServerRequestContext } from "~/lib/shared/routes/server-handlers";

interface DashboardLoaderData {
  dashboard: AdminDashboardSummary | null;
  drillDown: AdminDashboardDrillDown | null;
  drillDownError: AdminDashboardErrorPayload | null;
  loadError: AdminDashboardErrorPayload | null;
  filters: AdminDashboardFilters;
}

const PERIOD_OPTIONS = ["15m", "1h", "24h"] as const;
const ENVIRONMENT_OPTIONS = ["dev", "test", "prod"] as const;

export const useAdminDashboard = routeLoader$(async ({ sharedMap, cookie, query, redirect, url }) => {
  const requestContext = buildServerRequestContext({ sharedMap, cookie });
  const filters = buildAdminDashboardFilters(query);
  const returnTo = `${url.pathname}${url.search}`;
  let dashboard: AdminDashboardSummary;

  try {
    dashboard = await fetchAdminDashboard(requestContext, {
      period: filters.period,
      environment: filters.environment,
      roomId: filters.roomId || undefined,
      meetingId: filters.meetingId || undefined,
    });
  } catch (error) {
    if (error instanceof AdminDashboardServiceError) {
      if (error.payload.errorCode === "AUTH_REQUIRED") {
        throw redirect(302, resolveAuthRecoveryRedirectPath(error, returnTo));
      }
      if (error.payload.errorCode === "ACCESS_DENIED") {
        throw redirect(302, "/");
      }

      return {
        dashboard: null,
        drillDown: null,
        drillDownError: null,
        loadError: error.payload,
        filters,
      } satisfies DashboardLoaderData;
    }

    return {
      dashboard: null,
      drillDown: null,
      drillDownError: null,
      loadError: {
        title: "Ошибка загрузки",
        detail: "Не удалось загрузить административную сводку.",
        errorCode: "ADMIN_DASHBOARD_UNAVAILABLE",
      },
      filters,
    } satisfies DashboardLoaderData;
  }

  const dashboardState = buildAdminDashboardDerivedState(url, dashboard, filters);
  let drillDown: AdminDashboardDrillDown | null = null;
  let drillDownError: AdminDashboardErrorPayload | null = null;

  if (dashboardState.activeDrillDownSelection) {
    const selection = dashboardState.activeDrillDownSelection;

    try {
      drillDown = await fetchAdminDrillDown(requestContext, {
        period: selection.period,
        environment: selection.environment,
        roomId: selection.roomId || undefined,
        meetingId: selection.meetingId || undefined,
        errorCode: selection.errorCode || undefined,
        category: selection.category || undefined,
      });
    } catch (error) {
      if (error instanceof AdminDashboardServiceError) {
        if (error.payload.errorCode === "AUTH_REQUIRED") {
          throw redirect(302, resolveAuthRecoveryRedirectPath(error, returnTo));
        }
        if (error.payload.errorCode === "ACCESS_DENIED") {
          throw redirect(302, "/");
        }

        drillDownError = error.payload;
      } else {
        drillDownError = {
          title: "Ошибка детализации",
          detail: "Не удалось загрузить детализацию выбранного сигнала.",
          errorCode: "ADMIN_DASHBOARD_DRILLDOWN_UNAVAILABLE",
        };
      }
    }
  }

  return {
    dashboard,
    drillDown,
    drillDownError,
    loadError: null,
    filters,
  } satisfies DashboardLoaderData;
});

export default component$(() => {
  const loaderData = useAdminDashboard();
  const location = useLocation();
  const { dashboard, drillDown, drillDownError, loadError, filters } = loaderData.value;

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

  if (!dashboard) {
    return (
      <RequestStatePanel
        title="Нет данных дашборда"
        detail="Сервис не вернул сводку для выбранного периода и окружения."
      />
    );
  }

  const dashboardState = buildAdminDashboardDerivedState(location.url, dashboard, filters);
  const activeIncidentsHref = buildAdminDashboardActiveIncidentsHref(
    location.url,
    dashboardState.activeDrillDownSelection,
    dashboardState,
  );
  const activeSelectionSummary = dashboardState.activeDrillDownSelection
    ? [
        dashboardState.activeDrillDownSelection.errorCode,
        dashboardState.activeDrillDownSelection.category,
        dashboardState.activeDrillDownSelection.roomId,
        dashboardState.activeDrillDownSelection.meetingId,
      ].filter((value) => value.trim().length > 0).join(" / ")
    : "";

  return (
    <div class="space-y-6">
      <section class="rounded-[2rem] border border-border bg-[linear-gradient(135deg,var(--color-surface)_0%,var(--color-bg)_60%,color-mix(in_srgb,var(--color-muted)_12%,transparent)_100%)] px-6 py-6 shadow-sm">
        <div class="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between">
          <div class="max-w-3xl space-y-4">
            <div>
              <p class="text-xs uppercase tracking-[0.26em] text-muted">Обзор</p>
              <h2 class="mt-2 text-2xl font-semibold text-text">Оперативная сводка администратора</h2>
              <p class="mt-3 text-sm text-muted">
                На первом экране собраны сигналы, требующие внимания прямо сейчас: приоритетное уведомление,
                деградации, состояние ключевых сервисов и переход в очередь инцидентов.
              </p>
            </div>
            <div class="flex flex-wrap gap-2">
              {PERIOD_OPTIONS.map((period) => (
                <a
                  key={period}
                  href={buildQueryHref(location.url, { period })}
                  class={[
                    "rounded-full border px-3 py-1 text-sm transition-colors",
                    dashboardState.activePeriod === period
                      ? "border-text bg-text text-bg"
                      : "border-border bg-bg text-text hover:bg-surface-alt",
                  ]}
                >
                  {period}
                </a>
              ))}
            </div>
            <div class="flex flex-wrap gap-2">
              {ENVIRONMENT_OPTIONS.map((environment) => (
                <a
                  key={environment}
                  href={buildQueryHref(location.url, { environment })}
                  class={[
                    "rounded-full border px-3 py-1 text-sm uppercase transition-colors",
                    dashboardState.activeEnvironment === environment
                      ? "border-text bg-text text-bg"
                      : "border-border bg-bg text-text hover:bg-surface-alt",
                  ]}
                >
                  {environment}
                </a>
              ))}
            </div>
          </div>
          <div class="min-w-[16rem] rounded-3xl border border-border bg-bg/80 p-4">
            <p class="text-xs uppercase tracking-[0.22em] text-muted">Контекст</p>
            <dl class="mt-3 space-y-2 text-sm text-muted">
              <div class="flex items-center justify-between gap-3">
                <dt>Окружение</dt>
                <dd class="font-medium uppercase text-text">{dashboardState.activeEnvironment}</dd>
              </div>
              <div class="flex items-center justify-between gap-3">
                <dt>Окно</dt>
                <dd class="font-medium text-text">{dashboardState.activePeriod}</dd>
              </div>
              <div class="flex items-center justify-between gap-3">
                <dt>Trace ID</dt>
                <dd class="font-medium text-text">{dashboard.traceId}</dd>
              </div>
            </dl>
            {dashboard.sampleWindowLimited ? (
              <p class="mt-4 text-xs text-muted">
                Сводка построена по безопасному окну чтения, чтобы ускорить первичный разбор.
              </p>
            ) : null}
          </div>
        </div>
      </section>

      <section class={[
        "rounded-[2rem] border px-6 py-5 shadow-sm",
        resolveAdminDashboardCardTone(dashboard.priorityBanner.severity),
      ]}>
        <div class="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div class="max-w-3xl">
            <p class="text-xs uppercase tracking-[0.22em]">Приоритетный сигнал</p>
            <h3 class="mt-2 text-xl font-semibold">{dashboard.priorityBanner.headline}</h3>
            <p class="mt-2 text-sm opacity-90">{dashboard.priorityBanner.summary}</p>
          </div>
          <a
            href={buildAdminDashboardSelectionHref(location.url, dashboard.priorityBanner.handoff, dashboardState)}
            class="inline-flex rounded-2xl border border-current px-4 py-2 text-sm font-medium"
          >
            {dashboard.priorityBanner.actionLabel}
          </a>
        </div>
      </section>

      <section class="grid gap-4 xl:grid-cols-[1.25fr_0.75fr]">
        <div class="space-y-4">
          <article class="rounded-3xl border border-border bg-surface p-5 shadow-sm">
            <div class="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
              <div>
                <p class="text-xs uppercase tracking-[0.22em] text-muted">Активная детализация</p>
                <h3 class="mt-2 text-lg font-semibold text-text">
                  {drillDown ? `${drillDown.selectionType}: ${drillDown.selectionValue}` : "Один сигнал раскрывается прямо в overview"}
                </h3>
                <p class="mt-2 text-sm text-muted">
                  {drillDown
                    ? "Последние отказы и entity filter показаны рядом со сводкой, чтобы не выбрасывать разбор в отдельную очередь слишком рано."
                    : activeSelectionSummary.length > 0
                      ? `Выбранный контекст: ${activeSelectionSummary}`
                      : "Выберите карточку ниже, чтобы зафиксировать один контекст разбора и открыть связанную очередь при необходимости."}
                </p>
              </div>
              <a
                href={activeIncidentsHref}
                class="inline-flex rounded-2xl border border-border bg-bg px-4 py-2 text-sm font-medium text-text transition-colors hover:bg-surface-alt"
              >
                Открыть очередь
              </a>
            </div>

            {drillDownError ? (
              <div class="mt-4">
                <ApiErrorAlert
                  title={drillDownError.title}
                  message={drillDownError.detail}
                  errorCode={drillDownError.errorCode}
                  traceId={drillDownError.traceId}
                />
              </div>
            ) : null}

            {drillDown ? (
              <div class="mt-4 space-y-4">
                <div class="grid gap-3 md:grid-cols-3">
                  <div class="rounded-2xl border border-border bg-bg px-4 py-3">
                    <p class="text-xs uppercase tracking-[0.18em] text-muted">Отказы</p>
                    <p class="mt-2 text-2xl font-semibold text-text">{drillDown.failureCount}</p>
                  </div>
                  <div class="rounded-2xl border border-border bg-bg px-4 py-3">
                    <p class="text-xs uppercase tracking-[0.18em] text-muted">Окружение</p>
                    <p class="mt-2 text-base font-semibold uppercase text-text">{drillDown.environment}</p>
                  </div>
                  <div class="rounded-2xl border border-border bg-bg px-4 py-3">
                    <p class="text-xs uppercase tracking-[0.18em] text-muted">Окно</p>
                    <p class="mt-2 text-base font-semibold text-text">{drillDown.period}</p>
                  </div>
                </div>

                {(drillDown.entityFilter.roomId || drillDown.entityFilter.meetingId) ? (
                  <dl class="grid gap-3 rounded-2xl border border-border bg-bg px-4 py-4 text-sm text-muted md:grid-cols-2">
                    {drillDown.entityFilter.roomId ? (
                      <div>
                        <dt class="text-xs uppercase tracking-[0.18em]">ID комнаты</dt>
                        <dd class="mt-1 font-medium text-text">{drillDown.entityFilter.roomId}</dd>
                      </div>
                    ) : null}
                    {drillDown.entityFilter.meetingId ? (
                      <div>
                        <dt class="text-xs uppercase tracking-[0.18em]">ID встречи</dt>
                        <dd class="mt-1 font-medium text-text">{drillDown.entityFilter.meetingId}</dd>
                      </div>
                    ) : null}
                  </dl>
                ) : null}

                <div>
                  <div class="flex items-center justify-between gap-3">
                    <h4 class="text-base font-semibold text-text">Последние отказы</h4>
                    {drillDown.sampleWindowLimited ? (
                      <span class="text-xs uppercase tracking-[0.18em] text-muted">Безопасное окно выборки</span>
                    ) : null}
                  </div>
                  <div class="mt-3 space-y-3">
                    {drillDown.recentSamples.length > 0 ? drillDown.recentSamples.map((sample) => (
                      <article
                        key={`${sample.occurredAt}-${sample.traceId ?? sample.roomId ?? sample.meetingId ?? sample.userMessage}`}
                        class="rounded-2xl border border-border bg-bg px-4 py-3"
                      >
                        <div class="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
                          <div>
                            <p class="text-sm font-medium text-text">{sample.userMessage}</p>
                            <p class="mt-1 text-xs uppercase tracking-[0.18em] text-muted">{sample.occurredAt}</p>
                          </div>
                          {sample.traceUrl ? (
                            <a href={sample.traceUrl} class="text-sm text-text underline">
                              Трассировка
                            </a>
                          ) : null}
                        </div>
                        <dl class="mt-3 grid gap-3 text-sm text-muted md:grid-cols-2 xl:grid-cols-4">
                          {sample.errorCode ? (
                            <div>
                              <dt class="text-xs uppercase tracking-[0.18em]">Код</dt>
                              <dd class="mt-1 text-text">{sample.errorCode}</dd>
                            </div>
                          ) : null}
                          {sample.reasonCategory ? (
                            <div>
                              <dt class="text-xs uppercase tracking-[0.18em]">Категория</dt>
                              <dd class="mt-1 text-text">{sample.reasonCategory}</dd>
                            </div>
                          ) : null}
                          {sample.roomId ? (
                            <div>
                              <dt class="text-xs uppercase tracking-[0.18em]">Комната</dt>
                              <dd class="mt-1 text-text">{sample.roomId}</dd>
                            </div>
                          ) : null}
                          {sample.meetingId ? (
                            <div>
                              <dt class="text-xs uppercase tracking-[0.18em]">Встреча</dt>
                              <dd class="mt-1 text-text">{sample.meetingId}</dd>
                            </div>
                          ) : null}
                        </dl>
                      </article>
                    )) : (
                      <RequestStatePanel
                        title="Нет свежих выборок"
                        detail="Для активного сигнала не нашлось недавних отказов в пределах безопасного окна чтения."
                      />
                    )}
                  </div>
                </div>
              </div>
            ) : null}

            {!drillDown && !drillDownError ? (
              <div class="mt-4">
                <RequestStatePanel
                  title="Нет активной детализации"
                  detail="Выберите сигнал на overview, чтобы загрузить один drill-down контекст без перехода в отдельный экран."
                />
              </div>
            ) : null}
          </article>

          <article class="rounded-3xl border border-border bg-surface p-5 shadow-sm">
            <div class="flex items-center justify-between gap-3">
              <h3 class="text-lg font-semibold text-text">Ключевые деградации</h3>
              <a
                href={activeIncidentsHref}
                class="text-sm text-muted underline"
              >
                Открыть всю очередь
              </a>
            </div>
            <div class="mt-4 grid gap-3 md:grid-cols-2">
              {dashboard.topDegradations.length > 0 ? dashboard.topDegradations.map((degradation) => (
                <a
                  key={degradation.id}
                  href={buildAdminDashboardSelectionHref(location.url, degradation.handoff, dashboardState)}
                  class={[
                    "rounded-3xl border p-4 transition-transform hover:-translate-y-0.5",
                    resolveAdminDashboardCardTone(degradation.severity),
                  ]}
                >
                  <p class="text-xs uppercase tracking-[0.18em] opacity-70">{degradation.severity}</p>
                  <h4 class="mt-2 text-base font-semibold">{degradation.title}</h4>
                  <p class="mt-2 text-sm opacity-90">{degradation.summary}</p>
                  <p class="mt-4 text-sm font-medium underline">{degradation.actionLabel}</p>
                </a>
              )) : (
                <RequestStatePanel
                  title="Новых деградаций нет"
                  detail="Используйте приоритетный сигнал и безопасные действия, чтобы перейти к разбору очереди при изменении состояния."
                />
              )}
            </div>
          </article>

          <article class="rounded-3xl border border-border bg-surface p-5 shadow-sm">
            <h3 class="text-lg font-semibold text-text">Ключевые сервисы</h3>
            <div class="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
              {dashboard.keyServiceStatuses.map((service) => (
                <a
                  key={service.key}
                  href={buildAdminDashboardSelectionHref(location.url, service.handoff, dashboardState)}
                  class="rounded-2xl border border-border bg-bg p-4 transition-colors hover:bg-surface-alt"
                >
                  <p class="text-xs uppercase tracking-[0.18em] text-muted">{service.label}</p>
                  <p class="mt-2 text-2xl font-semibold text-text">{service.status}</p>
                  <p class="mt-2 text-sm text-muted">{service.detail}</p>
                </a>
              ))}
            </div>
          </article>
        </div>

        <div class="space-y-4">
          <article class="rounded-3xl border border-border bg-surface p-5 shadow-sm">
            <h3 class="text-lg font-semibold text-text">Новые всплески</h3>
            <div class="mt-4 space-y-2">
              {dashboard.latestSpikes.length > 0 ? dashboard.latestSpikes.map((spike) => (
                <a
                  key={`${spike.errorCode}-${spike.category ?? "uncategorized"}`}
                  href={buildAdminDashboardSelectionHref(location.url, spike.handoff, dashboardState)}
                  class="block rounded-2xl border border-border bg-bg px-4 py-3 transition-colors hover:bg-surface-alt"
                >
                  <div class="flex items-center justify-between gap-3">
                    <span class="text-sm font-medium text-text">{spike.errorCode}</span>
                    <span class="text-sm text-muted">{spike.count}</span>
                  </div>
                  <p class="mt-2 text-sm text-muted">{spike.summary}</p>
                </a>
              )) : (
                <p class="text-sm text-muted">По текущему окну новые всплески не обнаружены.</p>
              )}
            </div>
          </article>

          <article class="rounded-3xl border border-border bg-surface p-5 shadow-sm">
            <h3 class="text-lg font-semibold text-text">Затронутый контур</h3>
            <div class="mt-4 space-y-2">
              {dashboard.affectedScopeSummary.length > 0 ? dashboard.affectedScopeSummary.map((scope) => (
                <a
                  key={`${scope.scopeType}-${scope.scopeValue}`}
                  href={buildAdminDashboardSelectionHref(location.url, scope.handoff, dashboardState)}
                  class="block rounded-2xl border border-border bg-bg px-4 py-3 transition-colors hover:bg-surface-alt"
                >
                  <div class="flex items-center justify-between gap-3">
                    <span class="text-sm font-medium text-text">{scope.scopeType}: {scope.scopeValue}</span>
                    <span class="text-sm text-muted">{scope.affectedAttempts}</span>
                  </div>
                  <p class="mt-2 text-sm text-muted">{scope.summary}</p>
                </a>
              )) : (
                <p class="text-sm text-muted">Затронутые области не требуют отдельного handoff для текущего окна.</p>
              )}
            </div>
          </article>

          {dashboard.safeStateSummary.stable ? (
            <article class="rounded-3xl border border-emerald-300 bg-emerald-50 p-5 text-emerald-950 shadow-sm dark:border-emerald-800 dark:bg-emerald-950 dark:text-emerald-100">
              <h3 class="text-lg font-semibold">{dashboard.safeStateSummary.headline}</h3>
              <p class="mt-2 text-sm opacity-90">{dashboard.safeStateSummary.summary}</p>
              <div class="mt-4 flex flex-wrap gap-2">
                {dashboard.safeStateSummary.actions.map((action) => (
                  <a key={action.href} href={action.href} class="rounded-full border border-current px-3 py-1 text-sm font-medium">
                    {action.label}
                  </a>
                ))}
              </div>
              {dashboard.safeStateSummary.recentResolvedSpikes.length > 0 ? (
                <div class="mt-4 space-y-2 text-sm">
                  {dashboard.safeStateSummary.recentResolvedSpikes.map((spike) => (
                    <div key={spike.label} class="rounded-2xl border border-current/20 px-3 py-2">
                      <p class="font-medium">{spike.label}</p>
                      <p class="opacity-90">{spike.detail}</p>
                    </div>
                  ))}
                </div>
              ) : null}
            </article>
          ) : null}

          <article class="rounded-3xl border border-dashed border-border bg-bg/60 p-5 shadow-sm">
            <p class="text-xs uppercase tracking-[0.22em] text-muted">Вторичные модули</p>
            <h3 class="mt-2 text-lg font-semibold text-text">Дополнительные действия</h3>
            <p class="mt-2 text-sm text-muted">
              Редкие административные операции остаются рядом с обзором, но не отвлекают от приоритетных сигналов и очереди инцидентов.
            </p>
            <div class="mt-4 flex flex-wrap gap-2">
              {dashboardState.secondaryModuleLinks.map((link) => (
                <a key={link.href} href={link.href} class="rounded-full border border-border bg-surface px-3 py-2 text-sm text-text transition-colors hover:bg-surface-alt">
                  {link.label}
                </a>
              ))}
            </div>
          </article>
        </div>
      </section>
    </div>
  );
});

export const head: DocumentHead = {
  title: "Сводка администратора — Jitsi Portal",
  meta: [
    {
      name: "description",
      content: "Оперативная административная сводка: приоритетный сигнал, ключевые деградации, состояние сервисов и переход в очередь инцидентов.",
    },
  ],
};