import type {
  AdminIncidentCoordination,
  AdminIncidentDetail,
  AdminIncidentList,
  AdminIncidentSearch,
  AdminIncidentTicket,
  AdminDashboardDrillDown,
  AdminDashboardErrorPayload,
  AdminFrameworkVersions,
  AdminRoleHistory,
  AdminDashboardSummary,
} from "./types";
import {
  adminIncidentCoordinationSchema,
  adminIncidentDetailSchema,
  adminIncidentListSchema,
  adminIncidentSearchSchema,
  adminIncidentTicketSchema,
  adminDashboardDrillDownSchema,
  adminFrameworkVersionsSchema,
  adminRoleHistorySchema,
  adminDashboardSummarySchema,
} from "./types";
import { adaptProblemDetails } from "../../shared/api";
import type { MutationRequestContext, ServerRequestContext } from "../../shared/routes/server-handlers";
import { asMutationRequestContext, asServerRequestContext } from "../../shared/routes/server-handlers";

export interface AdminDashboardQuery {
  period?: string;
  environment?: string;
  roomId?: string;
  meetingId?: string;
}

export interface AdminDrillDownQuery extends AdminDashboardQuery {
  errorCode?: string;
  category?: string;
}

export interface AdminIncidentsQuery extends AdminDashboardQuery {
  view?: string;
  facet?: string;
  subjectId?: string;
  errorCode?: string;
  category?: string;
  severity?: string;
  limit?: number;
  offset?: number;
  sort?: string;
  direction?: string;
}

export interface AdminIncidentSearchQuery {
  environment?: string;
  traceId?: string;
  requestId?: string;
  errorCode?: string;
  from?: string;
  to?: string;
  meetingId?: string;
}

export interface AdminIncidentCoordinationMutationInput {
  environment?: string;
  owner?: string;
  workflowStatus: string;
  ticketReference?: string;
  ticketStatus?: string;
}

export interface AdminRoleHistoryQuery {
  environment?: string;
  q?: string;
  from?: string;
  to?: string;
  actionType?: string;
  role?: string;
  actorId?: string;
  subjectId?: string;
  roomId?: string;
  meetingId?: string;
  page?: number;
  pageSize?: number;
}

export class AdminDashboardServiceError extends Error {
  payload: AdminDashboardErrorPayload;

  constructor(payload: AdminDashboardErrorPayload) {
    super(payload.detail);
    this.name = "AdminDashboardServiceError";
    this.payload = payload;
  }
}

function normalizeMutationText(value?: string): string | null | undefined {
  if (value === undefined) {
    return undefined;
  }
  const normalized = value.trim();
  return normalized.length > 0 ? normalized : null;
}

function normalizeTicketStatus(ticketReference?: string, ticketStatus?: string): string | undefined {
  const normalizedReference = normalizeMutationText(ticketReference);
  const normalizedStatus = ticketStatus?.trim();
  if (!normalizedReference) {
    return normalizedStatus ? "not-linked" : undefined;
  }
  if (!normalizedStatus || normalizedStatus === "not-linked") {
    return "linked";
  }
  return normalizedStatus;
}

function fallbackErrorCode(status: number): string {
  if (status === 401) return "AUTH_REQUIRED";
  if (status === 403) return "ACCESS_DENIED";
  if (status >= 500) return "ADMIN_DASHBOARD_UNAVAILABLE";
  return "ADMIN_DASHBOARD_UNKNOWN";
}

function buildUrl(baseUrl: string, path: string, query?: Record<string, string | undefined>): string {
  const params = new URLSearchParams();
  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value && value.trim().length > 0) {
      params.set(key, value);
    }
  });
  const search = params.toString();
  return search.length > 0 ? `${baseUrl}${path}?${search}` : `${baseUrl}${path}`;
}

function parseOrThrow<T>(parseFn: (data: unknown) => T, data: unknown, endpoint: string): T {
  try {
    return parseFn(data);
  } catch (error) {
    throw new AdminDashboardServiceError({
      title: "Неожиданный формат ответа",
      detail: `${endpoint}: ${error instanceof Error ? error.message : "неверный формат ответа"}`,
      errorCode: "ADMIN_DASHBOARD_RESPONSE_INVALID",
    });
  }
}

