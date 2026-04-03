import { describe, expect, it } from "vitest";
import type { AdminDashboardSummary } from "../lib/domains/admin";
import {
  buildAdminDashboardActiveIncidentsHref,
  buildAdminDashboardDerivedState,
  buildAdminDashboardFilters,
  buildAdminDashboardIncidentHandoffHref,
  buildAdminDashboardSelectionHref,
  resolveAdminDashboardCardTone,
} from "../lib/domains/admin/admin-dashboard.route-helpers";

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

  it("derives active dashboard context, default drill-down selection, and fallback environment in secondary links", () => {
    const currentUrl = new URL("https://portal.example.test/admin?period=1h");
    const dashboard = {
      environment: "prod",
      period: "24h",
      priorityBanner: {
        handoff: {
          environment: "",
          period: "",
          severity: "critical",
          errorCode: "CONFIG_INCOMPATIBLE",
          category: "CONFIG",
          roomId: "room-1",
          meetingId: "meeting-1",
          incidentId: null,
        },
      },
      topDegradations: [],
      latestSpikes: [],
      affectedScopeSummary: [],
      keyServiceStatuses: [],
    } as AdminDashboardSummary;

    const state = buildAdminDashboardDerivedState(currentUrl, dashboard, {
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
    expect(state.secondaryModuleLinks).toEqual([
      {
        label: "История ролей",
        href: "/admin/role-history?environment=prod&returnTo=%2Fadmin%3Fperiod%3D1h",
      },
      {
        label: "Конфиг-наборы",
        href: "/admin/config-sets?environment=prod&returnTo=%2Fadmin%3Fperiod%3D1h",
      },
    ]);
  });

  it("builds incident handoff hrefs from the derived dashboard context", () => {
    const currentUrl = new URL("https://portal.example.test/admin?period=1h");

    expect(buildAdminDashboardIncidentHandoffHref(
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
    )).toContain("/admin/incidents?environment=prod&period=1h&view=critical&severity=critical&errorCode=CONFIG_INCOMPATIBLE");
  });

  it("honors explicit drill-down query over the derived default selection", () => {
    const currentUrl = new URL("https://portal.example.test/admin?period=1h&errorCode=JOIN_BLOCKED&category=AUTH");
    const dashboard = {
      environment: "prod",
      period: "24h",
      priorityBanner: {
        handoff: {
          environment: "prod",
          period: "24h",
          severity: "critical",
          errorCode: "CONFIG_INCOMPATIBLE",
          category: "CONFIG",
          roomId: null,
          meetingId: null,
          incidentId: null,
        },
      },
      topDegradations: [],
      latestSpikes: [],
      affectedScopeSummary: [],
      keyServiceStatuses: [],
    } as AdminDashboardSummary;

    const state = buildAdminDashboardDerivedState(currentUrl, dashboard, {
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
    const currentUrl = new URL("https://portal.example.test/admin?period=1h&environment=prod&errorCode=OLD&category=OLD");

    expect(buildAdminDashboardSelectionHref(
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
    )).toBe("/admin?period=1h&environment=prod&errorCode=TOKEN_REVOKED&roomId=room-2");
  });

  it("builds incidents handoff from the active drill-down selection", () => {
    const currentUrl = new URL("https://portal.example.test/admin?period=1h&environment=prod");

    expect(buildAdminDashboardActiveIncidentsHref(
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
    )).toContain("/admin/incidents?environment=prod&period=1h&view=critical&severity=critical&errorCode=TOKEN_REVOKED&category=AUTH&roomId=room-2&meetingId=meeting-7");
  });

  it("maps dashboard card tone by severity", () => {
    expect(resolveAdminDashboardCardTone("critical")).toContain("border-rose-300");
    expect(resolveAdminDashboardCardTone("warning")).toContain("border-amber-300");
    expect(resolveAdminDashboardCardTone("info")).toBe("border-border bg-surface text-text");
  });
});