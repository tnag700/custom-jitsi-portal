import { component$ } from "@qwik.dev/core";
import { routeLoader$, useLocation, type DocumentHead } from "@qwik.dev/router";
import {
  AdminDashboardOverview,
  AdminDashboardServiceError,
  buildAdminDashboardDerivedState,
  buildAdminDashboardFilters,
  fetchAdminDashboard,
  fetchAdminDrillDown,
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

export const useAdminDashboard = routeLoader$(
  async ({ sharedMap, cookie, query, redirect, url }) => {
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

    const dashboardState = buildAdminDashboardDerivedState(dashboard, filters);
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
            throw redirect(
              302,
              resolveAuthRecoveryRedirectPath(error, returnTo),
            );
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
  },
);

export default component$(() => {
  const loaderData = useAdminDashboard();
  const location = useLocation();
  const { dashboard, drillDown, drillDownError, loadError, filters } =
    loaderData.value;

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

  return (
    <AdminDashboardOverview
      currentUrl={location.url.href}
      dashboard={dashboard}
      drillDown={drillDown}
      drillDownError={drillDownError}
      filters={filters}
    />
  );
});

export const head: DocumentHead = {
  title: "Сводка администратора — Jitsi Portal",
  meta: [
    {
      name: "description",
      content:
        "Оперативная административная сводка: приоритетный сигнал, ключевые деградации, состояние сервисов и переход в очередь инцидентов.",
    },
  ],
};
