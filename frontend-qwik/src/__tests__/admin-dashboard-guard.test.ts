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

describe("Admin Dashboard Guard: domain service", () => {
  it("admin.service.ts should contain fetchAdminDashboard, fetchAdminDrillDown and AdminDashboardServiceError", () => {
    const ts = readSrc("lib/domains/admin/admin.service.ts");
    expect(ts).toContain("fetchAdminDashboard");
    expect(ts).toContain("fetchAdminDrillDown");
    expect(ts).toContain("AdminDashboardServiceError");
  });

  it("types.ts should define dashboard and drill-down schemas", () => {
    const ts = readSrc("lib/domains/admin/types.ts");
    expect(ts).toContain("adminDashboardSummarySchema");
    expect(ts).toContain("adminDashboardDrillDownSchema");
    expect(ts).toContain("AdminDashboardErrorPayload");
  });
});

describe("Admin Dashboard Guard: routes", () => {
  it("routes/admin/layout.tsx should contain routeLoader$ with admin cabinet role guard", () => {
    const tsx = readSrc("routes/admin/layout.tsx");
    expect(tsx).toContain("routeLoader$");
    expect(tsx).toContain("buildAdminPrimaryNavItems");
    expect(tsx).toContain("buildAdminSecondaryNavItems");
    expect(tsx).toContain("isActiveAdminNavItem");
    expect(tsx).toContain("hasAdminCabinetAccess");
    expect(tsx).toContain("primaryNavItems");
    expect(tsx).toContain("secondaryNavItems");
    expect(tsx).toContain("Вторичные модули");
    expect(tsx).toContain("buildAdminPrimaryNavItems(environment)");
    expect(tsx).toContain('buildAdminSecondaryNavItems(location.url, environment ?? "")');
    expect(tsx).not.toContain("function hasAdminCabinetAccess");
    expect(tsx).not.toContain("function withEnvironment");
    expect(tsx).not.toContain("function isActiveNavItem");
    expect(tsx).toContain("redirect(302, \"/\")");
  });

  it("routes/admin/index.tsx should keep evidence-first overview with one bounded drill-down context", () => {
    const tsx = readSrc("routes/admin/index.tsx");
    expect(tsx).toContain("routeLoader$");
    expect(tsx).toContain("buildAdminDashboardFilters");
    expect(tsx).toContain("buildAdminDashboardDerivedState");
    expect(tsx).toContain("buildAdminDashboardSelectionHref");
    expect(tsx).toContain("buildAdminDashboardActiveIncidentsHref");
    expect(tsx).toContain("resolveAdminDashboardCardTone");
    expect(tsx).toContain("buildServerRequestContext");
    expect(tsx).toContain("fetchAdminDrillDown");
    expect(tsx).toContain("priorityBanner");
    expect(tsx).toContain("topDegradations");
    expect(tsx).toContain("keyServiceStatuses");
    expect(tsx).toContain("latestSpikes");
    expect(tsx).toContain("affectedScopeSummary");
    expect(tsx).toContain("safeStateSummary");
    expect(tsx).toContain("drillDownError");
    expect(tsx).toContain("activeDrillDownSelection");
    expect(tsx).toContain("Вторичные модули");
    expect(tsx).toContain("dashboardState.secondaryModuleLinks");
    expect(tsx).toContain("buildAdminDashboardFilters(query)");
    expect(tsx).toContain("const dashboardState = buildAdminDashboardDerivedState(location.url, dashboard, filters)");
    expect(tsx).toContain("const activeIncidentsHref = buildAdminDashboardActiveIncidentsHref(");
    expect(tsx).toContain('buildAdminDashboardSelectionHref(location.url, dashboard.priorityBanner.handoff, dashboardState)');
    expect(tsx).toContain("Последние отказы");
    expect(tsx).toContain("ID комнаты");
    expect(tsx).toContain("ID встречи");
    expect(tsx).toContain("Трассировка");
    expect(tsx).not.toContain("function cardTone");
    expect(tsx).not.toContain("<form method=\"get\"");
    expect(tsx).not.toContain('query.get("environment")) || "dev"');
  });

  it("routes/admin/index.tsx should preserve fallback environment in secondary links via derived dashboard state", () => {
    const tsx = readSrc("routes/admin/index.tsx");
    expect(tsx).toContain("dashboardState.activeEnvironment");
    expect(tsx).not.toContain('buildAdminSecondaryHref(location.url, "/admin/role-history", filters.environment)');
  });

  it("routes/admin/index.tsx should render RequestStatePanel and ApiErrorAlert", () => {
    const tsx = readSrc("routes/admin/index.tsx");
    expect(tsx).toContain("RequestStatePanel");
    expect(tsx).toContain("ApiErrorAlert");
  });
});

describe("Admin Dashboard Guard: navigation", () => {
  it("sidebar-nav-items.ts should contain admin navigation entry", () => {
    const ts = readSrc("lib/shared/components/sidebar-nav-items.ts");
    expect(ts).toContain('label: "Админ"');
    expect(ts).toContain('href: "/admin"');
  });

  it("Sidebar.tsx should filter admin navigation by auth claims instead of rendering it globally", () => {
    const tsx = readSrc("lib/shared/components/Sidebar.tsx");
    expect(tsx).toContain("items?: readonly NavItem[]");
    expect(tsx).toContain("const visibleNavItems = items ?? navItems");
    expect(tsx).not.toContain("navItems.map((item)");
  });

  it("routes/layout.tsx should pass auth-filtered nav items into Sidebar", () => {
    const tsx = readSrc("routes/layout.tsx");
    expect(tsx).toContain("filterNavItemsForClaims");
    expect(tsx).toContain("const visibleNavItems = filterNavItemsForClaims(navItems, authStore.profile?.claims ?? [])");
    expect(tsx).toContain("<Sidebar expanded={expanded} items={visibleNavItems} />");
  });
});