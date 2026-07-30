import { describe, expect, it } from "vitest";
import type { SafeUserProfile } from "../lib/domains/auth";
import {
  buildAdminConfigRouteFilters,
  filterAdminConfigSummaries,
  loadAdminConfigLatestRollouts,
  resolveAdminConfigCapability,
  resolveAdminConfigSelectedId,
  shouldLoadAdminConfigDetail,
} from "../lib/domains/admin/admin-config.route-helpers";

describe("admin config route helpers", () => {
  it("maps mixed-case operator claims to the existing capability model", () => {
    const readOnlyUser: SafeUserProfile = {
      id: "user-1",
      displayName: "Operator",
      email: "operator@example.test",
      tenant: "tenant-1",
      claims: ["  System-Admin  ", "viewer"],
    };
    const adminUser: SafeUserProfile = {
      ...readOnlyUser,
      claims: ["ADMIN"],
    };
    const keycloakAdminUser: SafeUserProfile = {
      ...readOnlyUser,
      claims: ["viewer", "ROLE_ADMIN"],
    };

    expect(resolveAdminConfigCapability(readOnlyUser)).toEqual({
      role: "system-admin",
      canMutate: false,
      reason:
        "Роли support-engineer, security-admin и system-admin могут только просматривать конфигурацию. Изменение, развёртывание и откат доступны только роли admin.",
    });
    expect(resolveAdminConfigCapability(adminUser)).toEqual({
      role: "admin",
      canMutate: true,
      reason: null,
    });
    expect(resolveAdminConfigCapability(keycloakAdminUser)).toEqual({
      role: "admin",
      canMutate: true,
      reason: null,
    });
  });

  it("normalizes filters and keeps create mode from triggering detail loading", () => {
    const filters = buildAdminConfigRouteFilters(
      new URLSearchParams({
        environment: " dev ",
        status: " active ",
        mode: " create ",
        configSetId: "   ",
        returnTo: " /admin/incidents?environment=dev ",
      }),
    );

    expect(filters).toEqual({
      environment: "DEV",
      status: "active",
      mode: "create",
      configSetId: "",
      returnTo: "/admin/incidents?environment=dev",
    });
    expect(
      resolveAdminConfigSelectedId(filters, [{ configSetId: "cfg-1" }]),
    ).toBe("cfg-1");
    expect(shouldLoadAdminConfigDetail(filters, "cfg-1")).toBe(false);
  });

  it("filters summaries and tolerates partial rollout fetch failures", async () => {
    const filters = buildAdminConfigRouteFilters(
      new URLSearchParams({
        environment: " dev ",
        status: " active ",
      }),
    );
    const items = [
      {
        configSetId: "cfg-dev",
        environmentType: "dev",
        status: "ACTIVE",
      },
      {
        configSetId: "cfg-prod",
        environmentType: "PROD",
        status: "DRAFT",
      },
    ];

    const filtered = filterAdminConfigSummaries(items as never, {
      environment: filters.environment,
      status: filters.status,
    });

    expect(filtered).toHaveLength(1);
    expect(filtered[0]?.configSetId).toBe("cfg-dev");

    const rollouts = await loadAdminConfigLatestRollouts(
      {
        apiUrl: "http://localhost:8080/api/v1",
        sessionCookie: "sess-1",
        csrfToken: "csrf-1",
        headers: {
          Cookie: "JSESSIONID=sess-1",
          Accept: "application/json",
        },
      },
      "tenant-1",
      items as never,
      async (_context, query) => {
        if (query.environmentType === "DEV") {
          return {
            rolloutId: "rollout-dev",
            configSetId: "cfg-dev",
            previousConfigSetId: null,
            tenantId: "tenant-1",
            environmentType: "DEV",
            status: "SUCCEEDED",
            validationErrors: null,
            startedAt: null,
            completedAt: null,
            actorId: "alice.admin",
          };
        }

        throw new Error("backend unavailable");
      },
    );

    expect(rollouts.get("DEV")?.rolloutId).toBe("rollout-dev");
    expect(rollouts.has("PROD")).toBe(false);
  });
});