async function parseJsonOrThrow(response: Response): Promise<unknown> {
  const contentType = response.headers.get("content-type") ?? "";
  if (!contentType.includes("application/json")) {
    return null;
  }
  return response.json();
}

async function throwProblem(response: Response): Promise<never> {
  const payload = await adaptProblemDetails(
    response,
    response.status,
    fallbackErrorCode,
    "Ошибка админ-кабинета",
    "Не удалось загрузить данные operational dashboard.",
  );
  throw new AdminDashboardServiceError(payload);
}

export function fetchAdminDashboard(context: ServerRequestContext, query?: AdminDashboardQuery): Promise<AdminDashboardSummary>;
export function fetchAdminDashboard(sessionCookie: string, apiUrl: string, query?: AdminDashboardQuery): Promise<AdminDashboardSummary>;
export async function fetchAdminDashboard(
  contextOrSessionCookie: ServerRequestContext | string,
  apiUrlOrQuery?: string | AdminDashboardQuery,
  query?: AdminDashboardQuery,
): Promise<AdminDashboardSummary> {
  const context = asServerRequestContext(
    contextOrSessionCookie,
    typeof contextOrSessionCookie === "string" ? (apiUrlOrQuery as string) : undefined,
  );
  const resolvedQuery = typeof contextOrSessionCookie === "string" ? query : (apiUrlOrQuery as AdminDashboardQuery | undefined);
  const response = await fetch(
    buildUrl(context.apiUrl, "/admin/dashboard", {
      period: resolvedQuery?.period,
      environment: resolvedQuery?.environment,
      roomId: resolvedQuery?.roomId,
      meetingId: resolvedQuery?.meetingId,
    }),
    {
      method: "GET",
      headers: context.headers,
    },
  );

  if (!response.ok) {
    await throwProblem(response);
  }

  return parseOrThrow(
    (data) => adminDashboardSummarySchema.parse(data),
    await parseJsonOrThrow(response),
    "GET /api/v1/admin/dashboard",
  );
}

export function fetchAdminDrillDown(context: ServerRequestContext, query: AdminDrillDownQuery): Promise<AdminDashboardDrillDown>;
export function fetchAdminDrillDown(sessionCookie: string, apiUrl: string, query: AdminDrillDownQuery): Promise<AdminDashboardDrillDown>;
export async function fetchAdminDrillDown(
  contextOrSessionCookie: ServerRequestContext | string,
  apiUrlOrQuery: string | AdminDrillDownQuery,
  query?: AdminDrillDownQuery,
): Promise<AdminDashboardDrillDown> {
  const context = asServerRequestContext(
    contextOrSessionCookie,
    typeof contextOrSessionCookie === "string" ? (apiUrlOrQuery as string) : undefined,
  );
  const resolvedQuery = typeof contextOrSessionCookie === "string"
    ? (query ?? {})
    : (apiUrlOrQuery as AdminDrillDownQuery);
  const response = await fetch(
    buildUrl(context.apiUrl, "/admin/dashboard/drill-down", {
      period: resolvedQuery.period,
      environment: resolvedQuery.environment,
      roomId: resolvedQuery.roomId,
      meetingId: resolvedQuery.meetingId,
      errorCode: resolvedQuery.errorCode,
      category: resolvedQuery.category,
    }),
    {
      method: "GET",
      headers: context.headers,
    },
  );

  if (!response.ok) {
    await throwProblem(response);
  }

  return parseOrThrow(
    (data) => adminDashboardDrillDownSchema.parse(data),
    await parseJsonOrThrow(response),
    "GET /api/v1/admin/dashboard/drill-down",
  );
}

