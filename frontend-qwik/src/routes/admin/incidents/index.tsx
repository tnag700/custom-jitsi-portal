import { component$ } from "@qwik.dev/core";
import { routeLoader$, useLocation, type DocumentHead } from "@qwik.dev/router";
import {
  AdminDashboardServiceError,
  AdminIncidentQueueOverview,
  buildIncidentDetailHref,
  buildIncidentQueueDerivedState,
  buildIncidentQueueFilters,
  buildIncidentQueueReturnHref,
  fetchAdminIncidents,
  hasIncidentSearchQuery,
  normalizeNonNegativeAdminInteger as normalizeNonNegativeInteger,
  normalizePositiveAdminInteger as normalizePositiveInteger,
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

export const useAdminIncidents = routeLoader$(
  async ({ sharedMap, cookie, query, url, redirect }) => {
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
  },
);

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
    return (
      <RequestStatePanel
        title="Нет инцидентов"
        detail="Сервис не вернул список инцидентов."
      />
    );
  }

  return (
    <AdminIncidentQueueOverview
      currentUrl={location.url.href}
      incidents={incidents}
      searchResult={searchResult}
      filters={filters}
    />
  );
});

export const head: DocumentHead = {
  title: "Инциденты администратора — Jitsi Portal",
  meta: [
    {
      name: "description",
      content:
        "Административная очередь инцидентов для поиска, фильтрации и перехода к карточке инцидента.",
    },
  ],
};
