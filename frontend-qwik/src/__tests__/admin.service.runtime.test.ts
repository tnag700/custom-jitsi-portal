import { afterEach, describe, expect, it, vi } from "vitest";
import {
  AdminDashboardServiceError,
  fetchAdminDashboard,
  fetchAdminDrillDown,
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

describe("admin.service runtime", () => {
  it("fetchAdminDashboard calls bounded summary endpoint with deep-link query params", async () => {
    const payload = {
      period: "1h",
      environment: "dev",
      tenantId: "tenant-1",
      generatedAt: "2026-03-18T10:00:00Z",
      traceId: "trace-admin-1",
      priorityBanner: {
        active: true,
        severity: "critical",
        headline: "Config mismatch blocks joins",
        summary: "Config incompatibility is the dominant signal for the selected window.",
        actionLabel: "Открыть очередь инцидентов",
        handoff: {
          environment: "dev",
          period: "1h",
          severity: "critical",
          errorCode: "CONFIG_INCOMPATIBLE",
          category: "CONFIG",
          roomId: "room a/b",
          meetingId: "meeting-1",
          incidentId: null,
        },
      },
      topDegradations: [
        {
          id: "config-compatibility",
          title: "Config compatibility requires immediate attention",
          summary: "Role mismatch blocks join attempts in the active environment.",
          severity: "critical",
          actionLabel: "Открыть очередь инцидентов",
          handoff: {
            environment: "dev",
            period: "1h",
            severity: "critical",
            errorCode: "CONFIG_INCOMPATIBLE",
            category: "CONFIG",
            roomId: "room a/b",
            meetingId: "meeting-1",
            incidentId: null,
          },
        },
      ],
      keyServiceStatuses: [
        {
          key: "backend-api",
          label: "Backend API",
          status: "DOWN",
          detail: "Health surface reports an incompatible config.",
          handoff: {
            environment: "dev",
            period: "1h",
            severity: "critical",
            errorCode: null,
            category: "CONFIG",
            roomId: "room a/b",
            meetingId: "meeting-1",
            incidentId: null,
          },
        },
      ],
      latestSpikes: [
        {
          errorCode: "CONFIG_INCOMPATIBLE",
          category: "CONFIG",
          count: 3,
          summary: "Three recent failures share the same config incompatibility code.",
          handoff: {
            environment: "dev",
            period: "1h",
            severity: "critical",
            errorCode: "CONFIG_INCOMPATIBLE",
            category: "CONFIG",
            roomId: "room a/b",
            meetingId: "meeting-1",
            incidentId: null,
          },
        },
      ],
      affectedScopeSummary: [
        {
          scopeType: "room",
          scopeValue: "room a/b",
          affectedAttempts: 3,
          summary: "room a/b drives the latest failed join attempts.",
          handoff: {
            environment: "dev",
            period: "1h",
            severity: "critical",
            errorCode: "CONFIG_INCOMPATIBLE",
            category: "CONFIG",
            roomId: "room a/b",
            meetingId: null,
            incidentId: null,
          },
        },
      ],
      safeStateSummary: {
        stable: false,
        headline: "Есть активные сигналы",
        summary: "Use the priority queue handoff instead of secondary modules.",
        actions: [
          { label: "Открыть очередь инцидентов", href: "/admin/incidents?period=1h&environment=dev" },
        ],
        recentResolvedSpikes: [],
      },
      entityFilter: { roomId: "room a/b", meetingId: "meeting-1" },
      sampleWindowLimited: false,
    };

    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(payload, 200));

    const result = await fetchAdminDashboard("sess-1", "http://localhost:8080/api/v1", {
      period: "1h",
      environment: "dev",
      roomId: "room a/b",
      meetingId: "meeting-1",
    });

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/admin/dashboard?period=1h&environment=dev&roomId=room+a%2Fb&meetingId=meeting-1",
      expect.objectContaining({ method: "GET" }),
    );
    expect(result.priorityBanner.handoff.errorCode).toBe("CONFIG_INCOMPATIBLE");
    expect(result.topDegradations[0]?.id).toBe("config-compatibility");
  });

  it("fetchAdminDrillDown calls drill-down endpoint with category selection", async () => {
    const payload = {
      period: "15m",
      environment: "dev",
      tenantId: "tenant-1",
      generatedAt: "2026-03-18T10:00:00Z",
      selectionType: "category",
      selectionValue: "TOKEN",
      entityFilter: { roomId: null, meetingId: null },
      failureCount: 2,
      recentSamples: [],
      sampleWindowLimited: false,
    };

    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(payload, 200));

    const result = await fetchAdminDrillDown("sess-1", "http://localhost:8080/api/v1", {
      period: "15m",
      environment: "dev",
      category: "TOKEN",
    });

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/admin/dashboard/drill-down?period=15m&environment=dev&category=TOKEN",
      expect.objectContaining({ method: "GET" }),
    );
    expect(result.selectionValue).toBe("TOKEN");
  });

  it("throws AdminDashboardServiceError for problem details payload", async () => {
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
      fetchAdminDashboard("sess-1", "http://localhost:8080/api/v1", { period: "15m" }),
    ).rejects.toBeInstanceOf(AdminDashboardServiceError);
  });
});