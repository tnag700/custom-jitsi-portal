/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
import { beforeEach, describe, expect, it, vi } from "vitest";
import type * as AdminDomain from "~/lib/domains/admin";

const mockFetchAdminDashboard = vi.fn();
const mockFetchAdminDrillDown = vi.fn();
const mockBuildServerRequestContext = vi.fn();
const mockResolveAuthRecoveryRedirectPath = vi.fn();

class MockAdminDashboardServiceError extends Error {
  payload: {
    title: string;
    detail: string;
    errorCode: string;
    traceId?: string;
  };

  constructor(payload: {
    title: string;
    detail: string;
    errorCode: string;
    traceId?: string;
  }) {
    super(payload.detail);
    this.name = "AdminDashboardServiceError";
    this.payload = payload;
  }
}

vi.mock("@qwik.dev/core", async (importOriginal) => {
  const actual = await importOriginal();
  const identity = <T>(value: T): T => value;
  return {
    ...actual,
    component$: identity,
  };
});

vi.mock("@qwik.dev/router", async (importOriginal) => {
  const actual = await importOriginal();
  const identity = <T>(value: T): T => value;
  return {
    ...actual,
    routeLoader$: identity,
    routeLoaderQrl: identity,
    useLocation: () => ({
      url: new URL("http://localhost:3000/admin?period=1h"),
    }),
  };
});

vi.mock("~/lib/shared", () => ({
  ApiErrorAlert: () => null,
  RequestStatePanel: () => null,
}));

vi.mock("~/lib/shared/routes/server-handlers", () => ({
  buildServerRequestContext: mockBuildServerRequestContext,
}));

vi.mock("~/lib/domains/auth", () => ({
  resolveAuthRecoveryRedirectPath: mockResolveAuthRecoveryRedirectPath,
}));

vi.mock("~/lib/domains/admin", async (importOriginal) => {
  const actual = await importOriginal<typeof AdminDomain>();
  return {
    ...actual,
    fetchAdminDashboard: mockFetchAdminDashboard,
    fetchAdminDrillDown: mockFetchAdminDrillDown,
    AdminDashboardServiceError: MockAdminDashboardServiceError,
  };
});

function createCtx(urlValue = "http://localhost:3000/admin?period=1h") {
  return {
    sharedMap: new Map<string, unknown>([
      ["apiUrl", "http://localhost:8080/api/v1"],
    ]),
    url: new URL(urlValue),
    query: new URL(urlValue).searchParams,
    cookie: {
      get: (name: string) => {
        if (name === "JSESSIONID") {
          return { value: "sess-1" };
        }
        return undefined;
      },
    },
    redirect: (status: number, to: string) => ({
      type: "redirect",
      status,
      to,
    }),
  };
}

