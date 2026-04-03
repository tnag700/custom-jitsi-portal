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

describe("Admin Config Guard: domain service", () => {
  it("admin-config.service.ts should contain config set list/detail/mutation functions", () => {
    const ts = readSrc("lib/domains/admin/admin-config.service.ts");
    expect(ts).toContain("fetchAdminConfigSets");
    expect(ts).toContain("fetchAdminConfigSet");
    expect(ts).toContain("createAdminConfigSet");
    expect(ts).toContain("updateAdminConfigSet");
    expect(ts).toContain("rolloutAdminConfigSet");
    expect(ts).toContain("rollbackAdminConfigSet");
    expect(ts).toContain("checkAdminConfigSetCompatibility");
  });

  it("admin-config.types.ts should define config set schemas and role-aware capability shape", () => {
    const ts = readSrc("lib/domains/admin/admin-config.types.ts");
    expect(ts).toContain("adminConfigSetSummarySchema");
    expect(ts).toContain("adminConfigSetPageSchema");
    expect(ts).toContain("adminConfigSetCapabilitySchema");
    expect(ts).toContain("adminConfigSetRolloutSummarySchema");
    expect(ts).toContain("adminConfigOperationResultSchema");
  });
});

describe("Admin Config Guard: routes", () => {
  it("admin-config.route-helpers.ts should keep capability and rollout helper seams", () => {
    const ts = readSrc("lib/domains/admin/admin-config.route-helpers.ts");
    expect(ts).toContain("resolveAdminConfigCapability");
    expect(ts).toContain("loadAdminConfigLatestRollouts");
    expect(ts).toContain("support-engineer");
    expect(ts).toContain("system-admin");
  });

  it("admin-layout.route-helpers.ts should expose Конфиг-наборы entry inside secondary admin nav helper", () => {
    const ts = readSrc("lib/domains/admin/admin-layout.route-helpers.ts");
    expect(ts).toContain("buildAdminSecondaryNavItems");
    expect(ts).toContain('label: "Конфиг-наборы"');
    expect(ts).toContain('"/admin/config-sets"');
  });

  it("routes/admin/config-sets/index.tsx should use routeLoader$, routeAction$ and shared server request helpers", () => {
    const tsx = readSrc("routes/admin/config-sets/index.tsx");
    expect(tsx).toContain("routeLoader$");
    expect(tsx).toContain("routeAction$");
    expect(tsx).toContain("resolveIncidentReturnTo");
    expect(tsx).toContain("buildAdminConfigRouteFilters");
    expect(tsx).toContain("resolveAdminConfigCapability");
    expect(tsx).toContain("loadAdminConfigLatestRollouts");
    expect(tsx).toContain("Вторичный модуль");
    expect(tsx).toContain("Вернуться к очереди инцидентов");
    expect(tsx).toContain("buildServerRequestContext");
    expect(tsx).toContain("buildMutationRequestContext");
    expect(tsx).toContain("ApiErrorAlert");
    expect(tsx).toContain("RequestStatePanel");
    expect(tsx).toContain("ProblemDetail");
    expect(tsx).toContain("Конфиг-наборы");
    expect(tsx).toContain("Совместимость");
    expect(tsx).toContain("Развёртывание и откат");
    expect(tsx).toContain("Выполнить откат");
    expect(tsx).not.toContain("/api/v1/admin/roles");
  });
});