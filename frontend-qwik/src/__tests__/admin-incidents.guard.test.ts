import { describe, expect, it } from "vitest";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

const SRC_DIR = join(__dirname, "..");

function readSrc(relativePath: string): string {
  const full = join(SRC_DIR, relativePath);
  if (!existsSync(full)) {
    throw new Error(`File not found: ${relativePath}`);
  }
  return readFileSync(full, "utf-8");
}

describe("Admin Incidents Guard: domain service", () => {
  it("admin.service.ts should contain incidents list/detail/search/ticket functions", () => {
    const ts = readSrc("lib/domains/admin/admin.service.ts");
    expect(ts).toContain("fetchAdminIncidents");
    expect(ts).toContain("fetchAdminIncidentDetail");
    expect(ts).toContain("searchAdminIncidents");
    expect(ts).toContain("createAdminIncidentTicket");
    expect(ts).toContain("updateAdminIncidentCoordination");
  });

  it("types.ts should define incidents schemas", () => {
    const ts = readSrc("lib/domains/admin/types.ts");
    expect(ts).toContain("adminIncidentListSchema");
    expect(ts).toContain("adminIncidentDetailSchema");
    expect(ts).toContain("adminIncidentSearchSchema");
    expect(ts).toContain("adminIncidentTicketSchema");
    expect(ts).toContain("adminIncidentCoordinationSchema");
  });
});

describe("Admin Incidents Guard: routes", () => {
  it("routes/admin/incidents/index.tsx should contain routeLoader$, queue-first state and deep-link query params", () => {
    const tsx = readSrc("routes/admin/incidents/index.tsx");
    expect(tsx).toContain("routeLoader$");
    expect(tsx).toContain("buildIncidentQueueFilters");
    expect(tsx).toContain("buildIncidentQueueDerivedState");
    expect(tsx).toContain("buildIncidentQueueViewQueryUpdates");
    expect(tsx).toContain("buildIncidentQueueFacetQueryUpdates");
    expect(tsx).toContain("buildIncidentQueueResetFiltersQueryUpdates");
    expect(tsx).toContain("resolveIncidentRelativeTimeLabel");
    expect(tsx).toContain("normalizePositiveInteger");
    expect(tsx).toContain("normalizeNonNegativeInteger");
    expect(tsx).toContain("buildAdminSecondaryHref");
    expect(tsx).toContain("buildServerRequestContext");
    expect(tsx).toContain("buildIncidentQueueFilters(query)");
    expect(tsx).toContain("selectedView");
    expect(tsx).toContain("availableViews.map");
    expect(tsx).toContain("quickFacets");
    expect(tsx).toContain("Расширенные фильтры");
    expect(tsx).toContain("<details");
    expect(tsx).toContain("traceId");
    expect(tsx).toContain("requestId");
    expect(tsx).toContain("roomId");
    expect(tsx).toContain("meetingId");
    expect(tsx).toContain("Вторичные модули");
    expect(tsx).toContain("/admin/role-history");
    expect(tsx).toContain("/admin/config-sets");
    expect(tsx).toContain("limit");
    expect(tsx).toContain("offset");
    expect(tsx).toContain("buildIncidentDetailHref");
    expect(tsx).toContain("const limit = normalizePositiveInteger(filters.limit, 50)");
    expect(tsx).toContain("const offset = normalizeNonNegativeInteger(filters.offset, 0)");
    expect(tsx).toContain("affectedEntitySummary");
    expect(tsx).toContain("freshnessHint");
    expect(tsx).not.toContain("function resolveRelativeTimeLabel");
    expect(tsx).not.toContain("function hasAdvancedFilters");
    expect(tsx).not.toContain("function findSavedViewLabel");
    expect(tsx).not.toContain("function findQuickFacetLabel");
    expect(tsx).not.toContain("encodeReturnTo");
    expect(tsx).not.toContain('query.get("environment")) || "dev"');
  });

  it("routes/admin/incidents/[incidentId]/index.tsx should render investigation-first workspace with summary, evidence and next actions", () => {
    const tsx = readSrc("routes/admin/incidents/[incidentId]/index.tsx");
    expect(tsx).toContain("action$");
    expect(tsx).toContain("buildIncidentDetailDerivedState");
    expect(tsx).toContain("getIncidentActionError");
    expect(tsx).toContain("getIncidentTicketActionResult");
    expect(tsx).toContain("getIncidentCoordinationActionResult");
    expect(tsx).toContain("canManageIncidentTicket");
    expect(tsx).toContain("Triage context");
    expect(tsx).toContain("summaryBar");
    expect(tsx).toContain("timeline");
    expect(tsx).toContain("evidence");
    expect(tsx).toContain("relatedLinks");
    expect(tsx).toContain("nextActions");
    expect(tsx).toContain("Краткая сводка");
    expect(tsx).toContain("Таймлайн сигналов");
    expect(tsx).toContain("Связанный контекст");
    expect(tsx).toContain("Следующие шаги");
    expect(tsx).toContain("Технические детали");
    expect(tsx).toContain("createAdminIncidentTicket");
    expect(tsx).toContain("updateAdminIncidentCoordination");
    expect(tsx).toContain("canManageTicket");
    expect(tsx).toContain("Coordination seam");
    expect(tsx).toContain("workflowStatus");
    expect(tsx).toContain("owner");
    expect(tsx).toContain('name="ticketReference"');
    expect(tsx).toContain('name="ticketStatus"');
    expect(tsx).toContain("снять привязку существующего тикета");
    expect(tsx).toContain("только для роли admin");
    expect(tsx).toContain("ApiErrorAlert");
    expect(tsx).toContain("RequestStatePanel");
    expect(tsx).toContain("buildIncidentRelatedHref");
    expect(tsx).not.toContain("function canManageIncidentTicket");
    expect(tsx).not.toContain("COORDINATION_STATUS_LABELS");
    expect(tsx).not.toContain('query.get("environment")?.trim() || "dev"');
  });

  it("routes/admin/incidents/index.tsx should preserve effective environment in redirects and secondary links", () => {
    const tsx = readSrc("routes/admin/incidents/index.tsx");
    expect(tsx).toContain("const queueState = buildIncidentQueueDerivedState(incidents, filters)");
    expect(tsx).toContain('buildAdminSecondaryHref(location.url, "/admin/role-history", queueState.effectiveEnvironment)');
    expect(tsx).toContain('buildIncidentDetailHref(location.url, candidate.incidentId, queueState.effectiveEnvironment)');
    expect(tsx).not.toContain('buildAdminSecondaryHref(location.url, "/admin/role-history", filters.environment)');
  });
});