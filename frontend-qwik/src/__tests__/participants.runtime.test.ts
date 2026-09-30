import { afterEach, describe, expect, expectTypeOf, it, vi } from "vitest";
import {
  assignParticipant,
  bulkAssignParticipants,
  fetchParticipants,
  searchUsers,
  unassignParticipant,
  updateParticipantRole,
} from "../lib/domains/meetings/participants.service";
import { MeetingServiceError } from "../lib/domains/meetings/meetings.service";
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
const bulkMutationContext: MutationRequestContext = {
  ...mutationContext,
  idempotencyKey: "idem-bulk-1",
  headers: { ...mutationContext.headers, "Idempotency-Key": "idem-bulk-1" },
};

const assignmentFixture = {
  assignmentId: "a-1",
  meetingId: "m-1",
  subjectId: "u-1",
  role: "participant",
  assignedBy: "admin",
  assignedAt: "2026-03-03T10:00:00Z",
  createdAt: "2026-03-03T10:00:00Z",
  updatedAt: "2026-03-03T10:00:00Z",
  fullName: null,
  organization: null,
  position: null,
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

describe("participants.service runtime", () => {
  it("accepts request contexts as the public entry point for all participant APIs", () => {
    expectTypeOf<Parameters<typeof fetchParticipants>[0]>().toEqualTypeOf<ServerRequestContext>();
    expectTypeOf<Parameters<typeof searchUsers>[0]>().toEqualTypeOf<ServerRequestContext>();
    expectTypeOf<Parameters<typeof assignParticipant>[0]>().toEqualTypeOf<MutationRequestContext>();
    expectTypeOf<Parameters<typeof bulkAssignParticipants>[0]>().toEqualTypeOf<MutationRequestContext>();
    expectTypeOf<Parameters<typeof updateParticipantRole>[0]>().toEqualTypeOf<MutationRequestContext>();
    expectTypeOf<Parameters<typeof unassignParticipant>[0]>().toEqualTypeOf<MutationRequestContext>();
  });

  it("fetchParticipants calls participants endpoint", async () => {
    const payload = [
      {
        ...assignmentFixture,
        fullName: "Иванов Иван",
        organization: "ЦРБ",
        position: "Врач",
      },
    ];

    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(jsonResponse(payload, 200));

    const result = await fetchParticipants(
      serverContext,
      "meeting-1",
    );

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/meetings/meeting-1/participants",
      expect.objectContaining({ method: "GET", headers: serverContext.headers }),
    );
    expect(result).toEqual(payload);
  });

  it("assignParticipant sends idempotency header", async () => {
    const payload = {
      ...assignmentFixture,
      subjectId: "u-2",
      role: "moderator",
    };

    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(jsonResponse(payload, 201));

    const result = await assignParticipant(
      mutationContext,
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
        headers: mutationContext.headers,
        body: JSON.stringify({ subjectId: "u-2", role: "moderator" }),
      }),
    );
    expect(result).toEqual(payload);
  });

  it("bulkAssignParticipants posts the selected users to the bulk endpoint", async () => {
    const payload = [
      {
        ...assignmentFixture,
        subjectId: "u-2",
      },
      {
        ...assignmentFixture,
        assignmentId: "a-2",
        subjectId: "u-3",
      },
    ];

    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(jsonResponse(payload, 201));

    const result = await bulkAssignParticipants(
      bulkMutationContext,
      "meeting-1",
      {
        defaultRole: "participant",
        participants: [{ subjectId: "u-2" }, { subjectId: "u-3" }],
      },
    );

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/meetings/meeting-1/participants/bulk",
      expect.objectContaining({
        method: "POST",
        headers: bulkMutationContext.headers,
        body: JSON.stringify({
          defaultRole: "participant",
          participants: [{ subjectId: "u-2" }, { subjectId: "u-3" }],
        }),
      }),
    );
    expect(result).toEqual(payload);
  });

  it("searchUsers queries the tenant-scoped directory endpoint", async () => {
    const payload = [
      {
        subjectId: "u-1",
        fullName: "Иванов Иван",
        organization: "ЦРБ",
        position: "Врач",
      },
    ];
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(jsonResponse(payload, 200));

    const result = await searchUsers(
      serverContext,
      "tenant-1",
      "иван",
      "ЦРБ",
    );

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/users/search?tenant_id=tenant-1&q=%D0%B8%D0%B2%D0%B0%D0%BD&organization=%D0%A6%D0%A0%D0%91",
      expect.objectContaining({ method: "GET", headers: serverContext.headers }),
    );
    expect(result).toEqual(payload);
  });

  it.each([
    [undefined, undefined, "tenant_id=tenant-1"],
    [" иван ", undefined, "tenant_id=tenant-1&q=%D0%B8%D0%B2%D0%B0%D0%BD"],
    [undefined, " ЦРБ ", "tenant_id=tenant-1&organization=%D0%A6%D0%A0%D0%91"],
    [" \t ", " \t ", "tenant_id=tenant-1"],
    [" иван ", " ЦРБ ", "tenant_id=tenant-1&q=%D0%B8%D0%B2%D0%B0%D0%BD&organization=%D0%A6%D0%A0%D0%91"],
  ])("searchUsers trims filters and omits missing or blank values (%s, %s)", async (query, organization, search) => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse([], 200));

    expect(await searchUsers(serverContext, "tenant-1", query, organization)).toEqual([]);
    expect(fetchMock).toHaveBeenCalledWith(
      `http://localhost:8080/api/v1/users/search?${search}`,
      expect.objectContaining({ method: "GET", headers: serverContext.headers }),
    );
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
        mutationContext,
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
      expect.objectContaining({
        method: "PUT",
        headers: mutationContext.headers,
        body: JSON.stringify({ role: "participant" }),
      }),
    );
  });

  it("updateParticipantRole accepts the host role and preserves empty profile strings", async () => {
    const payload = {
      ...assignmentFixture,
      role: "host",
      fullName: "",
      organization: "",
      position: "",
    };
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(payload, 200));

    const result = await updateParticipantRole(
      mutationContext,
      "meeting-1", "u-1", { role: "host" },
    );

    expect(result).toMatchObject({
      assignmentId: "a-1", role: "host", fullName: "", organization: "", position: "",
    });
    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/meetings/meeting-1/participants/u-1",
      expect.objectContaining({
        method: "PUT",
        headers: mutationContext.headers,
        body: JSON.stringify({ role: "host" }),
      }),
    );
  });

  it("fetchParticipants normalizes omitted profile fields to null", async () => {
    const payload: Record<string, unknown> = { ...assignmentFixture };
    delete payload.fullName;
    delete payload.organization;
    delete payload.position;
    vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse([payload], 200));

    const result = await fetchParticipants(serverContext, "meeting-1");

    expect(result[0]).toMatchObject({
      fullName: null, organization: null, position: null,
    });
  });

  it.each([
    {
      endpoint: "fetchParticipants",
      payload: {},
      call: () => fetchParticipants(serverContext, "meeting-1"),
    },
    {
      endpoint: "assignParticipant",
      payload: [],
      call: () => assignParticipant(
        mutationContext,
        "meeting-1", { subjectId: "u-1", role: "participant" },
      ),
    },
    {
      endpoint: "bulkAssignParticipants",
      payload: [assignmentFixture, { ...assignmentFixture, role: "guest" }],
      call: () => bulkAssignParticipants(
        bulkMutationContext,
        "meeting-1", { participants: [{ subjectId: "u-1" }] },
      ),
    },
    {
      endpoint: "updateParticipantRole",
      payload: { ...assignmentFixture, role: "guest" },
      call: () => updateParticipantRole(
        mutationContext,
        "meeting-1", "u-1", { role: "participant" },
      ),
    },
    {
      endpoint: "searchUsers",
      payload: {},
      call: () => searchUsers(serverContext, "tenant-1"),
    },
  ])("$endpoint rejects malformed successful responses with MeetingServiceError", async ({ payload, call }) => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(payload, 200));

    await expect(call()).rejects.toMatchObject({
      name: "MeetingServiceError",
      payload: { errorCode: "MEETING_RESPONSE_INVALID" },
    });
  });

  it.each([
    "assignmentId", "meetingId", "subjectId", "role", "assignedBy",
    "assignedAt", "createdAt", "updatedAt",
  ])("assignParticipant rejects a response missing %s", async (field) => {
    const payload: Record<string, unknown> = { ...assignmentFixture };
    delete payload[field];
    vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(payload, 201));

    await expect(assignParticipant(
      mutationContext,
      "meeting-1", { subjectId: "u-1", role: "participant" },
    )).rejects.toMatchObject({
      payload: { errorCode: "MEETING_RESPONSE_INVALID" },
    });
  });

  it.each(["fullName", "organization", "position"])(
    "fetchParticipants rejects a non-string %s profile field", async (field) => {
      vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse([
        { ...assignmentFixture, [field]: 123 },
      ], 200));

      await expect(fetchParticipants(
        serverContext, "meeting-1",
      )).rejects.toMatchObject({
        payload: { errorCode: "MEETING_RESPONSE_INVALID" },
      });
    },
  );

  it.each([
    ["subjectId", undefined], ["subjectId", null],
    ["fullName", undefined], ["fullName", null],
    ["organization", undefined], ["organization", null],
    ["position", undefined], ["position", null],
  ] as const)("searchUsers rejects a %s summary field with value %s", async (field, value) => {
    const payload = {
      subjectId: "u-1", fullName: "Иванов Иван", organization: "ЦРБ", position: "Врач",
      [field]: value,
    };
    vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse([payload], 200));

    await expect(searchUsers(
      serverContext, "tenant-1",
    )).rejects.toMatchObject({
      payload: { errorCode: "MEETING_RESPONSE_INVALID" },
    });
  });

  it("unassignParticipant resolves on 204", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(null, { status: 204 }));

    await expect(
      unassignParticipant(
        mutationContext,
        "meeting-1",
        "u-1",
      ),
    ).resolves.toBeUndefined();

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/meetings/meeting-1/participants/u-1",
      expect.objectContaining({ method: "DELETE", headers: mutationContext.headers }),
    );
  });

  it("throws MeetingServiceError on participant API failures", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse({}, 500));

    try {
      await fetchParticipants(
        serverContext,
        "meeting-1",
      );
      throw new Error(
        "Expected fetchParticipants to throw MeetingServiceError",
      );
    } catch (error) {
      expect(error).toBeInstanceOf(MeetingServiceError);
      const meetingError = error as MeetingServiceError;
      expect(meetingError.payload.errorCode).toBe(
        "MEETING_SERVICE_UNAVAILABLE",
      );
    }
  });
});
