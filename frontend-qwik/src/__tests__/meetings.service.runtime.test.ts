import { afterEach, describe, expect, it, vi } from "vitest";
import {
  MeetingServiceError,
  cancelMeeting,
  createMeeting,
  fetchMeeting,
  fetchMeetings,
  updateMeeting,
} from "../lib/domains/meetings/meetings.service";
import type {
  MutationRequestContext,
  ServerRequestContext,
} from "../lib/shared/routes/server-handlers";

const serverContext: ServerRequestContext = {
  apiUrl: "http://localhost:8080/api/v1",
  sessionCookie: "sess-1",
  csrfToken: "",
  headers: { Cookie: "JSESSIONID=sess-1" },
};
const mutationContext: MutationRequestContext = {
  ...serverContext,
  csrfToken: "csrf-request-1",
  csrfCookieToken: "csrf-cookie-1",
  idempotencyKey: "idem-1",
  headers: {
    Cookie: "JSESSIONID=sess-1; XSRF-TOKEN=csrf-cookie-1",
    "Content-Type": "application/json",
    "X-XSRF-TOKEN": "csrf-request-1",
    "Idempotency-Key": "idem-1",
  },
};

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

describe("meetings.service runtime", () => {
  it("fetchMeeting loads a selected meeting independently of the current page", async () => {
    const meeting = {
      meetingId: "m-21", roomId: "r-1", title: "Selected", description: null,
      meetingType: "scheduled", configSetId: "config-1", status: "scheduled",
      startsAt: "2026-03-10T10:00:00Z", endsAt: "2026-03-10T11:00:00Z",
      allowGuests: true, recordingEnabled: false,
      createdAt: "2026-03-03T10:00:00Z", updatedAt: "2026-03-03T10:00:00Z",
    };
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(meeting, 200));

    expect(await fetchMeeting(serverContext, "m-21")).toEqual(meeting);
    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/meetings/m-21",
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({ Cookie: "JSESSIONID=sess-1" }),
      }),
    );
  });
  it.each([
    [undefined, undefined, "page=0&size=20"],
    [2, undefined, "page=2&size=20"],
    [2, 3, "page=2&size=3"],
  ])("fetchMeetings encodes the room id and preserves pagination (%s, %s)", async (page, size, query) => {
    const payload = {
      content: [],
      page: 0,
      pageSize: 20,
      totalElements: 0,
      totalPages: 0,
    };

    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(jsonResponse(payload, 200));

    const result = await fetchMeetings(
      serverContext,
      "room a/b",
      page,
      size,
    );

    expect(fetchMock).toHaveBeenCalledWith(
      `http://localhost:8080/api/v1/rooms/room%20a%2Fb/meetings?${query}`,
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({ Cookie: "JSESSIONID=sess-1" }),
      }),
    );
    expect(result).toEqual(payload);
  });

  it("createMeeting sends csrf and idempotency headers", async () => {
    const meeting = {
      meetingId: "m-1",
      roomId: "r-1",
      title: "Meeting",
      description: null,
      meetingType: "scheduled",
      configSetId: "config-1",
      status: "scheduled",
      startsAt: "2026-03-10T10:00:00Z",
      endsAt: "2026-03-10T11:00:00Z",
      allowGuests: true,
      recordingEnabled: false,
      createdAt: "2026-03-03T10:00:00Z",
      updatedAt: "2026-03-03T10:00:00Z",
    };

    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(jsonResponse(meeting, 201));

    const result = await createMeeting(
      mutationContext,
      "room-1",
      {
        title: "Meeting",
        meetingType: "scheduled",
        startsAt: "2026-03-10T10:00:00Z",
        endsAt: "2026-03-10T11:00:00Z",
      },
    );

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/rooms/room-1/meetings",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({
          Cookie: "JSESSIONID=sess-1; XSRF-TOKEN=csrf-cookie-1",
          "X-XSRF-TOKEN": "csrf-request-1",
          "Idempotency-Key": "idem-1",
        }),
        body: JSON.stringify({
          title: "Meeting",
          meetingType: "scheduled",
          startsAt: "2026-03-10T10:00:00Z",
          endsAt: "2026-03-10T11:00:00Z",
        }),
      }),
    );
    expect(result).toEqual(meeting);
  });

  it("fetchMeeting normalizes an omitted description to null", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse({
      meetingId: "m-1", roomId: "r-1", title: "Meeting",
      meetingType: "scheduled", configSetId: "config-1", status: "scheduled",
      startsAt: "2026-03-10T10:00:00Z", endsAt: "2026-03-10T11:00:00Z",
      allowGuests: true, recordingEnabled: false,
      createdAt: "2026-03-03T10:00:00Z", updatedAt: "2026-03-03T10:00:00Z",
    }, 200));

    expect(await fetchMeeting(serverContext, "m-1")).toMatchObject({
      meetingId: "m-1", description: null,
    });
  });

  it("fetchMeeting rejects malformed successful responses with MeetingServiceError", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse({ meetingId: "m-1" }, 200));

    await expect(fetchMeeting(serverContext, "m-1")).rejects.toMatchObject({
      name: "MeetingServiceError",
      payload: { errorCode: "MEETING_RESPONSE_INVALID" },
    });
  });

  it("updateMeeting maps explicit problem payload", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse(
        {
          title: "Finalized",
          detail: "Meeting already finalized",
          errorCode: "MEETING_FINALIZED",
          traceId: "trace-1",
        },
        409,
      ),
    );

    await expect(
      updateMeeting(
        mutationContext,
        "meeting a/b",
        { title: "New" },
      ),
    ).rejects.toMatchObject({
      payload: {
        errorCode: "MEETING_FINALIZED",
        traceId: "trace-1",
      },
    });
    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/meetings/meeting%20a%2Fb",
      expect.objectContaining({
        method: "PUT",
        headers: expect.objectContaining({
          Cookie: "JSESSIONID=sess-1; XSRF-TOKEN=csrf-cookie-1",
          "X-XSRF-TOKEN": "csrf-request-1",
          "Idempotency-Key": "idem-1",
        }),
        body: JSON.stringify({ title: "New" }),
      }),
    );
  });

  it("cancelMeeting maps fallback for non-json 4xx", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response("oops", {
        status: 400,
        headers: {
          "content-type": "text/plain",
        },
      }),
    );

    await expect(
      cancelMeeting(
        mutationContext,
        "meeting-1",
      ),
    ).rejects.toMatchObject({
      payload: {
        errorCode: "INVALID_SCHEDULE",
      },
    });
    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/meetings/meeting-1/cancel",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({
          Cookie: "JSESSIONID=sess-1; XSRF-TOKEN=csrf-cookie-1",
          "X-XSRF-TOKEN": "csrf-request-1",
          "Idempotency-Key": "idem-1",
        }),
      }),
    );
  });

  it("throws MeetingServiceError instance for failures", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse({}, 500));

    try {
      await fetchMeetings(serverContext, "room-1");
      throw new Error("Expected fetchMeetings to throw MeetingServiceError");
    } catch (error) {
      expect(error).toBeInstanceOf(MeetingServiceError);
      const meetingError = error as MeetingServiceError;
      expect(meetingError.payload.errorCode).toBe(
        "MEETING_SERVICE_UNAVAILABLE",
      );
    }
  });
});
