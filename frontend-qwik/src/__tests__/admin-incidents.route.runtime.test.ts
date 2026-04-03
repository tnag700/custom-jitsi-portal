import { describe, expect, it } from "vitest";
import {
  buildIncidentQueueDerivedState,
  buildIncidentQueueFacetQueryUpdates,
  buildIncidentQueueFilters,
  buildIncidentQueueResetFiltersQueryUpdates,
  buildDashboardIncidentHref,
  buildIncidentDetailDerivedState,
  buildIncidentDetailHref,
  buildIncidentNextActionHref,
  buildIncidentQueueReturnHref,
  buildIncidentQueueViewQueryUpdates,
  buildIncidentRelatedHref,
  canManageIncidentTicket,
  getIncidentActionError,
  getIncidentCoordinationActionResult,
  getIncidentTicketActionResult,
  hasIncidentSearchQuery,
  resolveIncidentRelativeTimeLabel,
  resolveIncidentReturnTo,
} from "../lib/domains/admin/admin-incidents.route-helpers";
import type { SafeUserProfile } from "../lib/domains/auth";
import type { AdminDashboardErrorPayload, AdminIncidentCoordination, AdminIncidentDetail, AdminIncidentList, AdminIncidentTicket } from "../lib/domains/admin";

function createIncidentDetail(): AdminIncidentDetail {
  return {
    incidentId: "incident-1",
    tenantId: "tenant-1",
    environment: "dev",
    errorCode: "TOKEN_INVALID",
    category: "AUTH",
    severity: "critical",
    summary: "Incident summary",
    startedAt: "2026-03-18T09:00:00Z",
    endedAt: "2026-03-18T10:00:00Z",
    affectedAttempts: [],
    summaryBar: {
      title: "Summary",
      refusalReason: "Access denied",
      affectedScope: "tenant",
      operationalStatus: "degraded",
      timeWindow: "1h",
      environment: "dev",
    },
    timeline: [],
    evidence: [],
    relatedLinks: [],
    nextActions: [],
    coordination: {
      enabled: true,
      availability: "available",
      explanation: "Coordination seam remains optional and investigation-first.",
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
      status: "disabled",
    },
  };
}

