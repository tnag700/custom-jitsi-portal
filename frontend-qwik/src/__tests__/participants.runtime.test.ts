import { afterEach, describe, expect, it, vi } from "vitest";
import {
  assignParticipant,
  fetchParticipants,
  unassignParticipant,
  updateParticipantRole,
} from "../lib/domains/meetings/participants.service";
import { MeetingServiceError } from "../lib/domains/meetings/meetings.service";

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

describe("participants.service runtime", () => {
  it("fetchParticipants calls participants endpoint", async () => {
    const payload = [
      {
        meetingId: "m-1",
        subjectId: "u-1",
        role: "participant",
        assignedBy: "admin",
        assignedAt: "2026-03-03T10:00:00Z",
      },
    ];

    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(payload, 200));

    const result = await fetchParticipants("sess-1", "http://localhost:8080/api/v1", "meeting-1");

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/meetings/meeting-1/participants",
      expect.objectContaining({ method: "GET" }),
    );
    expect(result).toEqual(payload);
  });

  it("assignParticipant sends idempotency header", async () => {
    const payload = {
      meetingId: "m-1",
      subjectId: "u-2",
      role: "moderator",
      assignedBy: "admin",
      assignedAt: "2026-03-03T10:00:00Z",
    };

    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(payload, 201));

    const result = await assignParticipant(
      "sess-1",
      "http://localhost:8080/api/v1",
      "csrf-1",
      "idem-1",
      "meeting-1",
      {
        subjectId: "u-2",
        role: "moderator",
      },
    );

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/meetings/meeting-1/participants",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({
          "Idempotency-Key": "idem-1",
          "X-XSRF-TOKEN": "csrf-1",
        }),
      }),
    );
    expect(result).toEqual(payload);
  });

  it("updateParticipantRole encodes subject id", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response("", {
        status: 400,
        headers: {
          "content-type": "text/plain",
        },
      }),
    );

    await expect(
      updateParticipantRole(
        "sess-1",
        "http://localhost:8080/api/v1",
        "csrf-1",
        "meeting-1",
        "user a/b",
        { role: "participant" },
      ),
    ).rejects.toMatchObject({
      payload: {
        errorCode: "INVALID_SCHEDULE",
      },
    });

    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/meetings/meeting-1/participants/user%20a%2Fb",
      expect.objectContaining({ method: "PUT" }),
    );
  });

  it("unassignParticipant resolves on 204", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(null, { status: 204 }));

    await expect(
      unassignParticipant("sess-1", "http://localhost:8080/api/v1", "csrf-1", "meeting-1", "u-1"),
    ).resolves.toBeUndefined();

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/meetings/meeting-1/participants/u-1",
      expect.objectContaining({ method: "DELETE" }),
    );
  });

  it("throws MeetingServiceError on participant API failures", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse({}, 500));

    try {
      await fetchParticipants("sess-1", "http://localhost:8080/api/v1", "meeting-1");
      throw new Error("Expected fetchParticipants to throw MeetingServiceError");
    } catch (error) {
      expect(error).toBeInstanceOf(MeetingServiceError);
      const meetingError = error as MeetingServiceError;
      expect(meetingError.payload.errorCode).toBe("MEETING_SERVICE_UNAVAILABLE");
    }
  });
});
