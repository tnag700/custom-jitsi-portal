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

  it("role history route should stay a thin composition boundary", () => {
    const tsx = readSrc("routes/admin/role-history/index.tsx");
    expect(tsx).toContain("useAdminRoleHistory");
    expect(tsx).toContain("AdminRoleHistoryOverview");
    expect(tsx).toContain("ApiErrorAlert");
    expect(tsx).not.toContain("routeLoader$");
    expect(tsx).not.toContain("<form");
    expect(tsx).not.toContain("fetchAdminRoleHistory");
    expect(tsx.split("\n").length).toBeLessThan(80);
  });

  it("role history loader should own authenticated data loading and bounded pagination", () => {
    const ts = readSrc("routes/admin/role-history/loader.ts");
    expect(ts).toContain("routeLoader$");
    expect(ts).toContain("buildServerRequestContext");
    expect(ts).toContain("buildAdminRoleHistoryFilters");
    expect(ts).toContain("hasAdminRoleHistoryPrimaryFilter");
    expect(ts).toContain("normalizePositiveAdminInteger");
    expect(ts).toContain("normalizeNonNegativeAdminInteger");
    expect(ts).toContain("resolveAuthRecoveryRedirectPath");
    expect(ts).toContain("subjectId");
    expect(ts).toContain("meetingId");
    expect(ts).toContain("roomId");
    expect(ts).toContain("actionType");
    expect(ts).toContain("returnTo");
    expect(ts).toContain("buildAdminRoleHistoryFilters(query)");
    expect(ts).toContain(
      "const page = normalizeNonNegativeAdminInteger(filters.page, 0)",
    );
    expect(ts).toContain(
      "const pageSize = normalizePositiveAdminInteger(filters.pageSize, 20)",
    );
  });

  it("role history overview should separate primary, advanced and technical context", () => {
    const overview = readSrc(
      "lib/domains/admin/components/AdminRoleHistoryOverview.tsx",
    );
    const filters = readSrc(
      "lib/domains/admin/components/AdminRoleHistoryFilterForm.tsx",
    );
    const timeline = readSrc(
      "lib/domains/admin/components/AdminRoleHistoryTimeline.tsx",
    );

    expect(overview).toContain("buildAdminRoleHistoryResetQueryUpdates");
    expect(overview).toContain("buildAdminRoleHistoryPageQueryUpdates");
    expect(overview).toContain("Сначала задайте область поиска");
    expect(overview).not.toContain("Контекст разбора");
    expect(overview).toContain("<h2");
    expect(filters).toContain("Дополнительные фильтры");
    expect(filters).toContain('name="subjectId"');
    expect(filters).toContain('name="meetingId"');
    expect(filters).toContain('name="roomId"');
    expect(filters).toContain('name="actionType"');
    expect(timeline).toContain("Технический контекст");
    expect(timeline).toContain("describeAdminRoleTransition");
  });

  it("AdminIncidentInvestigation should expose deep-link to История ролей", () => {
    const tsx = readSrc(
      "lib/domains/admin/components/AdminIncidentInvestigation.tsx",
    );
    expect(tsx).toContain("buildIncidentRelatedHref");
    expect(tsx).toContain("relatedLinks");
    expect(tsx).toContain("Связанный контекст");
  });
});