function createDashboard() {
  return {
    environment: "prod",
    period: "24h",
    traceId: "trace-1",
    sampleWindowLimited: false,
    priorityBanner: {
      active: true,
      severity: "critical",
      headline: "Config drift",
      summary: "Config drift blocks joins.",
      actionLabel: "Открыть сигнал",
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
    keyServiceStatuses: [],
    latestSpikes: [],
    affectedScopeSummary: [],
    safeStateSummary: {
      stable: false,
      headline: "",
      summary: "",
      actions: [],
      recentResolvedSpikes: [],
    },
  };
}

describe("admin dashboard route loader runtime", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.clearAllMocks();
    mockBuildServerRequestContext.mockReturnValue({
      apiUrl: "http://localhost:8080/api/v1",
      sessionCookie: "sess-1",
      headers: {
        Cookie: "JSESSIONID=sess-1",
      },
    });
    mockResolveAuthRecoveryRedirectPath.mockReturnValue(
      "/auth?error=AUTH_REQUIRED&mode=recover&returnTo=%2Fadmin%3Fperiod%3D1h",
    );
  });

  it("loads one drill-down context from the derived overview selection", async () => {
    const dashboard = createDashboard();
    const drillDown = {
      period: "1h",
      environment: "prod",
      tenantId: "tenant-a",
      generatedAt: "2026-04-03T08:00:00Z",
      selectionType: "errorCode",
      selectionValue: "CONFIG_INCOMPATIBLE",
      entityFilter: {
        roomId: "room-1",
        meetingId: "meeting-1",
      },
      failureCount: 7,
      recentSamples: [],
      sampleWindowLimited: false,
    };
    mockFetchAdminDashboard.mockResolvedValue(dashboard);
    mockFetchAdminDrillDown.mockResolvedValue(drillDown);

    const mod = await import("~/routes/admin/index");
    // The Qwik loader is replaced with an identity mock; its static type no longer reflects the async runtime value.
    // eslint-disable-next-line @typescript-eslint/await-thenable
    const result = await mod.useAdminDashboard(createCtx() as never);

    expect(mockFetchAdminDrillDown).toHaveBeenCalledWith(
      {
        apiUrl: "http://localhost:8080/api/v1",
        sessionCookie: "sess-1",
        headers: {
          Cookie: "JSESSIONID=sess-1",
        },
      },
      {
        period: "1h",
        environment: "prod",
        roomId: "room-1",
        meetingId: "meeting-1",
        errorCode: "CONFIG_INCOMPATIBLE",
        category: "CONFIG",
      },
    );
    expect(result).toEqual({
      dashboard,
      drillDown,
      drillDownError: null,
      loadError: null,
      filters: {
        period: "1h",
        environment: "",
        roomId: "",
        meetingId: "",
        errorCode: "",
        category: "",
      },
    });
  });

  it("keeps the overview summary when drill-down fails with a non-auth service error", async () => {
    const dashboard = createDashboard();
    mockFetchAdminDashboard.mockResolvedValue(dashboard);
    mockFetchAdminDrillDown.mockRejectedValue(
      new MockAdminDashboardServiceError({
        title: "Invalid",
        detail: "Broken drill-down payload",
        errorCode: "ADMIN_DASHBOARD_RESPONSE_INVALID",
      }),
    );

    const mod = await import("~/routes/admin/index");
    // The Qwik loader is replaced with an identity mock; its static type no longer reflects the async runtime value.
    // eslint-disable-next-line @typescript-eslint/await-thenable
    const result = await mod.useAdminDashboard(createCtx() as never);

    expect(result).toEqual({
      dashboard,
      drillDown: null,
      drillDownError: {
        title: "Invalid",
        detail: "Broken drill-down payload",
        errorCode: "ADMIN_DASHBOARD_RESPONSE_INVALID",
      },
      loadError: null,
      filters: {
        period: "1h",
        environment: "",
        roomId: "",
        meetingId: "",
        errorCode: "",
        category: "",
      },
    });
  });

  it("skips the drill-down request for a stable dashboard with healthy services", async () => {
    const dashboard = createDashboard();
    dashboard.priorityBanner.active = false;
    dashboard.priorityBanner.severity = "info";
    dashboard.topDegradations = [];
    dashboard.latestSpikes = [];
    dashboard.affectedScopeSummary = [];
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
    mockFetchAdminDashboard.mockResolvedValue(dashboard);

    const mod = await import("~/routes/admin/index");
    // eslint-disable-next-line @typescript-eslint/await-thenable
    const result = await mod.useAdminDashboard(createCtx() as never);

    expect(mockFetchAdminDrillDown).not.toHaveBeenCalled();
    expect(result).toEqual({
      dashboard,
      drillDown: null,
      drillDownError: null,
      loadError: null,
      filters: {
        period: "1h",
        environment: "",
        roomId: "",
        meetingId: "",
        errorCode: "",
        category: "",
      },
    });
  });

  it("redirects to auth when drill-down discovers a missing session", async () => {
    mockFetchAdminDashboard.mockResolvedValue(createDashboard());
    mockFetchAdminDrillDown.mockRejectedValue(
      new MockAdminDashboardServiceError({
        title: "Unauthorized",
        detail: "Session missing",
        errorCode: "AUTH_REQUIRED",
      }),
    );

    const mod = await import("~/routes/admin/index");

    await expect(mod.useAdminDashboard(createCtx() as never)).rejects.toEqual({
      type: "redirect",
      status: 302,
      to: "/auth?error=AUTH_REQUIRED&mode=recover&returnTo=%2Fadmin%3Fperiod%3D1h",
    });

    expect(mockResolveAuthRecoveryRedirectPath).toHaveBeenCalledWith(
      expect.objectContaining({
        payload: expect.objectContaining({ errorCode: "AUTH_REQUIRED" }),
      }),
      "/admin?period=1h",
    );
  });
});
