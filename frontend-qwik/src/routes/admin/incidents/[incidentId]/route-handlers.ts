import { routeAction$, routeLoader$, z, zod$ } from "@qwik.dev/router";
import {
  resolveAuthRecoveryRedirectPath,
  type SafeUserProfile,
} from "~/lib/domains/auth";
import {
  AdminDashboardServiceError,
  buildIncidentMutationAccessDenied,
  buildIncidentMutationUnexpectedError,
  canManageIncidentTicket,
  createAdminIncidentTicket,
  fetchAdminIncidentDetail,
  updateAdminIncidentCoordination,
  type AdminDashboardErrorPayload,
  type AdminIncidentDetail,
} from "~/lib/domains/admin";
import {
  buildMutationRequestContext,
  buildServerRequestContext,
} from "~/lib/shared/routes/server-handlers";

interface IncidentDetailLoaderData {
  incident: AdminIncidentDetail | null;
  loadError: AdminDashboardErrorPayload | null;
  canManageTicket: boolean;
}

// eslint-disable-next-line qwik/loader-location
export const useIncidentDetail = routeLoader$(
  async ({ sharedMap, cookie, params, query, redirect, url }) => {
    const user = (sharedMap.get("user") as SafeUserProfile | null) ?? null;
    const requestContext = buildServerRequestContext({ sharedMap, cookie });
    const returnTo = `${url.pathname}${url.search}`;
    try {
      const incident = await fetchAdminIncidentDetail(
        requestContext,
        params.incidentId,
        query.get("environment")?.trim() || undefined,
      );
      return {
        incident,
        loadError: null,
        canManageTicket: canManageIncidentTicket(user),
      } satisfies IncidentDetailLoaderData;
    } catch (error) {
      if (error instanceof AdminDashboardServiceError) {
        if (error.payload.errorCode === "AUTH_REQUIRED") {
          throw redirect(302, resolveAuthRecoveryRedirectPath(error, returnTo));
        }
        if (error.payload.errorCode === "ACCESS_DENIED") {
          throw redirect(302, "/");
        }
        return {
          incident: null,
          loadError: error.payload,
          canManageTicket: canManageIncidentTicket(user),
        } satisfies IncidentDetailLoaderData;
      }
      throw error;
    }
  },
);

// eslint-disable-next-line qwik/loader-location
export const useCreateIncidentTicket = routeAction$(
  async (data, { sharedMap, cookie, fail, redirect, url }) => {
    const user = (sharedMap.get("user") as SafeUserProfile | null) ?? null;
    const returnTo = `${url.pathname}${url.search}`;
    if (!canManageIncidentTicket(user)) {
      return fail(403, {
        error: buildIncidentMutationAccessDenied(
          "Создание external ticket доступно только admin.",
        ),
      });
    }
    try {
      const requestContext = await buildMutationRequestContext({
        sharedMap,
        cookie,
      });
      const ticket = await createAdminIncidentTicket(
        requestContext,
        data.incidentId,
        data.environment,
      );
      return { success: true as const, ticket };
    } catch (error) {
      if (error instanceof AdminDashboardServiceError) {
        if (error.payload.errorCode === "AUTH_REQUIRED") {
          throw redirect(302, resolveAuthRecoveryRedirectPath(error, returnTo));
        }
        return fail(400, { error: error.payload });
      }
      return fail(500, {
        error: buildIncidentMutationUnexpectedError(
          "Ошибка ticket action",
          "Не удалось создать внешний тикет.",
          "ADMIN_INCIDENT_TICKET_FAILED",
        ),
      });
    }
  },
  zod$(
    z.object({
      incidentId: z.string().min(1),
      environment: z.string().optional(),
    }),
  ),
);

// eslint-disable-next-line qwik/loader-location
export const useUpdateIncidentCoordination = routeAction$(
  async (data, { sharedMap, cookie, fail, redirect, url }) => {
    const user = (sharedMap.get("user") as SafeUserProfile | null) ?? null;
    const returnTo = `${url.pathname}${url.search}`;
    if (!canManageIncidentTicket(user)) {
      return fail(403, {
        error: buildIncidentMutationAccessDenied(
          "Изменение coordination seam доступно только admin.",
        ),
      });
    }
    try {
      const requestContext = await buildMutationRequestContext({
        sharedMap,
        cookie,
      });
      const coordination = await updateAdminIncidentCoordination(
        requestContext,
        data.incidentId,
        {
          environment: data.environment,
          owner: data.owner,
          workflowStatus: data.workflowStatus,
          ticketReference: data.ticketReference,
          ticketStatus: data.ticketStatus,
        },
      );
      return { success: true as const, coordination };
    } catch (error) {
      if (error instanceof AdminDashboardServiceError) {
        if (error.payload.errorCode === "AUTH_REQUIRED") {
          throw redirect(302, resolveAuthRecoveryRedirectPath(error, returnTo));
        }
        return fail(400, { error: error.payload });
      }
      return fail(500, {
        error: buildIncidentMutationUnexpectedError(
          "Ошибка coordination seam",
          "Не удалось обновить owner, workflow status или ticket link.",
          "ADMIN_INCIDENT_COORDINATION_FAILED",
        ),
      });
    }
  },
  zod$(
    z.object({
      incidentId: z.string().min(1),
      environment: z.string().optional(),
      owner: z.string().optional(),
      workflowStatus: z.enum([
        "triage",
        "investigating",
        "waiting-external",
        "resolved",
      ]),
      ticketReference: z.string().optional(),
      ticketStatus: z
        .enum(["not-linked", "linked", "waiting-external", "resolved"])
        .optional(),
    }),
  ),
);
