import { afterEach, describe, expect, it, vi } from "vitest";
import {
  InviteServiceError,
  createInvite,
  fetchInvites,
  revokeInvite,
} from "../lib/domains/invites/invites.service";

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

describe("invites.service runtime", () => {
  it("fetchInvites sends encoded meeting id and pagination params", async () => {
    const payload = {
      content: [],
      page: 1,
      pageSize: 5,
      totalElements: 0,
      totalPages: 0,
    };

    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(payload, 200));

    const result = await fetchInvites("sess-1", "http://localhost:8080/api/v1", "meeting a/b", 1, 5);

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/meetings/meeting%20a%2Fb/invites?page=1&size=5",
      {
        method: "GET",
        headers: {
          Cookie: "JSESSIONID=sess-1",
        },
      },
    );
    expect(result).toEqual(payload);
  });

  it("createInvite sends mutation headers including idempotency key", async () => {
    const invite = {
      id: "i-1",
      meetingId: "m-1",
      token: "token-1",
      role: "participant",
      recipientEmail: "user@example.com",
      recipientSubjectId: null,
      maxUses: 1,
      usedCount: 0,
      expiresAt: "2026-03-10T10:00:00Z",
      revokedAt: null,
      createdBy: "",
      valid: false,
      createdAt: "2026-03-03T10:00:00Z",
      updatedAt: "2026-03-03T10:00:00Z",
    };

    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(invite, 201));

    const result = await createInvite(
      "sess-1",
      "http://localhost:8080/api/v1",
      "csrf-1",
      "idem-1",
      "meeting-1",
      {
        role: "participant",
        recipientEmail: "user@example.com",
        maxUses: 1,
        expiresInHours: 24,
      },
    );

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/meetings/meeting-1/invites",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({
          Cookie: "JSESSIONID=sess-1; XSRF-TOKEN=csrf-1",
          "X-XSRF-TOKEN": "csrf-1",
          "Idempotency-Key": "idem-1",
        }),
      }),
    );
    expect(result).toEqual(invite);
  });

  it("revokeInvite sends DELETE without idempotency header", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(null, { status: 204 }));

    await expect(
      revokeInvite("sess-1", "http://localhost:8080/api/v1", "csrf-1", "meeting-1", "invite-1"),
    ).resolves.toBeUndefined();

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/meetings/meeting-1/invites/invite-1",
      expect.objectContaining({
        method: "DELETE",
        headers: expect.objectContaining({
          Cookie: "JSESSIONID=sess-1; XSRF-TOKEN=csrf-1",
          "X-XSRF-TOKEN": "csrf-1",
        }),
      }),
    );

    const lastCall = fetchMock.mock.calls.at(-1);
    const headers = (lastCall?.[1] as { headers?: Record<string, string> })?.headers;
    expect(headers?.["Idempotency-Key"]).toBeUndefined();
  });

  it("maps fallback error code for non-json backend response", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response("oops", {
        status: 500,
        headers: {
          "content-type": "text/plain",
        },
      }),
    );

    await expect(fetchInvites("sess-1", "http://localhost:8080/api/v1", "meeting-1")).rejects.toMatchObject({
      name: "InviteServiceError",
      payload: {
        errorCode: "INVITE_SERVICE_UNAVAILABLE",
      },
    });
  });

  it("throws InviteServiceError instance", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse({}, 404));

    try {
      await revokeInvite("sess-1", "http://localhost:8080/api/v1", "csrf-1", "meeting-1", "invite-1");
      throw new Error("Expected revokeInvite to throw InviteServiceError");
    } catch (error) {
      expect(error).toBeInstanceOf(InviteServiceError);
      const inviteError = error as InviteServiceError;
      expect(inviteError.payload.errorCode).toBe("INVITE_NOT_FOUND");
    }
  });
});
