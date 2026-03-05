import { afterEach, describe, expect, it, vi } from "vitest";
import {
  MeetingServiceError,
  cancelMeeting,
  createMeeting,
  fetchMeetings,
  updateMeeting,
} from "../lib/domains/meetings/meetings.service";

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
  it("fetchMeetings calls room meetings endpoint with encoded room id", async () => {
    const payload = {
      content: [],
      page: 0,
      pageSize: 20,
      totalElements: 0,
      totalPages: 0,
    };

    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(payload, 200));

    const result = await fetchMeetings("sess-1", "http://localhost:8080/api/v1", "room a/b");

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/rooms/room%20a%2Fb/meetings?page=0&size=20",
      expect.objectContaining({ method: "GET" }),
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

    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(meeting, 201));

    const result = await createMeeting(
      "sess-1",
      "http://localhost:8080/api/v1",
      "csrf-1",
      "idem-1",
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
          "X-XSRF-TOKEN": "csrf-1",
          "Idempotency-Key": "idem-1",
        }),
      }),
    );
    expect(result).toEqual(meeting);
  });

  it("updateMeeting maps explicit problem payload", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
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
      updateMeeting("sess-1", "http://localhost:8080/api/v1", "csrf-1", "meeting a/b", { title: "New" }),
    ).rejects.toMatchObject({
      payload: {
        errorCode: "MEETING_FINALIZED",
        traceId: "trace-1",
      },
    });
  });

  it("cancelMeeting maps fallback for non-json 4xx", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response("oops", {
        status: 400,
        headers: {
          "content-type": "text/plain",
        },
      }),
    );

    await expect(
      cancelMeeting("sess-1", "http://localhost:8080/api/v1", "csrf-1", "meeting-1"),
    ).rejects.toMatchObject({
      payload: {
        errorCode: "INVALID_SCHEDULE",
      },
    });
  });

  it("throws MeetingServiceError instance for failures", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse({}, 500));

    try {
      await fetchMeetings("sess-1", "http://localhost:8080/api/v1", "room-1");
      throw new Error("Expected fetchMeetings to throw MeetingServiceError");
    } catch (error) {
      expect(error).toBeInstanceOf(MeetingServiceError);
      const meetingError = error as MeetingServiceError;
      expect(meetingError.payload.errorCode).toBe("MEETING_SERVICE_UNAVAILABLE");
    }
  });
});