describe("admin incidents route helpers", () => {
  it("detects admin-only incident mutation capability from mixed-case claims", () => {
    const adminUser = { claims: ["viewer", " Role_Admin "] } as SafeUserProfile;
    const readonlyUser = { claims: ["support-engineer"] } as SafeUserProfile;

    expect(canManageIncidentTicket(adminUser)).toBe(true);
    expect(canManageIncidentTicket(readonlyUser)).toBe(false);
    expect(canManageIncidentTicket(null)).toBe(false);
  });

  it("extracts incident action success payloads and error payloads", () => {
    const ticket = {
      available: true,
      created: true,
      ticketKey: "INC-42",
      ticketUrl: "https://tickets.example.test/INC-42",
    } as AdminIncidentTicket;
    const coordination = {
      enabled: true,
      availability: "available",
      explanation: "Coordination seam remains optional and investigation-first.",
      owner: "lead.support",
      workflowStatus: "investigating",
      ticketReference: "INC-42",
      ticketStatus: "linked",
      ticketUrl: "https://tickets.example.test/INC-42",
      history: [],
    } as AdminIncidentCoordination;
    const error = {
      title: "Недостаточно прав",
      detail: "Создание external ticket доступно только admin.",
      errorCode: "ACCESS_DENIED",
    } as AdminDashboardErrorPayload;

    expect(getIncidentTicketActionResult({ success: true, ticket })).toEqual(ticket);
    expect(getIncidentCoordinationActionResult({ success: true, coordination })).toEqual(coordination);
    expect(getIncidentActionError({ error })).toEqual(error);
    expect(getIncidentTicketActionResult({ error })).toBeNull();
  });

  it("derives fallback ticket state when ticket action succeeds before coordination update", () => {
    const incident = createIncidentDetail();
    const ticket = {
      available: true,
      created: true,
      ticketKey: "INC-42",
      ticketUrl: "https://tickets.example.test/INC-42",
    } as AdminIncidentTicket;

    const result = buildIncidentDetailDerivedState(incident, ticket, null);

    expect(result.coordination.ticketReference).toBe("INC-42");
    expect(result.ticketing.status).toBe("created");
    expect(result.effectiveTicketReference).toBe("INC-42");
    expect(result.effectiveTicketStatus).toBe("linked");
  });

  it("keeps coordination status precedence while preserving ticket fallback reference data", () => {
    const incident = createIncidentDetail();
    const ticket = {
      available: true,
      created: true,
      ticketKey: "INC-42",
      ticketUrl: "https://tickets.example.test/INC-42",
    } as AdminIncidentTicket;
    const coordination = {
      enabled: true,
      availability: "available",
      explanation: "Coordination seam remains optional and investigation-first.",
      owner: "lead.support",
      workflowStatus: "resolved",
      ticketReference: null,
      ticketStatus: "not-linked",
      ticketUrl: null,
      history: [],
    } as AdminIncidentCoordination;

    const result = buildIncidentDetailDerivedState(incident, ticket, coordination);

    expect(result.coordination.owner).toBe("lead.support");
    expect(result.effectiveTicketReference).toBe("INC-42");
    expect(result.effectiveTicketUrl).toBe("https://tickets.example.test/INC-42");
    expect(result.effectiveTicketStatus).toBe("not-linked");
  });

  it("builds queue filters with trimmed values while preserving raw numeric strings for later normalization", () => {
    const query = new URL(
      "https://portal.example.test/admin/incidents?period=%201h%20&environment=%20%20&traceId=%20trace-1%20&meetingId=%20meeting-1%20&limit=%20%20&offset=%20-5%20",
    ).searchParams;

    expect(buildIncidentQueueFilters(query)).toEqual({
      period: "1h",
      environment: "",
      view: "",
      facet: "",
      roomId: "",
      meetingId: "meeting-1",
      subjectId: "",
      errorCode: "",
      category: "",
      severity: "",
      traceId: "trace-1",
      requestId: "",
      from: "",
      to: "",
      limit: "50",
      offset: "-5",
    });
  });

  it("derives queue state with server environment fallback, token fallback labels and advanced filters", () => {
    const incidents = {
      environment: "prod",
      selectedView: "unknown-view",
      selectedQuickFacet: "custom-facet",
      availableViews: [{ token: "critical", label: "Critical", summary: "Only critical incidents" }],
      quickFacets: [{ token: "stale", label: "Stale", count: 2, active: false }],
    } as AdminIncidentList;

    const state = buildIncidentQueueDerivedState(incidents, {
      period: "15m",
      environment: "",
      view: "",
      facet: "",
      roomId: "",
      meetingId: "",
      subjectId: "",
      errorCode: "",
      category: "CONFIG",
      severity: "",
      traceId: "trace-1",
      requestId: "",
      from: "",
      to: "",
      limit: "50",
      offset: "0",
    });

    expect(state.selectedEnvironment).toBe("prod");
    expect(state.effectiveEnvironment).toBe("prod");
    expect(state.activeViewLabel).toBe("unknown-view");
    expect(state.activeFacetLabel).toBe("custom-facet");
    expect(state.advancedFiltersOpen).toBe(true);
  });

  it("builds queue query updates for saved views, facet toggles and filter reset", () => {
    const filters = {
      period: "24h",
      environment: "prod",
      view: "triage",
      facet: "stale",
      roomId: "room-1",
      meetingId: "meeting-1",
      subjectId: "subject-1",
      errorCode: "TOKEN_INVALID",
      category: "AUTH",
      severity: "critical",
      traceId: "trace-1",
      requestId: "req-1",
      from: "2026-03-18T09:00:00Z",
      to: "2026-03-18T10:00:00Z",
      limit: "100",
      offset: "25",
    };

    expect(buildIncidentQueueViewQueryUpdates(filters, "critical")).toEqual({
      period: "24h",
      environment: "prod",
      view: "critical",
      facet: null,
      roomId: null,
      meetingId: null,
      subjectId: null,
      errorCode: null,
      category: null,
      severity: null,
      traceId: null,
      requestId: null,
      from: null,
      to: null,
      offset: "0",
    });
    expect(buildIncidentQueueFacetQueryUpdates(filters, "triage", "stale", "stale").facet).toBeNull();
    expect(buildIncidentQueueFacetQueryUpdates(filters, "triage", "stale", "fresh").facet).toBe("fresh");
    expect(buildIncidentQueueResetFiltersQueryUpdates(filters, "triage", "stale")).toEqual({
      period: "24h",
      environment: "prod",
      view: "triage",
      facet: "stale",
      roomId: null,
      meetingId: null,
      subjectId: null,
      errorCode: null,
      category: null,
      severity: null,
      traceId: null,
      requestId: null,
      from: null,
      to: null,
      offset: "0",
    });
  });

  it("falls back to default incident freshness copy when source hint is empty", () => {
    expect(resolveIncidentRelativeTimeLabel(" ")).toBe("Сводка активности недоступна");
    expect(resolveIncidentRelativeTimeLabel("3 minutes ago")).toBe("3 minutes ago");
  });

  it("strips exact-match search params from returnTo while preserving queue state", () => {
    const currentUrl = new URL("http://localhost:3000/admin/incidents?period=1h&environment=dev&view=critical&facet=severity%3Acritical&traceId=trace-1&requestId=req-1&errorCode=TOKEN_INVALID&from=2026-03-18T09%3A00%3A00Z&to=2026-03-18T10%3A00%3A00Z&meetingId=meeting-1");

    expect(buildIncidentQueueReturnHref(currentUrl)).toBe(
      "/admin/incidents?period=1h&environment=dev&view=critical&facet=severity%3Acritical",
    );
    expect(buildIncidentDetailHref(currentUrl, "incident-1", "dev", buildIncidentQueueReturnHref(currentUrl))).toContain(
      "returnTo=%2Fadmin%2Fincidents%3Fperiod%3D1h%26environment%3Ddev%26view%3Dcritical%26facet%3Dseverity%253Acritical",
    );
  });

  it("falls back to queue route when detail page receives unsafe returnTo", () => {
    const currentUrl = new URL("http://localhost:3000/admin/incidents/incident-1?environment=prod&returnTo=https%3A%2F%2Fevil.example%2Fphish&traceId=trace-1");

    expect(resolveIncidentReturnTo(currentUrl, "prod")).toBe("/admin/incidents?environment=prod");
    expect(buildIncidentNextActionHref(
      currentUrl,
      { kind: "queue", label: "Вернуться", detail: "", target: "queue-return", externalUrl: null },
      [],
      "prod",
    )).toBe("/admin/incidents?environment=prod");
  });

  it("preserves detail return context for incident-scope drill-through", () => {
    const currentUrl = new URL("http://localhost:3000/admin/incidents/incident-1?environment=dev&returnTo=%2Fadmin%2Fincidents%3Fperiod%3D1h%26environment%3Ddev");

    const href = buildIncidentRelatedHref(
      currentUrl,
      {
        kind: "incident-scope",
        label: "Очередь по затронутой сущности",
        environment: "dev",
        subjectId: "subject-1",
        roomId: "room-1",
        meetingId: "meeting-1",
        traceId: null,
        externalUrl: null,
      },
      "dev",
    );

    expect(href).toContain("returnTo=%2Fadmin%2Fincidents%2Fincident-1%3Fenvironment%3Ddev%26returnTo%3D%252Fadmin%252Fincidents%253Fperiod%253D1h%2526environment%253Ddev");
    expect(href).toContain("roomId=room-1");
    expect(href).toContain("meetingId=meeting-1");
  });

  it("treats meeting and time bounds as valid search inputs", () => {
    expect(hasIncidentSearchQuery({
      traceId: "",
      requestId: "",
      errorCode: "",
      from: "",
      to: "2026-03-18T10:00:00Z",
      meetingId: "meeting-1",
    })).toBe(true);
  });

  it("materializes critical queue intent in dashboard handoff links", () => {
    const currentUrl = new URL("http://localhost:3000/admin?period=1h&environment=dev");

    const href = buildDashboardIncidentHref(
      currentUrl,
      {
        environment: "dev",
        period: "1h",
        severity: "critical",
        errorCode: "CONFIG_INCOMPATIBLE",
        category: "CONFIG",
        roomId: "room-1",
        meetingId: "meeting-1",
        incidentId: null,
      },
      "dev",
      "1h",
    );

    expect(href).toContain("/admin/incidents?");
    expect(href).toContain("view=critical");
    expect(href).toContain("severity=critical");
  });
});