export function fetchAdminIncidents(context: ServerRequestContext, query?: AdminIncidentsQuery): Promise<AdminIncidentList>;
export function fetchAdminIncidents(sessionCookie: string, apiUrl: string, query?: AdminIncidentsQuery): Promise<AdminIncidentList>;
export async function fetchAdminIncidents(
  contextOrSessionCookie: ServerRequestContext | string,
  apiUrlOrQuery?: string | AdminIncidentsQuery,
  query?: AdminIncidentsQuery,
): Promise<AdminIncidentList> {
  const context = asServerRequestContext(
    contextOrSessionCookie,
    typeof contextOrSessionCookie === "string" ? (apiUrlOrQuery as string) : undefined,
  );
  const resolvedQuery = typeof contextOrSessionCookie === "string" ? query : (apiUrlOrQuery as AdminIncidentsQuery | undefined);
  const response = await fetch(
    buildUrl(context.apiUrl, "/admin/incidents", {
      period: resolvedQuery?.period,
      environment: resolvedQuery?.environment,
      view: resolvedQuery?.view,
      facet: resolvedQuery?.facet,
      roomId: resolvedQuery?.roomId,
      meetingId: resolvedQuery?.meetingId,
      subjectId: resolvedQuery?.subjectId,
      errorCode: resolvedQuery?.errorCode,
      category: resolvedQuery?.category,
      severity: resolvedQuery?.severity,
      limit: resolvedQuery?.limit?.toString(),
      offset: resolvedQuery?.offset?.toString(),
      sort: resolvedQuery?.sort,
      direction: resolvedQuery?.direction,
    }),
    {
      method: "GET",
      headers: context.headers,
    },
  );

  if (!response.ok) {
    await throwProblem(response);
  }

  return parseOrThrow(
    (data) => adminIncidentListSchema.parse(data),
    await parseJsonOrThrow(response),
    "GET /api/v1/admin/incidents",
  );
}

export function fetchAdminIncidentDetail(context: ServerRequestContext, incidentId: string, environment?: string): Promise<AdminIncidentDetail>;
export function fetchAdminIncidentDetail(sessionCookie: string, apiUrl: string, incidentId: string, environment?: string): Promise<AdminIncidentDetail>;
export async function fetchAdminIncidentDetail(
  contextOrSessionCookie: ServerRequestContext | string,
  apiUrlOrIncidentId: string,
  incidentIdOrEnvironment?: string,
  environment?: string,
): Promise<AdminIncidentDetail> {
  const isStringOverload = typeof contextOrSessionCookie === "string";
  const context = asServerRequestContext(contextOrSessionCookie, isStringOverload ? apiUrlOrIncidentId : undefined);
  const incidentId = isStringOverload ? (incidentIdOrEnvironment as string) : apiUrlOrIncidentId;
  const resolvedEnvironment = isStringOverload ? environment : incidentIdOrEnvironment;
  const response = await fetch(
    buildUrl(context.apiUrl, `/admin/incidents/${incidentId}`, {
      environment: resolvedEnvironment,
    }),
    {
      method: "GET",
      headers: context.headers,
    },
  );

  if (!response.ok) {
    await throwProblem(response);
  }

  return parseOrThrow(
    (data) => adminIncidentDetailSchema.parse(data),
    await parseJsonOrThrow(response),
    "GET /api/v1/admin/incidents/{incidentId}",
  );
}

export function searchAdminIncidents(context: ServerRequestContext, query: AdminIncidentSearchQuery): Promise<AdminIncidentSearch>;
export function searchAdminIncidents(sessionCookie: string, apiUrl: string, query: AdminIncidentSearchQuery): Promise<AdminIncidentSearch>;
export async function searchAdminIncidents(
  contextOrSessionCookie: ServerRequestContext | string,
  apiUrlOrQuery: string | AdminIncidentSearchQuery,
  query?: AdminIncidentSearchQuery,
): Promise<AdminIncidentSearch> {
  const context = asServerRequestContext(
    contextOrSessionCookie,
    typeof contextOrSessionCookie === "string" ? (apiUrlOrQuery as string) : undefined,
  );
  const resolvedQuery = typeof contextOrSessionCookie === "string"
    ? (query ?? {})
    : (apiUrlOrQuery as AdminIncidentSearchQuery);
  const response = await fetch(
    buildUrl(context.apiUrl, "/admin/incidents/search", {
      environment: resolvedQuery.environment,
      traceId: resolvedQuery.traceId,
      requestId: resolvedQuery.requestId,
      errorCode: resolvedQuery.errorCode,
      from: resolvedQuery.from,
      to: resolvedQuery.to,
      meetingId: resolvedQuery.meetingId,
    }),
    {
      method: "GET",
      headers: context.headers,
    },
  );

  if (!response.ok) {
    await throwProblem(response);
  }

  return parseOrThrow(
    (data) => adminIncidentSearchSchema.parse(data),
    await parseJsonOrThrow(response),
    "GET /api/v1/admin/incidents/search",
  );
}

