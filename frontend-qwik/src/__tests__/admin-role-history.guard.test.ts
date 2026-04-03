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

describe("Admin Role History Guard: domain service", () => {
  it("admin.service.ts should contain fetchAdminRoleHistory", () => {
    const ts = readSrc("lib/domains/admin/admin.service.ts");
    expect(ts).toContain("fetchAdminRoleHistory");
    expect(ts).toContain("AdminRoleHistoryQuery");
  });

  it("types.ts should define role history schemas", () => {
    const ts = readSrc("lib/domains/admin/types.ts");
    expect(ts).toContain("adminRoleHistoryEntrySchema");
    expect(ts).toContain("adminRoleHistorySchema");
  });
});

describe("Admin Role History Guard: routes", () => {
  it("admin-layout.route-helpers.ts should expose История ролей entry inside secondary admin nav helper", () => {
    const ts = readSrc("lib/domains/admin/admin-layout.route-helpers.ts");
    expect(ts).toContain("buildAdminSecondaryNavItems");
    expect(ts).toContain('label: "История ролей"');
    expect(ts).toContain('"/admin/role-history"');
  });

  it("routes/admin/role-history/index.tsx should use routeLoader$, buildServerRequestContext and deep-link query params", () => {
    const tsx = readSrc("routes/admin/role-history/index.tsx");
    expect(tsx).toContain("routeLoader$");
    expect(tsx).toContain("buildAdminRoleHistoryFilters");
    expect(tsx).toContain("hasAdminRoleHistoryPrimaryFilter");
    expect(tsx).toContain("buildAdminRoleHistoryResetQueryUpdates");
    expect(tsx).toContain("buildAdminRoleHistoryPageQueryUpdates");
    expect(tsx).toContain("normalizePositiveInteger");
    expect(tsx).toContain("normalizeNonNegativeInteger");
    expect(tsx).toContain("buildServerRequestContext");
    expect(tsx).toContain("resolveIncidentReturnTo");
    expect(tsx).toContain("Вторичный модуль");
    expect(tsx).toContain("Сводка");
    expect(tsx).toContain("Инциденты");
    expect(tsx).toContain("subjectId");
    expect(tsx).toContain("meetingId");
    expect(tsx).toContain("roomId");
    expect(tsx).toContain("actionType");
    expect(tsx).toContain("returnTo");
    expect(tsx).toContain("buildAdminRoleHistoryFilters(query)");
    expect(tsx).toContain("const page = normalizeNonNegativeInteger(filters.page, 0)");
    expect(tsx).toContain("const pageSize = normalizePositiveInteger(filters.pageSize, 20)");
    expect(tsx).not.toContain("function hasPrimaryFilter");
  });

  it("routes/admin/incidents/[incidentId]/index.tsx should expose deep-link to История ролей", () => {
    const tsx = readSrc("routes/admin/incidents/[incidentId]/index.tsx");
    expect(tsx).toContain("buildIncidentRelatedHref");
    expect(tsx).toContain("relatedLinks");
    expect(tsx).toContain("Связанный контекст");
  });
});