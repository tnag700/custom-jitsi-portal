import { describe, expect, it } from "vitest";
import type { AdminDashboardSummary } from "../lib/domains/admin";
import {
  buildAdminDashboardActiveIncidentsHref,
  buildAdminDashboardDerivedState,
  buildAdminDashboardFilters,
  buildAdminDashboardIncidentHandoffHref,
  buildAdminDashboardSelectionHref,
  isHealthyAdminServiceStatus,
  resolveAdminDashboardCardTone,
  resolveAdminServiceStatusTone,
} from "../lib/domains/admin/admin-dashboard.route-helpers";

function createDashboardSummary(
  handoff: AdminDashboardSummary["priorityBanner"]["handoff"],
): AdminDashboardSummary {
  return {
    period: "24h",
    environment: "prod",
    tenantId: "tenant-1",
    generatedAt: "2026-04-03T08:00:00Z",
    traceId: "trace-1",
    priorityBanner: {
      active: true,
      severity: "critical",
      headline: "Config drift",
      summary: "Config drift blocks joins.",
      actionLabel: "Открыть сигнал",
      handoff,
    },
    topDegradations: [],
    latestSpikes: [],
    affectedScopeSummary: [],
    keyServiceStatuses: [],
    safeStateSummary: {
      stable: false,
      headline: "",
      summary: "",
      actions: [],
      recentResolvedSpikes: [],
    },
    entityFilter: {
      roomId: null,
      meetingId: null,
    },
    sampleWindowLimited: false,
  };
}