export function createAdminIncidentTicket(context: MutationRequestContext, incidentId: string, environment?: string): Promise<AdminIncidentTicket>;
export function createAdminIncidentTicket(sessionCookie: string, apiUrl: string, incidentId: string, environment?: string): Promise<AdminIncidentTicket>;
export async function createAdminIncidentTicket(
  contextOrSessionCookie: MutationRequestContext | string,
  apiUrlOrIncidentId: string,
  incidentIdOrEnvironment?: string,
  environment?: string,
): Promise<AdminIncidentTicket> {
  const isStringOverload = typeof contextOrSessionCookie === "string";
  const context = asMutationRequestContext(contextOrSessionCookie, isStringOverload ? apiUrlOrIncidentId : undefined);
  const incidentId = isStringOverload ? (incidentIdOrEnvironment as string) : apiUrlOrIncidentId;
  const resolvedEnvironment = isStringOverload ? environment : incidentIdOrEnvironment;
  const response = await fetch(
    buildUrl(context.apiUrl, `/admin/incidents/${incidentId}/ticket`, {
      environment: resolvedEnvironment,
    }),
    {
      method: "POST",
      headers: context.headers,
    },
  );

  if (!response.ok) {
    await throwProblem(response);
  }

  return parseOrThrow(
    (data) => adminIncidentTicketSchema.parse(data),
    await parseJsonOrThrow(response),
    "POST /api/v1/admin/incidents/{incidentId}/ticket",
  );
}

export function updateAdminIncidentCoordination(
  context: MutationRequestContext,
  incidentId: string,
  input: AdminIncidentCoordinationMutationInput,
): Promise<AdminIncidentCoordination>;
export function updateAdminIncidentCoordination(
  sessionCookie: string,
  apiUrl: string,
  incidentId: string,
  input: AdminIncidentCoordinationMutationInput,
): Promise<AdminIncidentCoordination>;
export async function updateAdminIncidentCoordination(
  contextOrSessionCookie: MutationRequestContext | string,
  apiUrlOrIncidentId: string,
  incidentIdOrInput: string | AdminIncidentCoordinationMutationInput,
  input?: AdminIncidentCoordinationMutationInput,
): Promise<AdminIncidentCoordination> {
  const isStringOverload = typeof contextOrSessionCookie === "string";
  const context = asMutationRequestContext(contextOrSessionCookie, isStringOverload ? apiUrlOrIncidentId : undefined);
  const incidentId = isStringOverload ? (incidentIdOrInput as string) : apiUrlOrIncidentId;
  const resolvedInput = isStringOverload ? (input as AdminIncidentCoordinationMutationInput) : (incidentIdOrInput as AdminIncidentCoordinationMutationInput);
  const owner = normalizeMutationText(resolvedInput.owner);
  const ticketReference = normalizeMutationText(resolvedInput.ticketReference);
  const ticketStatus = normalizeTicketStatus(resolvedInput.ticketReference, resolvedInput.ticketStatus);
  const response = await fetch(
    buildUrl(context.apiUrl, `/admin/incidents/${incidentId}/coordination`, {
      environment: resolvedInput.environment,
    }),
    {
      method: "POST",
      headers: context.headers,
      body: JSON.stringify({
        owner,
        workflowStatus: resolvedInput.workflowStatus,
        ticketReference,
        ticketStatus,
      }),
    },
  );

  if (!response.ok) {
    await throwProblem(response);
  }

  return parseOrThrow(
    (data) => adminIncidentCoordinationSchema.parse(data),
    await parseJsonOrThrow(response),
    "POST /api/v1/admin/incidents/{incidentId}/coordination",
  );
}

