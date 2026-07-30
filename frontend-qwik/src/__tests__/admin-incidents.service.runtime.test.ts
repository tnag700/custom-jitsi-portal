import { afterEach, describe, expect, it, vi } from "vitest";
import {
  AdminDashboardServiceError,
  updateAdminIncidentCoordination,
  createAdminIncidentTicket,
  fetchAdminIncidentDetail,
  fetchAdminIncidents,
  searchAdminIncidents,
} from "../lib/domains/admin/admin.service";

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json",
    },
  });
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("admin incidents service runtime", () => {
  it("fetchAdminIncidents calls bounded incidents endpoint with queue-first view state and parses typed queue metadata", async () => {
    const payload = {
      period: "1h",
      environment: "dev",
      tenantId: "tenant-1",
      generatedAt: "2026-03-18T10:00:00Z",
      selectedView: "active",
      selectedQuickFacet: "scope:room",
      availableViews: [
        {
          token: "active",
          label: "Active",
          summary: "Открытые инциденты для triage",
        },
        {
          token: "critical",
          label: "Critical",
          summary: "Критические сигналы выше остальных",
        },
      ],
      quickFacets: [
        { token: "scope:room", label: "Комнаты", count: 3, active: true },
        {
          token: "severity:critical",
          label: "Critical",
          count: 1,
          active: false,
        },
      ],
      sort: {
        token: "queue",
        label: "Severity + freshness",
        direction: "desc",
      },
      pageSize: 50,
      offset: 0,
      totalElements: 1,
      items: [
        {
          incidentId: "incident-1",
          occurredAt: "2026-03-18T09:58:00Z",
          errorCode: "TOKEN_INVALID",
          category: "TOKEN",
          tenantId: "tenant-1",
          roomId: "room-1",
          meetingId: "meeting-1",
          affectedSubjects: 2,
          severity: "warn",
          affectedEntitySummary:
            "Комната room-1, встреча meeting-1, 2 затронутых субъекта",
          freshnessHint: "Активность 2 минуты назад",
        },
      ],
    };

    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(jsonResponse(payload, 200));

    const result = await fetchAdminIncidents(
      "sess-1",
      "http://localhost:8080/api/v1",
      {
        period: "1h",
        environment: "dev",
        view: "active",
        facet: "scope:room",
        roomId: "room-1",
        severity: "warn",
        limit: 50,
        offset: 0,
      },
    );

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/admin/incidents?period=1h&environment=dev&view=active&facet=scope%3Aroom&roomId=room-1&severity=warn&limit=50&offset=0",
      expect.objectContaining({ method: "GET" }),
    );
    expect(result.items[0]?.incidentId).toBe("incident-1");
    expect(result.selectedView).toBe("active");
    expect(result.quickFacets[0]?.active).toBe(true);
    expect(result.items[0]?.affectedEntitySummary).toContain("Комната room-1");
  });

  it("fetchAdminIncidentDetail parses additive investigation-first detail contract", async () => {
    const payload = {
      incidentId: "incident-1",
      tenantId: "tenant-1",
      environment: "dev",
      errorCode: "TOKEN_INVALID",
      category: "TOKEN",
      severity: "warn",
      summary: "Token incident",
      startedAt: "2026-03-18T09:45:00Z",
      endedAt: "2026-03-18T10:00:00Z",
      affectedAttempts: [
        {
          occurredAt: "2026-03-18T09:58:00Z",
          traceId: "trace-1",
          correlationId: "trace-1",
          subjectDisplay: "subject-1",
          subjectIdFilterValue: "subject-1",
          role: "participant",
          diagnosticResult: "diagnostic",
          roomId: "room-1",
          meetingId: "meeting-1",
          traceUrl: null,
        },
      ],
      summaryBar: {
        title: "Token incident",
        refusalReason: "TOKEN_INVALID / TOKEN",
        affectedScope:
          "Комната room-1, встреча meeting-1, 1 затронутый субъект",
        operationalStatus: "active-investigation",
        timeWindow: "2026-03-18T09:45:00Z → 2026-03-18T10:00:00Z",
        environment: "dev",
      },
      timeline: [
        {
          occurredAt: "2026-03-18T09:58:00Z",
          title: "Повторный отказ входа",
          summary: "participant · subject-1",
          subjectDisplay: "subject-1",
          role: "participant",
          traceId: "trace-1",
          correlationId: "trace-1",
          roomId: "room-1",
          meetingId: "meeting-1",
        },
      ],
      evidence: [
        {
          kind: "diagnostics",
          title: "Diagnostics result",
          status: "available",
          summary: "diagnostic",
          detail: "participant signal retained for investigation",
          traceId: "trace-1",
          correlationId: "trace-1",
          traceUrl: null,
          emptyState: null,
        },
      ],
      relatedLinks: [
        {
          kind: "role-history",
          label: "История ролей по субъекту",
          environment: "dev",
          subjectId: "subject-1",
          roomId: "room-1",
          meetingId: "meeting-1",
          traceId: "trace-1",
          externalUrl: null,
        },
      ],
      nextActions: [
        {
          kind: "queue",
          label: "Вернуться в очередь",
          detail: "Сохранить incident context и продолжить triage",
          target: "queue-return",
          externalUrl: null,
        },
      ],
      coordination: {
        enabled: true,
        availability: "available",
        explanation:
          "Coordination seam remains optional and investigation-first.",
        owner: "lead.support",
        workflowStatus: "investigating",
        ticketReference: "INC-42",
        ticketStatus: "linked",
        ticketUrl: "https://tickets.example.test/INC-42",
        history: [
          {
            occurredAt: "2026-03-18T09:59:00Z",
            actorId: "admin-user",
            actionType: "coordination-updated",
            traceId: "trace-1",
            fromState:
              "owner=<none>; workflowStatus=triage; ticketReference=<none>; ticketStatus=not-linked",
            toState:
              "owner=lead.support; workflowStatus=investigating; ticketReference=INC-42; ticketStatus=linked",
          },
        ],
      },
      ticketing: {
        available: false,
        ticketKey: null,
        ticketUrl: null,
        status: "disabled",
      },
    };

    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(jsonResponse(payload, 200));

    const result = await fetchAdminIncidentDetail(
      "sess-1",
      "http://localhost:8080/api/v1",
      "incident-1",
      "dev",
    );

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/admin/incidents/incident-1?environment=dev",
      expect.objectContaining({ method: "GET" }),
    );
    expect(result.summaryBar.title).toBe("Token incident");
    expect(result.summaryBar.operationalStatus).toBe("active-investigation");
    expect(result.timeline[0]?.title).toBe("Повторный отказ входа");
    expect(result.evidence[0]?.status).toBe("available");
    expect(result.relatedLinks[0]?.kind).toBe("role-history");
    expect(result.nextActions[0]?.target).toBe("queue-return");
    expect(result.coordination.owner).toBe("lead.support");
    expect(result.coordination.workflowStatus).toBe("investigating");
    expect(result.coordination.history[0]?.actionType).toBe(
      "coordination-updated",
    );
    expect(result.ticketing.status).toBe("disabled");
  });

  it("updateAdminIncidentCoordination posts a lightweight coordination mutation and parses audit-friendly response", async () => {
    const payload = {
      enabled: true,
      availability: "available",
      explanation:
        "Coordination seam remains optional and investigation-first.",
      owner: "lead.support",
      workflowStatus: "waiting-external",
      ticketReference: "INC-42",
      ticketStatus: "waiting-external",
      ticketUrl: null,
      history: [
        {
          occurredAt: "2026-03-18T10:02:00Z",
          actorId: "admin-user",
          actionType: "coordination-updated",
          traceId: "trace-admin-incident",
          fromState:
            "owner=<none>; workflowStatus=triage; ticketReference=<none>; ticketStatus=not-linked",
          toState:
            "owner=lead.support; workflowStatus=waiting-external; ticketReference=INC-42; ticketStatus=waiting-external",
        },
      ],
    };

    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(jsonResponse(payload, 200));

    const result = await updateAdminIncidentCoordination(
      {
        apiUrl: "http://localhost:8080/api/v1",
        sessionCookie: "sess-1",
        csrfToken: "csrf-1",
        csrfCookieToken: "csrf-1",
        idempotencyKey: "idem-1",
        headers: { cookie: "JSESSIONID=sess-1", "x-xsrf-token": "csrf-1" },
      },
      "incident-1",
      {
        owner: "lead.support",
        workflowStatus: "investigating",
        environment: "dev",
        ticketReference: "INC-42",
        ticketStatus: "waiting-external",
      },
    );

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/admin/incidents/incident-1/coordination?environment=dev",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          owner: "lead.support",
          workflowStatus: "investigating",
          ticketReference: "INC-42",
          ticketStatus: "waiting-external",
        }),
      }),
    );
    expect(result.workflowStatus).toBe("waiting-external");
    expect(result.ticketReference).toBe("INC-42");
    expect(result.history[0]?.actorId).toBe("admin-user");
  });

  it("updateAdminIncidentCoordination keeps explicit clear semantics for owner and ticket link", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse(
        {
          enabled: true,
          availability: "available",
          explanation:
            "Coordination seam remains optional and investigation-first.",
          owner: null,
          workflowStatus: "triage",
          ticketReference: null,
          ticketStatus: "not-linked",
          ticketUrl: null,
          history: [],
        },
        200,
      ),
    );

    await updateAdminIncidentCoordination(
      "sess-1",
      "http://localhost:8080/api/v1",
      "incident-1",
      {
        owner: "   ",
        workflowStatus: "triage",
        environment: "dev",
        ticketReference: "   ",
        ticketStatus: "waiting-external",
      },
    );

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/admin/incidents/incident-1/coordination?environment=dev",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          owner: null,
          workflowStatus: "triage",
          ticketReference: null,
          ticketStatus: "not-linked",
        }),
      }),
    );
  });

  it("searchAdminIncidents supports traceId lookup and candidate lists", async () => {
    const payload = {
      outcome: "candidate-list",
      incidentId: null,
      detailUrl: null,
      message: "Уточните tenant или entity filters.",
      candidates: [
        {
          incidentId: "incident-1",
          occurredAt: "2026-03-18T09:58:00Z",
          errorCode: "TOKEN_INVALID",
          meetingId: "meeting-1",
        },
      ],
    };

    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(jsonResponse(payload, 200));

    const result = await searchAdminIncidents(
      "sess-1",
      "http://localhost:8080/api/v1",
      {
        environment: "dev",
        traceId: "trace-1",
      },
    );

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/admin/incidents/search?environment=dev&traceId=trace-1",
      expect.objectContaining({ method: "GET" }),
    );
    expect(result.outcome).toBe("candidate-list");
  });

  it("searchAdminIncidents forwards broader bounded filters like meetingId and to", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse(
        {
          outcome: "not-found",
          incidentId: null,
          detailUrl: null,
          message: "Совпадений не найдено.",
          candidates: [],
        },
        200,
      ),
    );

    await searchAdminIncidents("sess-1", "http://localhost:8080/api/v1", {
      environment: "dev",
      errorCode: "TOKEN_INVALID",
      to: "2026-03-18T10:00:00Z",
      meetingId: "meeting-1",
    });

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/admin/incidents/search?environment=dev&errorCode=TOKEN_INVALID&to=2026-03-18T10%3A00%3A00Z&meetingId=meeting-1",
      expect.objectContaining({ method: "GET" }),
    );
  });

  it("createAdminIncidentTicket posts to server-side ticket endpoint", async () => {
    const payload = {
      available: true,
      created: true,
      ticketKey: "INC-42",
      ticketUrl: "https://tickets.example.test/INC-42",
      summary: "TOKEN_INVALID incident",
      message: null,
    };

    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(jsonResponse(payload, 200));

    const result = await createAdminIncidentTicket(
      "sess-1",
      "http://localhost:8080/api/v1",
      "incident-1",
      "dev",
    );

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/admin/incidents/incident-1/ticket?environment=dev",
      expect.objectContaining({ method: "POST" }),
    );
    expect(result.ticketKey).toBe("INC-42");
  });

  it("throws AdminDashboardServiceError for incidents problem details payload", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse(
        {
          title: "Forbidden",
          detail: "Access denied",
          errorCode: "ACCESS_DENIED",
          traceId: "trace-403",
        },
        403,
      ),
    );

    await expect(
      fetchAdminIncidents("sess-1", "http://localhost:8080/api/v1", {
        period: "15m",
      }),
    ).rejects.toBeInstanceOf(AdminDashboardServiceError);
  });
});