describe("admin dashboard route helpers", () => {
  it("builds trimmed dashboard filters with default period", () => {
    const query = new URL(
      "https://portal.example.test/admin?period=%201h%20&environment=%20%20&roomId=%20room-1%20&meetingId=%20meeting-1%20",
    ).searchParams;

    expect(buildAdminDashboardFilters(query)).toEqual({
      period: "1h",
      environment: "",
      roomId: "room-1",
      meetingId: "meeting-1",
      errorCode: "",
      category: "",
    });
  });

  it("derives active dashboard context and default drill-down selection", () => {
    const dashboard = createDashboardSummary({
      environment: "",
      period: "",
      severity: "critical",
      errorCode: "CONFIG_INCOMPATIBLE",
      category: "CONFIG",
      roomId: "room-1",
      meetingId: "meeting-1",
      incidentId: null,
    });

    const state = buildAdminDashboardDerivedState(dashboard, {
      period: "1h",
      environment: "",
      roomId: "",
      meetingId: "",
      errorCode: "",
      category: "",
    });

    expect(state.activeEnvironment).toBe("prod");
    expect(state.activePeriod).toBe("1h");
    expect(state.activeDrillDownSelection).toEqual({
      environment: "prod",
      period: "1h",
      severity: "critical",
      errorCode: "CONFIG_INCOMPATIBLE",
      category: "CONFIG",
      roomId: "room-1",
      meetingId: "meeting-1",
      incidentId: "",
    });
  });

  it("does not derive a drill-down request from healthy stable-state cards", () => {
    const dashboard = createDashboardSummary({
      environment: "dev",
      period: "15m",
      severity: "info",
      errorCode: null,
      category: "CONFIG",
      roomId: null,
      meetingId: null,
      incidentId: null,
    });
    dashboard.priorityBanner.active = false;
    dashboard.priorityBanner.severity = "info";
    dashboard.keyServiceStatuses = [
      {
        key: "backend",
        label: "Backend",
        status: "UP",
        detail: "Healthy",
        handoff: {
          environment: "dev",
          period: "15m",
          severity: "info",
          errorCode: null,
          category: "CONFIG",
          roomId: null,
          meetingId: null,
          incidentId: null,
        },
      },
    ];

    const state = buildAdminDashboardDerivedState(dashboard, {
      period: "15m",
      environment: "",
      roomId: "",
      meetingId: "",
      errorCode: "",
      category: "",
    });

    expect(state.activeDrillDownSelection).toBeNull();
  });

  it("builds incident handoff hrefs from the derived dashboard context", () => {
    const currentUrl = new URL("https://portal.example.test/admin?period=1h");

    expect(
      buildAdminDashboardIncidentHandoffHref(
        currentUrl,
        {
          environment: "",
          period: "",
          severity: "critical",
          errorCode: "CONFIG_INCOMPATIBLE",
          category: null,
          roomId: null,
          meetingId: null,
          incidentId: null,
        },
        {
          activeEnvironment: "prod",
          activePeriod: "1h",
        },
      ),
    ).toContain(
      "/admin/incidents?environment=prod&period=1h&view=critical&severity=critical&errorCode=CONFIG_INCOMPATIBLE",
    );
  });

  it("honors explicit drill-down query over the derived default selection", () => {
    const dashboard = createDashboardSummary({
      environment: "prod",
      period: "24h",
      severity: "critical",
      errorCode: "CONFIG_INCOMPATIBLE",
      category: "CONFIG",
      roomId: null,
      meetingId: null,
      incidentId: null,
    });

    const state = buildAdminDashboardDerivedState(dashboard, {
      period: "1h",
      environment: "",
      roomId: "",
      meetingId: "",
      errorCode: "JOIN_BLOCKED",
      category: "AUTH",
    });

    expect(state.activeDrillDownSelection).toEqual({
      environment: "prod",
      period: "1h",
      severity: "",
      errorCode: "JOIN_BLOCKED",
      category: "AUTH",
      roomId: "",
      meetingId: "",
      incidentId: "",
    });
  });

  it("builds dashboard selection hrefs that normalize one active evidence context", () => {
    const currentUrl = new URL(
      "https://portal.example.test/admin?period=1h&environment=prod&errorCode=OLD&category=OLD",
    );

    expect(
      buildAdminDashboardSelectionHref(
        currentUrl,
        {
          environment: "",
          period: "",
          severity: "warning",
          errorCode: "TOKEN_REVOKED",
          category: null,
          roomId: "room-2",
          meetingId: null,
          incidentId: null,
        },
        {
          activeEnvironment: "prod",
          activePeriod: "1h",
        },
      ),
    ).toBe(
      "/admin?period=1h&environment=prod&errorCode=TOKEN_REVOKED&roomId=room-2",
    );
  });

  it("builds incidents handoff from the active drill-down selection", () => {
    const currentUrl = new URL(
      "https://portal.example.test/admin?period=1h&environment=prod",
    );

    expect(
      buildAdminDashboardActiveIncidentsHref(
        currentUrl,
        {
          environment: "prod",
          period: "1h",
          severity: "critical",
          errorCode: "TOKEN_REVOKED",
          category: "AUTH",
          roomId: "room-2",
          meetingId: "meeting-7",
          incidentId: "",
        },
        {
          activeEnvironment: "prod",
          activePeriod: "1h",
        },
      ),
    ).toContain(
      "/admin/incidents?environment=prod&period=1h&view=critical&severity=critical&errorCode=TOKEN_REVOKED&category=AUTH&roomId=room-2&meetingId=meeting-7",
    );
  });

  it("maps dashboard card tone by severity", () => {
    expect(resolveAdminDashboardCardTone("critical")).toContain(
      "border-rose-300",
    );
    expect(resolveAdminDashboardCardTone("warning")).toContain(
      "border-amber-300",
    );
    expect(resolveAdminDashboardCardTone("info")).toBe(
      "border-border bg-surface text-text",
    );
  });

  it("normalizes service health statuses for compact status indicators", () => {
    expect(isHealthyAdminServiceStatus(" up ")).toBe(true);
    expect(isHealthyAdminServiceStatus("READY")).toBe(true);
    expect(isHealthyAdminServiceStatus("DOWN")).toBe(false);
    expect(resolveAdminServiceStatusTone("UP")).toBe("bg-emerald-500");
    expect(resolveAdminServiceStatusTone("DEGRADED")).toBe("bg-rose-500");
  });
});
