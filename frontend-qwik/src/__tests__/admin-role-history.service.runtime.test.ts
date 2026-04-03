import { afterEach, describe, expect, it, vi } from "vitest";
import {
  AdminDashboardServiceError,
  fetchAdminRoleHistory,
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

describe("admin role history service runtime", () => {
  it("fetchAdminRoleHistory calls bounded role-history endpoint with server-side filters", async () => {
    const payload = {
      tenantId: "tenant-1",
      environment: "dev",
      generatedAt: "2026-03-19T10:00:00Z",
      page: 0,
      pageSize: 20,
      totalElements: 1,
      totalPages: 1,
      content: [
        {
          occurredAt: "2026-03-19T09:55:00Z",
          actionType: "update",
          actionLabel: "Изменение роли",
          oldRole: "participant",
          newRole: "moderator",
          subjectLabel: "Иван Иванов",
          subjectReference: "use***45",
          actorLabel: "Мария Петрова",
          actorReference: "adm***01",
          tenantId: "tenant-1",
          environment: "dev",
          roomId: "room-1",
          meetingId: "meeting-1",
          traceId: "trace-1",
        },
      ],
    };

    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(payload, 200));

    const result = await fetchAdminRoleHistory("sess-1", "http://localhost:8080/api/v1", {
      environment: "dev",
      subjectId: "user-1",
      meetingId: "meeting-1",
      page: 0,
      pageSize: 20,
    });

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/admin/role-history?environment=dev&subjectId=user-1&meetingId=meeting-1&page=0&pageSize=20",
      expect.objectContaining({ method: "GET" }),
    );
    expect(result.content[0]?.actionType).toBe("update");
  });

  it("throws AdminDashboardServiceError for role-history problem details payload", async () => {
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
      fetchAdminRoleHistory("sess-1", "http://localhost:8080/api/v1", { meetingId: "meeting-1" }),
    ).rejects.toBeInstanceOf(AdminDashboardServiceError);
  });
});