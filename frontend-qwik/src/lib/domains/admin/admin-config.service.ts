import {
  adaptProblemDetails,
  configSetResponseSchema,
} from "../../shared/api";
import {
  asMutationRequestContext,
  asServerRequestContext,
} from "../../shared/routes/server-handlers";
import type {
  MutationRequestContext,
  ServerRequestContext,
} from "../../shared/routes/server-handlers";
import type {
  AdminConfigCompatibility,
  AdminConfigEnvironment,
  AdminConfigSetDetail,
  AdminConfigSetForm,
  AdminConfigSetPage,
  AdminConfigSetRolloutSummary,
} from "./admin-config.types";
import {
  adminConfigCompatibilitySchema,
  adminConfigSetDetailSchema,
  adminConfigSetRolloutSummarySchema,
  normalizeConfigSetPayload,
  mapPagedConfigSetResponse,
} from "./admin-config.types";
export type {
  MutationRequestContext,
  ServerRequestContext,
} from "../../shared/routes/server-handlers";

export interface AdminConfigQuery {
  tenantId: string;
  page?: number;
  size?: number;
}

export interface AdminConfigDetailQuery {
  configSetId: string;
  tenantId: string;
}

export interface AdminConfigRolloutQuery {
  tenantId: string;
  environmentType: AdminConfigEnvironment;
}

export interface AdminConfigMutationTarget {
  configSetId: string;
  tenantId: string;
}

export interface AdminConfigRollbackTarget extends AdminConfigMutationTarget {
  environmentType: AdminConfigEnvironment;
}

type ConfigSetResponse = ReturnType<typeof configSetResponseSchema.parse>;

function requireQuery<T>(value: T | undefined, name: string): T {
  if (value === undefined) {
    throw new AdminConfigServiceError({
      title: "Отсутствует обязательный параметр",
      detail: `Не удалось определить параметр ${name}.`,
      errorCode: "ADMIN_CONFIG_QUERY_MISSING",
    });
  }
  return value;
}

export class AdminConfigServiceError extends Error {
  payload: { title: string; detail: string; errorCode: string; traceId?: string };

  constructor(payload: { title: string; detail: string; errorCode: string; traceId?: string }) {
    super(payload.detail);
    this.name = "AdminConfigServiceError";
    this.payload = payload;
  }
}

