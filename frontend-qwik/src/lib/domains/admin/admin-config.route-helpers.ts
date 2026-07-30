import type { SafeUserProfile } from "../auth";
import { hasPlatformAdminAccess } from "~/lib/shared/security";
import {
  fetchLatestAdminConfigSetRollout,
  type ServerRequestContext,
} from "./admin-config.service";
import { sanitizeAdminQueryValue } from "./admin-route-query";
import type {
  AdminConfigEnvironment,
  AdminConfigSetCapability,
  AdminConfigSetRolloutSummary,
  AdminConfigSetSummary,
} from "./admin-config.types";

const KNOWN_OPERATOR_ROLES = ["support-engineer", "security-admin", "system-admin", "admin"] as const;

export interface AdminConfigRouteFilters {
  environment: string;
  status: string;
  mode: string;
  configSetId: string;
  returnTo: string;
}

type LatestRolloutFetcher = (
  context: ServerRequestContext,
  query: { tenantId: string; environmentType: AdminConfigEnvironment },
) => Promise<AdminConfigSetRolloutSummary>;

export function normalizeAdminConfigEnvironment(value: string): AdminConfigEnvironment {
  return value.trim().toUpperCase() as AdminConfigEnvironment;
}

function resolveOperatorRoleClaims(claims: string[]): string {
  const claim = claims
    .map((item) => item.trim().toLowerCase().replace(/^role_/, ""))
    .find((item) => KNOWN_OPERATOR_ROLES.includes(item as (typeof KNOWN_OPERATOR_ROLES)[number]));
  return claim ?? "support-engineer";
}

export function resolveAdminConfigCapability(user: SafeUserProfile): AdminConfigSetCapability {
  const canMutate = hasPlatformAdminAccess(user.claims);

  return {
    role: resolveOperatorRoleClaims(user.claims),
    canMutate,
    reason: canMutate
      ? null
      : "Роли support-engineer, security-admin и system-admin могут только просматривать конфигурацию. Изменение, развёртывание и откат доступны только роли admin.",
  };
}

export function buildAdminConfigRouteFilters(query: URLSearchParams): AdminConfigRouteFilters {
  const environment = sanitizeAdminQueryValue(query.get("environment"));

  return {
    environment: environment ? normalizeAdminConfigEnvironment(environment) : "",
    status: sanitizeAdminQueryValue(query.get("status")),
    mode: sanitizeAdminQueryValue(query.get("mode")),
    configSetId: sanitizeAdminQueryValue(query.get("configSetId")),
    returnTo: sanitizeAdminQueryValue(query.get("returnTo")),
  };
}

export function filterAdminConfigSummaries(
  items: AdminConfigSetSummary[],
  filters: Pick<AdminConfigRouteFilters, "environment" | "status">,
): AdminConfigSetSummary[] {
  return items
    .filter((item) => (filters.environment ? normalizeAdminConfigEnvironment(item.environmentType) === filters.environment : true))
    .filter((item) => (filters.status ? item.status.toLowerCase() === filters.status.toLowerCase() : true));
}

export function resolveAdminConfigSelectedId(
  filters: Pick<AdminConfigRouteFilters, "configSetId">,
  items: Array<Pick<AdminConfigSetSummary, "configSetId">>,
): string {
  return filters.configSetId || items[0]?.configSetId || "";
}

export function shouldLoadAdminConfigDetail(
  filters: Pick<AdminConfigRouteFilters, "mode">,
  selectedConfigId: string,
): boolean {
  return filters.mode !== "create" && selectedConfigId.trim().length > 0;
}

export async function loadAdminConfigLatestRollouts(
  requestContext: ServerRequestContext,
  tenantId: string,
  items: Array<Pick<AdminConfigSetSummary, "environmentType">>,
  fetchLatestRollout: LatestRolloutFetcher = fetchLatestAdminConfigSetRollout,
): Promise<Map<string, AdminConfigSetRolloutSummary>> {
  const environments = [...new Set(items.map((item) => normalizeAdminConfigEnvironment(item.environmentType)))];
  const results = await Promise.allSettled(
    environments.map(async (environmentType) => ({
      environmentType,
      rollout: await fetchLatestRollout(requestContext, { tenantId, environmentType }),
    })),
  );

  const byEnvironment = new Map<string, AdminConfigSetRolloutSummary>();
  results.forEach((result) => {
    if (result.status === "fulfilled") {
      byEnvironment.set(result.value.environmentType, result.value.rollout);
    }
  });
  return byEnvironment;
}
