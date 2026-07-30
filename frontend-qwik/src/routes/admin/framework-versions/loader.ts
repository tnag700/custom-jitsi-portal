import { routeAction$, routeLoader$ } from "@qwik.dev/router";
import {
  AdminDashboardServiceError,
  canRefreshFrameworkVersions,
  fetchAdminFrameworkVersions,
  refreshAdminFrameworkVersions,
  type AdminDashboardErrorPayload,
  type AdminFrameworkVersions,
} from "~/lib/domains/admin";
import {
  resolveAuthRecoveryRedirectPath,
  type SafeUserProfile,
} from "~/lib/domains/auth";
import {
  buildMutationRequestContext,
  buildServerRequestContext,
} from "~/lib/shared/routes/server-handlers";

export interface FrameworkVersionsLoaderData {
  snapshot: AdminFrameworkVersions | null;
  loadError: AdminDashboardErrorPayload | null;
  canRefresh: boolean;
}

// eslint-disable-next-line qwik/loader-location
export const useAdminFrameworkVersions = routeLoader$(
  async ({ sharedMap, cookie, redirect, url }) => {
    const user = (sharedMap.get("user") as SafeUserProfile | null) ?? null;
    const returnTo = `${url.pathname}${url.search}`;
    if (!user) {
      throw redirect(302, resolveAuthRecoveryRedirectPath(undefined, returnTo));
    }
    const refreshAllowed = canRefreshFrameworkVersions(user.claims);

    try {
      return {
        snapshot: await fetchAdminFrameworkVersions(
          buildServerRequestContext({ sharedMap, cookie }),
        ),
        loadError: null,
        canRefresh: refreshAllowed,
      } satisfies FrameworkVersionsLoaderData;
    } catch (error) {
      if (error instanceof AdminDashboardServiceError) {
        if (error.payload.errorCode === "AUTH_REQUIRED") {
          throw redirect(302, resolveAuthRecoveryRedirectPath(error, returnTo));
        }
        if (error.payload.errorCode === "ACCESS_DENIED") {
          throw redirect(302, "/");
        }
        return {
          snapshot: null,
          loadError: error.payload,
          canRefresh: refreshAllowed,
        } satisfies FrameworkVersionsLoaderData;
      }
      throw error;
    }
  },
);

// eslint-disable-next-line qwik/loader-location
export const useRefreshFrameworkVersions = routeAction$(
  async (_data, { sharedMap, cookie, fail }) => {
    try {
      const snapshot = await refreshAdminFrameworkVersions(
        await buildMutationRequestContext({ sharedMap, cookie }),
      );
      return { success: true as const, snapshot };
    } catch (error) {
      if (error instanceof AdminDashboardServiceError) {
        return fail(error.payload.errorCode === "ACCESS_DENIED" ? 403 : 400, {
          error: error.payload,
        });
      }
      return fail(500, {
        error: {
          title: "Проверка не выполнена",
          detail: "Не удалось обновить снимок версий и уязвимостей.",
          errorCode: "FRAMEWORK_VERSION_REFRESH_FAILED",
        },
      });
    }
  },
);