function fallbackErrorCode(status: number): string {
  if (status === 401) return "AUTH_REQUIRED";
  if (status === 403) return "ACCESS_DENIED";
  if (status === 404) return "CONFIG_SET_NOT_FOUND";
  if (status >= 500) return "ADMIN_CONFIG_UNAVAILABLE";
  return "ADMIN_CONFIG_UNKNOWN";
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

async function parseJsonOrThrow(response: Response): Promise<unknown> {
  const contentType = response.headers.get("content-type") ?? "";
  if (!contentType.includes("application/json")) {
    return null;
  }
  return response.json();
}

function parseOrThrow<T>(parseFn: (data: unknown) => T, data: unknown, endpoint: string): T {
  try {
    return parseFn(data);
  } catch (error) {
    throw new AdminConfigServiceError({
      title: "Неожиданный формат ответа",
      detail: `${endpoint}: ${error instanceof Error ? error.message : "неверный формат ответа"}`,
      errorCode: "ADMIN_CONFIG_RESPONSE_INVALID",
    });
  }
}

async function throwProblem(response: Response, detail: string): Promise<never> {
  const payload = await adaptProblemDetails(
    response,
    response.status,
    fallbackErrorCode,
    "Ошибка управления конфигурацией",
    detail,
  );
  throw new AdminConfigServiceError(payload);
}

function normalizeEnvironment(value: string): AdminConfigEnvironment {
  return value.trim().toUpperCase() as AdminConfigEnvironment;
}

function withDetail(configSet: ConfigSetResponse, compatibility: AdminConfigCompatibility | null, latestRollout: AdminConfigSetRolloutSummary | null): AdminConfigSetDetail {
  return adminConfigSetDetailSchema.parse({
    ...configSet,
    compatibility,
    latestRollout,
  });
}

export function fetchAdminConfigSets(context: ServerRequestContext, query: AdminConfigQuery): Promise<AdminConfigSetPage>;
export function fetchAdminConfigSets(sessionCookie: string, apiUrl: string, query: AdminConfigQuery): Promise<AdminConfigSetPage>;
export async function fetchAdminConfigSets(
  contextOrSessionCookie: ServerRequestContext | string,
  apiUrlOrQuery: string | AdminConfigQuery,
  query?: AdminConfigQuery,
): Promise<AdminConfigSetPage> {
  const context = asServerRequestContext(
    contextOrSessionCookie,
    typeof contextOrSessionCookie === "string" ? (apiUrlOrQuery as string) : undefined,
  );
  const resolvedQuery = requireQuery(
    typeof contextOrSessionCookie === "string" ? query : (apiUrlOrQuery as AdminConfigQuery | undefined),
    "AdminConfigQuery",
  );
  const response = await fetch(
    buildUrl(context.apiUrl, "/config-sets", {
      tenantId: resolvedQuery.tenantId,
      page: String(resolvedQuery.page ?? 0),
      size: String(resolvedQuery.size ?? 20),
    }),
    {
      method: "GET",
      headers: context.headers,
    },
  );

  if (!response.ok) {
    await throwProblem(response, "Не удалось загрузить список конфиг-наборов.");
  }

  return parseOrThrow(mapPagedConfigSetResponse, await parseJsonOrThrow(response), "GET /api/v1/config-sets");
}

export function fetchLatestAdminConfigSetRollout(context: ServerRequestContext, query: AdminConfigRolloutQuery): Promise<AdminConfigSetRolloutSummary>;
export function fetchLatestAdminConfigSetRollout(sessionCookie: string, apiUrl: string, query: AdminConfigRolloutQuery): Promise<AdminConfigSetRolloutSummary>;
export async function fetchLatestAdminConfigSetRollout(
  contextOrSessionCookie: ServerRequestContext | string,
  apiUrlOrQuery: string | AdminConfigRolloutQuery,
  query?: AdminConfigRolloutQuery,
): Promise<AdminConfigSetRolloutSummary> {
  const context = asServerRequestContext(
    contextOrSessionCookie,
    typeof contextOrSessionCookie === "string" ? (apiUrlOrQuery as string) : undefined,
  );
  const resolvedQuery = requireQuery(
    typeof contextOrSessionCookie === "string" ? query : (apiUrlOrQuery as AdminConfigRolloutQuery | undefined),
    "AdminConfigRolloutQuery",
  );
  const response = await fetch(
    buildUrl(context.apiUrl, "/config-sets/rollouts/latest", {
      tenantId: resolvedQuery.tenantId,
      environmentType: resolvedQuery.environmentType,
    }),
    {
      method: "GET",
      headers: context.headers,
    },
  );

  if (!response.ok) {
    await throwProblem(response, "Не удалось загрузить последний rollout/rollback.");
  }

  return parseOrThrow(
    (data) => adminConfigSetRolloutSummarySchema.parse(data),
    await parseJsonOrThrow(response),
    "GET /api/v1/config-sets/rollouts/latest",
  );
}

export function checkAdminConfigSetCompatibility(context: ServerRequestContext, query: AdminConfigDetailQuery): Promise<AdminConfigCompatibility>;
export function checkAdminConfigSetCompatibility(sessionCookie: string, apiUrl: string, query: AdminConfigDetailQuery): Promise<AdminConfigCompatibility>;
export async function checkAdminConfigSetCompatibility(
  contextOrSessionCookie: ServerRequestContext | string,
  apiUrlOrQuery: string | AdminConfigDetailQuery,
  query?: AdminConfigDetailQuery,
): Promise<AdminConfigCompatibility> {
  const context = asServerRequestContext(
    contextOrSessionCookie,
    typeof contextOrSessionCookie === "string" ? (apiUrlOrQuery as string) : undefined,
  );
  const resolvedQuery = requireQuery(
    typeof contextOrSessionCookie === "string" ? query : (apiUrlOrQuery as AdminConfigDetailQuery | undefined),
    "AdminConfigDetailQuery",
  );
  const response = await fetch(
    buildUrl(context.apiUrl, `/config-sets/${resolvedQuery.configSetId}/compatibility`, {
      tenantId: resolvedQuery.tenantId,
    }),
    {
      method: "GET",
      headers: context.headers,
    },
  );

  if (!response.ok) {
    await throwProblem(response, "Не удалось выполнить compatibility check.");
  }

  return parseOrThrow(
    (data) => adminConfigCompatibilitySchema.parse(data),
    await parseJsonOrThrow(response),
    "GET /api/v1/config-sets/{configSetId}/compatibility",
  );
}

export function fetchAdminConfigSet(context: ServerRequestContext, query: AdminConfigDetailQuery): Promise<AdminConfigSetDetail>;
export function fetchAdminConfigSet(sessionCookie: string, apiUrl: string, query: AdminConfigDetailQuery): Promise<AdminConfigSetDetail>;
export async function fetchAdminConfigSet(
  contextOrSessionCookie: ServerRequestContext | string,
  apiUrlOrQuery: string | AdminConfigDetailQuery,
  query?: AdminConfigDetailQuery,
): Promise<AdminConfigSetDetail> {
  const context = asServerRequestContext(
    contextOrSessionCookie,
    typeof contextOrSessionCookie === "string" ? (apiUrlOrQuery as string) : undefined,
  );
  const resolvedQuery = requireQuery(
    typeof contextOrSessionCookie === "string" ? query : (apiUrlOrQuery as AdminConfigDetailQuery | undefined),
    "AdminConfigDetailQuery",
  );
  const detailResponse = await fetch(`${context.apiUrl}/config-sets/${resolvedQuery.configSetId}`, {
    method: "GET",
    headers: context.headers,
  });

  if (!detailResponse.ok) {
    await throwProblem(detailResponse, "Не удалось загрузить карточку конфиг-набора.");
  }

  const detail = parseOrThrow(
    (data) => configSetResponseSchema.parse(normalizeConfigSetPayload(data)),
    await parseJsonOrThrow(detailResponse),
    "GET /api/v1/config-sets/{configSetId}",
  );
  const compatibility = await checkAdminConfigSetCompatibility(context, resolvedQuery);
  const latestRollout = await fetchLatestAdminConfigSetRollout(context, {
    tenantId: resolvedQuery.tenantId,
    environmentType: normalizeEnvironment(detail.environmentType),
  });
  return withDetail(detail, compatibility, latestRollout);
}

export async function createAdminConfigSet(context: MutationRequestContext, data: AdminConfigSetForm & { tenantId: string }): Promise<ConfigSetResponse> {
  const resolvedContext = asMutationRequestContext(context);
  const response = await fetch(`${resolvedContext.apiUrl}/config-sets`, {
    method: "POST",
    headers: resolvedContext.headers,
    body: JSON.stringify(data),
  });

  if (!response.ok) {
    await throwProblem(response, "Не удалось создать конфиг-набор.");
  }

  return parseOrThrow(
    (payload) => configSetResponseSchema.parse(normalizeConfigSetPayload(payload)),
    await parseJsonOrThrow(response),
    "POST /api/v1/config-sets",
  );
}

export async function updateAdminConfigSet(context: MutationRequestContext, configSetId: string, data: AdminConfigSetForm & { tenantId: string }): Promise<ConfigSetResponse> {
  const resolvedContext = asMutationRequestContext(context);
  const response = await fetch(`${resolvedContext.apiUrl}/config-sets/${configSetId}`, {
    method: "PUT",
    headers: resolvedContext.headers,
    body: JSON.stringify(data),
  });

  if (!response.ok) {
    await throwProblem(response, "Не удалось обновить конфиг-набор.");
  }

  return parseOrThrow(
    (payload) => configSetResponseSchema.parse(normalizeConfigSetPayload(payload)),
    await parseJsonOrThrow(response),
    "PUT /api/v1/config-sets/{configSetId}",
  );
}

export async function rolloutAdminConfigSet(context: MutationRequestContext, target: AdminConfigMutationTarget): Promise<AdminConfigSetRolloutSummary> {
  const resolvedContext = asMutationRequestContext(context);
  const response = await fetch(
    buildUrl(resolvedContext.apiUrl, `/config-sets/${target.configSetId}/rollout`, {
      tenantId: target.tenantId,
    }),
    {
      method: "POST",
      headers: resolvedContext.headers,
    },
  );

  if (!response.ok) {
    await throwProblem(response, "Не удалось запустить rollout.");
  }

  return parseOrThrow(
    (payload) => adminConfigSetRolloutSummarySchema.parse(payload),
    await parseJsonOrThrow(response),
    "POST /api/v1/config-sets/{configSetId}/rollout",
  );
}

export async function rollbackAdminConfigSet(context: MutationRequestContext, target: AdminConfigRollbackTarget): Promise<AdminConfigSetRolloutSummary> {
  const resolvedContext = asMutationRequestContext(context);
  const response = await fetch(
    buildUrl(resolvedContext.apiUrl, `/config-sets/${target.configSetId}/rollback`, {
      tenantId: target.tenantId,
      environmentType: target.environmentType,
    }),
    {
      method: "POST",
      headers: resolvedContext.headers,
    },
  );

  if (!response.ok) {
    await throwProblem(response, "Не удалось выполнить rollback.");
  }

  return parseOrThrow(
    (payload) => adminConfigSetRolloutSummarySchema.parse(payload),
    await parseJsonOrThrow(response),
    "POST /api/v1/config-sets/{configSetId}/rollback",
  );
}