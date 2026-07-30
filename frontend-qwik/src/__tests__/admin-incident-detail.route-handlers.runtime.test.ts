/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
import { beforeEach, describe, expect, it, vi } from "vitest";
import type * as AdminDomain from "~/lib/domains/admin";

const mockFetchAdminIncidentDetail = vi.fn();
const mockCreateAdminIncidentTicket = vi.fn();
const mockUpdateAdminIncidentCoordination = vi.fn();
const mockBuildServerRequestContext = vi.fn();
const mockBuildMutationRequestContext = vi.fn();
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
    Form: () => null,
    routeLoader$: identity,
    routeLoaderQrl: identity,
    routeAction$: identity,
    routeActionQrl: identity,
    zod$: identity,
    useLocation: () => ({
      url: new URL(
        "http://localhost:3000/admin/incidents/incident-1?environment=dev",
      ),
    }),
  };
});

vi.mock("~/lib/domains/auth", () => ({
  resolveAuthRecoveryRedirectPath: mockResolveAuthRecoveryRedirectPath,
}));

vi.mock("~/lib/shared", () => ({
  ApiErrorAlert: () => null,
  RequestStatePanel: () => null,
}));

vi.mock("~/lib/shared/routes/server-handlers", () => ({
  buildServerRequestContext: mockBuildServerRequestContext,
  buildMutationRequestContext: mockBuildMutationRequestContext,
}));

vi.mock("~/lib/domains/admin", async (importOriginal) => {
  const actual = await importOriginal<typeof AdminDomain>();
  return {
    ...actual,
    AdminDashboardServiceError: MockAdminDashboardServiceError,
    fetchAdminIncidentDetail: mockFetchAdminIncidentDetail,
    createAdminIncidentTicket: mockCreateAdminIncidentTicket,
    updateAdminIncidentCoordination: mockUpdateAdminIncidentCoordination,
  };
});

const adminUser = {
  id: "admin-1",
  displayName: "Alice Admin",
  email: "alice@example.test",
  tenant: "tenant-1",
  claims: ["ROLE_ADMIN"],
};

function createIncidentDetail() {
  return {
    incidentId: "incident-1",
    tenantId: "tenant-1",
    environment: "dev",
    errorCode: "ROLE_MISMATCH",
    category: "SSO",
    severity: "warn",
    summary: "Incident summary",
    startedAt: "2026-07-29T10:00:00Z",
    endedAt: "2026-07-29T10:05:00Z",
    affectedAttempts: [],
    summaryBar: {
      title: "Role mismatch",
      refusalReason: "ROLE_MISMATCH / SSO",
      affectedScope: "1 subject",
      operationalStatus: "active-investigation",
      timeWindow: "5m",
      environment: "dev",
    },
    timeline: [],
    evidence: [],
    relatedLinks: [],
    nextActions: [],
    coordination: {
      enabled: true,
      availability: "available",
      explanation: "Optional coordination.",
      owner: null,
      workflowStatus: "triage",
      ticketReference: null,
      ticketStatus: "not-linked",
      ticketUrl: null,
      history: [],
    },
    ticketing: {
      available: true,
      ticketKey: null,
      ticketUrl: null,
      status: "available",
    },
  };
}

function createLoaderCtx(
  urlValue = "http://localhost:3000/admin/incidents/incident-1?environment=dev",
) {
  const redirect = vi.fn((status: number, to: string) => ({
    type: "redirect",
    status,
    to,
  }));
  return {
    sharedMap: new Map<string, unknown>([
      ["user", adminUser],
      ["apiUrl", "http://localhost:8080/api/v1"],
    ]),
    cookie: {},
    params: {
      incidentId: "incident-1",
    },
    query: new URL(urlValue).searchParams,
    url: new URL(urlValue),
    redirect,
  };
}

function createActionCtx(
  user = adminUser,
  urlValue = "http://localhost:3000/admin/incidents/incident-1?environment=dev",
) {
  const redirect = vi.fn((status: number, to: string) => ({
    type: "redirect",
    status,
    to,
  }));
  const fail = vi.fn((status: number, value: unknown) => ({
    failed: true,
    status,
    ...value,
  }));
  return {
    sharedMap: new Map<string, unknown>([["user", user]]),
    cookie: {},
    url: new URL(urlValue),
    redirect,
    fail,
  };
}

