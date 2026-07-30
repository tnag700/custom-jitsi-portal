import { routeLoader$ } from "@qwik.dev/router";
import {
  AdminDashboardServiceError,
  buildAdminRoleHistoryFilters,
  fetchAdminRoleHistory,
  hasAdminRoleHistoryPrimaryFilter,
  normalizeNonNegativeAdminInteger,
  normalizePositiveAdminInteger,
  type AdminDashboardErrorPayload,
  type AdminRoleHistory,
  type AdminRoleHistoryFilters,
} from "~/lib/domains/admin";
import { resolveAuthRecoveryRedirectPath } from "~/lib/domains/auth";
import { buildServerRequestContext } from "~/lib/shared/routes/server-handlers";

export interface RoleHistoryLoaderData {
  history: AdminRoleHistory | null;
  loadError: AdminDashboardErrorPayload | null;
  hasPrimaryFilter: boolean;
  filters: AdminRoleHistoryFilters;
}

// eslint-disable-next-line qwik/loader-location
export const useAdminRoleHistory = routeLoader$(
  async ({ sharedMap, cookie, query, redirect, url }) => {
    const requestContext = buildServerRequestContext({ sharedMap, cookie });
    const filters = buildAdminRoleHistoryFilters(query);
    const returnTo = `${url.pathname}${url.search}`;

    if (!hasAdminRoleHistoryPrimaryFilter(filters)) {
      return {
        history: null,
        loadError: null,
        hasPrimaryFilter: false,
        filters,
      } satisfies RoleHistoryLoaderData;
    }

    const page = normalizeNonNegativeAdminInteger(filters.page, 0);
    const pageSize = normalizePositiveAdminInteger(filters.pageSize, 20);

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

      return {
        history,
        loadError: null,
        hasPrimaryFilter: true,
        filters,
      } satisfies RoleHistoryLoaderData;
    } catch (error) {
      if (error instanceof AdminDashboardServiceError) {
        if (error.payload.errorCode === "AUTH_REQUIRED") {
          throw redirect(302, resolveAuthRecoveryRedirectPath(error, returnTo));
        }
        if (error.payload.errorCode === "ACCESS_DENIED") {
          throw redirect(302, "/");
        }
        return {
          history: null,
          loadError: error.payload,
          hasPrimaryFilter: true,
          filters,
        } satisfies RoleHistoryLoaderData;
      }
      throw error;
    }
  },
);
