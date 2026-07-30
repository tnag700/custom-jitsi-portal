/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
import { beforeEach, describe, expect, it, vi } from "vitest";
import type * as AdminDomain from "~/lib/domains/admin";

const mockFetchAdminIncidents = vi.fn();
const mockSearchAdminIncidents = vi.fn();
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
  return {
    ...actual,
    component$: <T>(value: T): T => value,
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
      url: new URL("http://localhost:3000/admin/incidents"),
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
    AdminIncidentQueueOverview: () => null,
    fetchAdminIncidents: mockFetchAdminIncidents,
    searchAdminIncidents: mockSearchAdminIncidents,
    AdminDashboardServiceError: MockAdminDashboardServiceError,
  };
});

function createIncidents() {
  return {
    period: "1h",
    environment: "prod",
    tenantId: "tenant-1",
    generatedAt: "2026-07-29T12:00:00Z",
    selectedView: "critical",
    selectedQuickFacet: null,
    availableViews: [
      {
        token: "critical",
        label: "Critical",
        summary: "Critical incidents.",
      },
    ],
    quickFacets: [],
    sort: {
      token: "severity-freshness",
      label: "Severity + freshness",
      direction: "desc",
    },
    pageSize: 25,
    offset: 5,
    totalElements: 0,
    items: [],
  };
}

function createCtx(
  urlValue = "http://localhost:3000/admin/incidents?period=1h&view=critical&limit=25&offset=5",
) {
  const redirect = vi.fn((status: number, to: string) => ({
    type: "redirect",
    status,
    to,
  }));

  return {
    sharedMap: new Map<string, unknown>([
      ["apiUrl", "http://localhost:8080/api/v1"],
    ]),
    url: new URL(urlValue),
    query: new URL(urlValue).searchParams,
    cookie: {
      get: (name: string) =>
        name === "JSESSIONID" ? { value: "sess-1" } : undefined,
    },
    redirect,
  };
}

describe("admin incidents route loader runtime", () => {
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
      "/auth?error=AUTH_REQUIRED&mode=recover&returnTo=%2Fadmin%2Fincidents",
    );
  });

  it("loads the bounded queue without invoking exact search", async () => {
    const incidents = createIncidents();
    mockFetchAdminIncidents.mockResolvedValue(incidents);

    const mod = await import("~/routes/admin/incidents/index");
    // eslint-disable-next-line @typescript-eslint/await-thenable
    const result = await mod.useAdminIncidents(createCtx() as never);

    expect(mockFetchAdminIncidents).toHaveBeenCalledWith(
      {
        apiUrl: "http://localhost:8080/api/v1",
        sessionCookie: "sess-1",
        headers: {
          Cookie: "JSESSIONID=sess-1",
        },
      },
      {
        period: "1h",
        environment: "",
        view: "critical",
        facet: undefined,
        roomId: undefined,
        meetingId: undefined,
        subjectId: undefined,
        errorCode: undefined,
        category: undefined,
        severity: undefined,
        limit: 25,
        offset: 5,
      },
    );
    expect(mockSearchAdminIncidents).not.toHaveBeenCalled();
    expect(result).toEqual({
      incidents,
      searchResult: null,
      loadError: null,
      filters: expect.objectContaining({
        period: "1h",
        view: "critical",
        limit: "25",
        offset: "5",
      }),
    });
  });

  it("runs exact search only for search inputs and redirects an exact match with queue return context", async () => {
    mockFetchAdminIncidents.mockResolvedValue(createIncidents());
    mockSearchAdminIncidents.mockResolvedValue({
      outcome: "exact-match",
      incidentId: "incident-7",
      message: null,
      candidates: [],
    });
    const ctx = createCtx(
      "http://localhost:3000/admin/incidents?period=1h&view=critical&traceId=trace-7&requestId=request-7",
    );

    const mod = await import("~/routes/admin/incidents/index");

    await expect(mod.useAdminIncidents(ctx as never)).rejects.toMatchObject({
      type: "redirect",
      status: 302,
      to: expect.stringContaining(
        "/admin/incidents/incident-7?environment=prod&returnTo=",
      ),
    });
    expect(mockSearchAdminIncidents).toHaveBeenCalledWith(
      expect.objectContaining({
        apiUrl: "http://localhost:8080/api/v1",
      }),
      {
        environment: "",
        traceId: "trace-7",
        requestId: "request-7",
        errorCode: undefined,
        from: undefined,
        to: undefined,
        meetingId: undefined,
      },
    );
    expect(ctx.redirect).toHaveBeenCalledWith(
      302,
      "/admin/incidents/incident-7?environment=prod&returnTo=%2Fadmin%2Fincidents%3Fperiod%3D1h%26view%3Dcritical",
    );
  });

  it("returns typed service failures without losing the parsed filters", async () => {
    mockFetchAdminIncidents.mockRejectedValue(
      new MockAdminDashboardServiceError({
        title: "Invalid response",
        detail: "Queue contract mismatch",
        errorCode: "ADMIN_INCIDENTS_RESPONSE_INVALID",
        traceId: "trace-error",
      }),
    );

    const mod = await import("~/routes/admin/incidents/index");
    // eslint-disable-next-line @typescript-eslint/await-thenable
    const result = await mod.useAdminIncidents(createCtx() as never);

    expect(result).toEqual({
      incidents: null,
      searchResult: null,
      loadError: {
        title: "Invalid response",
        detail: "Queue contract mismatch",
        errorCode: "ADMIN_INCIDENTS_RESPONSE_INVALID",
        traceId: "trace-error",
      },
      filters: expect.objectContaining({
        period: "1h",
        view: "critical",
      }),
    });
  });

  it("redirects authentication and authorization failures through their recovery paths", async () => {
    const mod = await import("~/routes/admin/incidents/index");
    mockFetchAdminIncidents.mockRejectedValueOnce(
      new MockAdminDashboardServiceError({
        title: "Unauthorized",
        detail: "Session missing",
        errorCode: "AUTH_REQUIRED",
      }),
    );

    await expect(
      mod.useAdminIncidents(
        createCtx("http://localhost:3000/admin/incidents") as never,
      ),
    ).rejects.toEqual({
      type: "redirect",
      status: 302,
      to: "/auth?error=AUTH_REQUIRED&mode=recover&returnTo=%2Fadmin%2Fincidents",
    });
    expect(mockResolveAuthRecoveryRedirectPath).toHaveBeenCalledWith(
      expect.objectContaining({
        payload: expect.objectContaining({ errorCode: "AUTH_REQUIRED" }),
      }),
      "/admin/incidents",
    );

    mockFetchAdminIncidents.mockRejectedValueOnce(
      new MockAdminDashboardServiceError({
        title: "Forbidden",
        detail: "Admin role required",
        errorCode: "ACCESS_DENIED",
      }),
    );
    await expect(
      mod.useAdminIncidents(
        createCtx("http://localhost:3000/admin/incidents") as never,
      ),
    ).rejects.toEqual({
      type: "redirect",
      status: 302,
      to: "/",
    });
  });
});
