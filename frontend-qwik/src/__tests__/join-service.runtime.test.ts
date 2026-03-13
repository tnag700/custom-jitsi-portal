import { afterEach, describe, expect, it, vi } from "vitest";
import {
  JoinServiceError,
  adaptJoinProblemDetails,
  fetchUpcomingMeetings,
  issueAccessToken,
} from "../lib/domains/join/join.service";

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("join.service runtime: adaptJoinProblemDetails", () => {
  it("maps RFC7807 fields and preserves explicit errorCode", async () => {
    const response = jsonResponse(
      {
        title: "Meeting Not Found",
        detail: "The requested meeting does not exist",
        errorCode: "MEETING_NOT_FOUND",
      },
      404,
    );
    await expect(adaptJoinProblemDetails(response)).resolves.toEqual({
      title: "Meeting Not Found",
      detail: "The requested meeting does not exist",
      errorCode: "MEETING_NOT_FOUND",
      traceId: undefined,
    });
  });

  it("falls back to AUTH_REQUIRED for 401", async () => {
    const result = await adaptJoinProblemDetails(jsonResponse({}, 401));
    expect(result.errorCode).toBe("AUTH_REQUIRED");
    expect(result.title).toBe("Ошибка входа во встречу");
  });

  it("falls back to ACCESS_DENIED for 403", async () => {
    const result = await adaptJoinProblemDetails(jsonResponse({}, 403));
    expect(result.errorCode).toBe("ACCESS_DENIED");
  });

  it("falls back to MEETING_NOT_FOUND for 404", async () => {
    const result = await adaptJoinProblemDetails(jsonResponse({}, 404));
    expect(result.errorCode).toBe("MEETING_NOT_FOUND");
  });

  it("falls back to MEETING_ENDED for 409", async () => {
    const result = await adaptJoinProblemDetails(jsonResponse({}, 409));
    expect(result.errorCode).toBe("MEETING_ENDED");
  });

  it("falls back to JOIN_SERVICE_UNAVAILABLE for 5xx", async () => {
    const result = await adaptJoinProblemDetails(jsonResponse({}, 503));
    expect(result.errorCode).toBe("JOIN_SERVICE_UNAVAILABLE");
  });

  it("falls back to JOIN_UNKNOWN for non-mapped 4xx", async () => {
    const result = await adaptJoinProblemDetails(jsonResponse({}, 422));
    expect(result.errorCode).toBe("JOIN_UNKNOWN");
  });

  it("handles non-JSON body with status fallback", async () => {
    const response = new Response("not-json", {
      status: 401,
      headers: { "content-type": "text/plain" },
    });
    const result = await adaptJoinProblemDetails(response);
    expect(result.errorCode).toBe("AUTH_REQUIRED");
    expect(result.title).toBe("Ошибка входа во встречу");
    expect(result.detail).toBe("Не удалось войти во встречу.");
  });

  it("preserves traceId when present", async () => {
    const result = await adaptJoinProblemDetails(
      jsonResponse(
        { title: "Error", detail: "Fail", errorCode: "JOIN_SERVICE_UNAVAILABLE", traceId: "trace-abc-123" },
        503,
      ),
    );
    expect(result.traceId).toBe("trace-abc-123");
  });

  it("omits traceId when absent", async () => {
    const result = await adaptJoinProblemDetails(jsonResponse({ errorCode: "MEETING_ENDED" }, 409));
    expect(result.traceId).toBeUndefined();
  });
});

describe("join.service runtime: fetchUpcomingMeetings", () => {
  it("calls GET /meetings/upcoming with JSESSIONID cookie and returns meetings", async () => {
    const mockMeetings = [
      {
        meetingId: "m-1",
        title: "Team Standup",
        startsAt: "2026-03-03T09:00:00Z",
        roomName: "standup",
        joinAvailability: "available",
      },
    ];
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(mockMeetings, 200));

    const result = await fetchUpcomingMeetings("sess-123", "http://localhost:8080/api/v1");

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/meetings/upcoming",
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({
          Cookie: "JSESSIONID=sess-123",
        }),
      }),
    );
    expect(result).toEqual(mockMeetings);
  });

  it("throws JoinServiceError on 401", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse({ title: "Unauthorized", detail: "Not authenticated", errorCode: "AUTH_REQUIRED" }, 401),
    );
    await expect(fetchUpcomingMeetings("", "http://localhost:8080/api/v1")).rejects.toMatchObject({
      payload: { errorCode: "AUTH_REQUIRED" },
    });
  });

  it("throws JoinServiceError with fallback code on 5xx", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse({}, 503));
    await expect(
      fetchUpcomingMeetings("sess", "http://localhost:8080/api/v1"),
    ).rejects.toBeInstanceOf(JoinServiceError);
  });
});

describe("join.service runtime: issueAccessToken", () => {
  it("calls POST /meetings/{id}/access-token with JSESSIONID and X-XSRF-TOKEN headers", async () => {
    const tokenResponse = {
      joinUrl: "https://meet.example.com/room?token=xyz",
      expiresAt: "2026-03-03T10:00:00Z",
      role: "MODERATOR",
    };
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(tokenResponse, 200));

    const result = await issueAccessToken(
      "sess-123",
      "http://localhost:8080/api/v1",
      "csrf-456",
      "meeting-789",
    );

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/meetings/meeting-789/access-token",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({
          Cookie: "JSESSIONID=sess-123; XSRF-TOKEN=csrf-456",
          "X-XSRF-TOKEN": "csrf-456",
        }),
      }),
    );
    expect(result).toEqual(tokenResponse);
  });

  it("does not send request body (POST is body-less for access-token)", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse({ joinUrl: "https://meet.example.com/room", expiresAt: "2026-03-03T10:00:00Z", role: "ATTENDEE" }, 200),
    );

    await issueAccessToken("sess", "http://localhost:8080/api/v1", "csrf", "m-1");

    const callArgs = fetchMock.mock.calls[0][1] as RequestInit;
    expect(callArgs.body).toBeUndefined();
  });

  it("URL-encodes meetingId in the path", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse({ joinUrl: "https://meet.example.com/r", expiresAt: "2026-03-03T10:00:00Z", role: "ATTENDEE" }, 200),
    );

    await issueAccessToken("sess", "http://localhost:8080/api/v1", "csrf", "meeting/with spaces");

    expect(fetchMock.mock.calls[0][0] as string).toContain("meeting%2Fwith%20spaces");
  });

  it("throws JoinServiceError with AUTH_REQUIRED on 401", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse({ errorCode: "AUTH_REQUIRED", title: "Unauthorized", detail: "Session expired" }, 401),
    );
    await expect(
      issueAccessToken("", "http://localhost:8080/api/v1", "", "m-1"),
    ).rejects.toMatchObject({ payload: { errorCode: "AUTH_REQUIRED" } });
  });

  it("throws JoinServiceError with MEETING_NOT_FOUND on 404", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse({}, 404));
    await expect(
      issueAccessToken("sess", "http://localhost:8080/api/v1", "csrf", "ghost-id"),
    ).rejects.toMatchObject({ payload: { errorCode: "MEETING_NOT_FOUND" } });
  });

  it("throws JoinServiceError for 503", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse({}, 503));
    await expect(
      issueAccessToken("sess", "http://localhost:8080/api/v1", "csrf", "m-1"),
    ).rejects.toBeInstanceOf(JoinServiceError);
  });
});