export function fetchAdminRoleHistory(context: ServerRequestContext, query: AdminRoleHistoryQuery): Promise<AdminRoleHistory>;
export function fetchAdminRoleHistory(sessionCookie: string, apiUrl: string, query: AdminRoleHistoryQuery): Promise<AdminRoleHistory>;
export async function fetchAdminRoleHistory(
  contextOrSessionCookie: ServerRequestContext | string,
  apiUrlOrQuery: string | AdminRoleHistoryQuery,
  query?: AdminRoleHistoryQuery,
): Promise<AdminRoleHistory> {
  const context = asServerRequestContext(
    contextOrSessionCookie,
    typeof contextOrSessionCookie === "string" ? (apiUrlOrQuery as string) : undefined,
  );
  const resolvedQuery = typeof contextOrSessionCookie === "string"
    ? (query ?? {})
    : (apiUrlOrQuery as AdminRoleHistoryQuery);
  const response = await fetch(
    buildUrl(context.apiUrl, "/admin/role-history", {
      environment: resolvedQuery.environment,
      q: resolvedQuery.q,
      from: resolvedQuery.from,
      to: resolvedQuery.to,
      actionType: resolvedQuery.actionType,
      role: resolvedQuery.role,
      actorId: resolvedQuery.actorId,
      subjectId: resolvedQuery.subjectId,
      roomId: resolvedQuery.roomId,
      meetingId: resolvedQuery.meetingId,
      page: resolvedQuery.page?.toString(),
      pageSize: resolvedQuery.pageSize?.toString(),
    }),
    {
      method: "GET",
      headers: context.headers,
    },
  );

  if (!response.ok) {
    await throwProblem(response);
  }

  return parseOrThrow(
    (data) => adminRoleHistorySchema.parse(data),
    await parseJsonOrThrow(response),
    "GET /api/v1/admin/role-history",
  );
}

export function fetchAdminFrameworkVersions(
  context: ServerRequestContext,
): Promise<AdminFrameworkVersions>;
export function fetchAdminFrameworkVersions(
  sessionCookie: string,
  apiUrl: string,
): Promise<AdminFrameworkVersions>;
export async function fetchAdminFrameworkVersions(
  contextOrSessionCookie: ServerRequestContext | string,
  apiUrl?: string,
): Promise<AdminFrameworkVersions> {
  const context = asServerRequestContext(contextOrSessionCookie, apiUrl);
  const response = await fetch(
    buildUrl(context.apiUrl, "/admin/framework-versions"),
    {
      method: "GET",
      headers: context.headers,
    },
  );

  if (!response.ok) {
    await throwProblem(response);
  }

  return parseOrThrow(
    (data) => adminFrameworkVersionsSchema.parse(data),
    await parseJsonOrThrow(response),
    "GET /api/v1/admin/framework-versions",
  );
}

export function refreshAdminFrameworkVersions(
  context: MutationRequestContext,
): Promise<AdminFrameworkVersions>;
export function refreshAdminFrameworkVersions(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
): Promise<AdminFrameworkVersions>;
export async function refreshAdminFrameworkVersions(
  contextOrSessionCookie: MutationRequestContext | string,
  apiUrl?: string,
  csrfToken = "",
): Promise<AdminFrameworkVersions> {
  const context = asMutationRequestContext(
    contextOrSessionCookie,
    apiUrl,
    csrfToken,
  );
  const response = await fetch(
    buildUrl(context.apiUrl, "/admin/framework-versions/refresh"),
    {
      method: "POST",
      headers: context.headers,
    },
  );

  if (!response.ok) {
    await throwProblem(response);
  }

  return parseOrThrow(
    (data) => adminFrameworkVersionsSchema.parse(data),
    await parseJsonOrThrow(response),
    "POST /api/v1/admin/framework-versions/refresh",
  );
}