describe("admin incident detail route handlers runtime", () => {
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
    mockBuildMutationRequestContext.mockResolvedValue({
      apiUrl: "http://localhost:8080/api/v1",
      sessionCookie: "sess-1",
      csrfToken: "csrf-1",
      headers: {
        Cookie: "JSESSIONID=sess-1",
        "X-CSRF-TOKEN": "csrf-1",
      },
    });
    mockResolveAuthRecoveryRedirectPath.mockReturnValue(
      "/auth?error=AUTH_REQUIRED&mode=recover",
    );
  });

  it("loads the incident with its environment and admin mutation capability", async () => {
    const incident = createIncidentDetail();
    mockFetchAdminIncidentDetail.mockResolvedValue(incident);
    const mod = await import("~/routes/admin/incidents/[incidentId]/index");

    // eslint-disable-next-line @typescript-eslint/await-thenable
    const result = await mod.useIncidentDetail(createLoaderCtx() as never);

    expect(mockFetchAdminIncidentDetail).toHaveBeenCalledWith(
      expect.objectContaining({
        apiUrl: "http://localhost:8080/api/v1",
        sessionCookie: "sess-1",
      }),
      "incident-1",
      "dev",
    );
    expect(result).toEqual({
      incident,
      loadError: null,
      canManageTicket: true,
    });
  });

  it("redirects expired sessions and preserves typed detail failures", async () => {
    const mod = await import("~/routes/admin/incidents/[incidentId]/index");
    mockFetchAdminIncidentDetail.mockRejectedValueOnce(
      new MockAdminDashboardServiceError({
        title: "Unauthorized",
        detail: "Session missing",
        errorCode: "AUTH_REQUIRED",
      }),
    );

    await expect(
      mod.useIncidentDetail(createLoaderCtx() as never),
    ).rejects.toEqual({
      type: "redirect",
      status: 302,
      to: "/auth?error=AUTH_REQUIRED&mode=recover",
    });

    mockFetchAdminIncidentDetail.mockRejectedValueOnce(
      new MockAdminDashboardServiceError({
        title: "Incident unavailable",
        detail: "Retention window expired",
        errorCode: "ADMIN_INCIDENT_NOT_FOUND",
        traceId: "trace-detail-error",
      }),
    );
    // eslint-disable-next-line @typescript-eslint/await-thenable
    const result = await mod.useIncidentDetail(createLoaderCtx() as never);

    expect(result.loadError).toEqual({
      title: "Incident unavailable",
      detail: "Retention window expired",
      errorCode: "ADMIN_INCIDENT_NOT_FOUND",
      traceId: "trace-detail-error",
    });
    expect(result.incident).toBeNull();
  });

  it("rejects incident mutations before building a mutation context for readonly roles", async () => {
    const readonlyUser = {
      ...adminUser,
      claims: ["support-engineer"],
    };
    const mod = await import("~/routes/admin/incidents/[incidentId]/index");

    // eslint-disable-next-line @typescript-eslint/await-thenable
    const ticketResult = await mod.useCreateIncidentTicket(
      {
        incidentId: "incident-1",
        environment: "dev",
      } as never,
      createActionCtx(readonlyUser) as never,
    );
    // eslint-disable-next-line @typescript-eslint/await-thenable
    const coordinationResult = await mod.useUpdateIncidentCoordination(
      {
        incidentId: "incident-1",
        environment: "dev",
        workflowStatus: "triage",
      } as never,
      createActionCtx(readonlyUser) as never,
    );

    expect(ticketResult).toEqual(
      expect.objectContaining({
        failed: true,
        status: 403,
        error: expect.objectContaining({ errorCode: "ACCESS_DENIED" }),
      }),
    );
    expect(coordinationResult).toEqual(
      expect.objectContaining({
        failed: true,
        status: 403,
        error: expect.objectContaining({ errorCode: "ACCESS_DENIED" }),
      }),
    );
    expect(mockBuildMutationRequestContext).not.toHaveBeenCalled();
  });

  it("executes ticket and coordination mutations with the CSRF-aware context", async () => {
    const ticket = {
      available: true,
      created: true,
      ticketKey: "INC-42",
      ticketUrl: "https://tickets.example.test/INC-42",
      summary: "Ticket created",
      message: null,
    };
    const coordination = {
      enabled: true,
      availability: "available",
      explanation: "Optional coordination.",
      owner: "lead.support",
      workflowStatus: "investigating",
      ticketReference: "INC-42",
      ticketStatus: "linked",
      ticketUrl: "https://tickets.example.test/INC-42",
      history: [],
    };
    mockCreateAdminIncidentTicket.mockResolvedValue(ticket);
    mockUpdateAdminIncidentCoordination.mockResolvedValue(coordination);
    const mod = await import("~/routes/admin/incidents/[incidentId]/index");

    // eslint-disable-next-line @typescript-eslint/await-thenable
    const ticketResult = await mod.useCreateIncidentTicket(
      {
        incidentId: "incident-1",
        environment: "dev",
      } as never,
      createActionCtx() as never,
    );
    // eslint-disable-next-line @typescript-eslint/await-thenable
    const coordinationResult = await mod.useUpdateIncidentCoordination(
      {
        incidentId: "incident-1",
        environment: "dev",
        owner: "lead.support",
        workflowStatus: "investigating",
        ticketReference: "INC-42",
        ticketStatus: "linked",
      } as never,
      createActionCtx() as never,
    );

    expect(mockCreateAdminIncidentTicket).toHaveBeenCalledWith(
      expect.objectContaining({ csrfToken: "csrf-1" }),
      "incident-1",
      "dev",
    );
    expect(mockUpdateAdminIncidentCoordination).toHaveBeenCalledWith(
      expect.objectContaining({ csrfToken: "csrf-1" }),
      "incident-1",
      {
        environment: "dev",
        owner: "lead.support",
        workflowStatus: "investigating",
        ticketReference: "INC-42",
        ticketStatus: "linked",
      },
    );
    expect(ticketResult).toEqual({ success: true, ticket });
    expect(coordinationResult).toEqual({ success: true, coordination });
  });
});
