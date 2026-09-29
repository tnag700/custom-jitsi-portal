/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
/* eslint-disable @typescript-eslint/await-thenable */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mockFetchRooms = vi.fn();
const mockFetchRoom = vi.fn();
const mockFetchMeetings = vi.fn();
const mockFetchMeeting = vi.fn();
const mockFetchParticipants = vi.fn();
const mockFetchInvites = vi.fn();
const mockCreateInvite = vi.fn();
const mockRevokeInvite = vi.fn();
const mockCreateMeeting = vi.fn();
const mockUpdateMeeting = vi.fn();
const mockCancelMeeting = vi.fn();
const mockAssignParticipant = vi.fn();
const mockBulkAssignParticipants = vi.fn();
const mockSearchUsers = vi.fn();
const mockUpdateParticipantRole = vi.fn();
const mockUnassignParticipant = vi.fn();

class MockMeetingServiceError extends Error {
  payload: {
    title: string;
    detail: string;
    errorCode: string;
    traceId?: string;
  };

  constructor(payload: {
    title: string;
    detail: string;
    errorCode: string;
    traceId?: string;
  }) {
    super(payload.detail);
    this.name = "MeetingServiceError";
    this.payload = payload;
  }
}

class MockInviteServiceError extends Error {
  payload: {
    title: string;
    detail: string;
    errorCode: string;
    traceId?: string;
  };

  constructor(payload: {
    title: string;
    detail: string;
    errorCode: string;
    traceId?: string;
  }) {
    super(payload.detail);
    this.name = "InviteServiceError";
    this.payload = payload;
  }
}

vi.mock("@qwik.dev/core", async (importOriginal) => {
  const actual = await importOriginal();
  const identity = <T>(value: T): T => value;
  const noop = () => undefined;
  return {
    ...actual,
    $: identity,
    component$: identity,
    componentQrl: identity,
    inlinedQrl: identity,
    inlinedQrlDEV: identity,
    useSignal: <T>(value: T) => ({ value }),
    useTask$: noop,
    useLocation: () => ({ url: new URL("http://localhost/meetings") }),
    useNavigate: () => () => Promise.resolve(),
  };
});

vi.mock("@qwik.dev/router", async (importOriginal) => {
  const actual = await importOriginal();
  const identity = <T>(value: T): T => value;
  const stringSchema = () => ({ min: () => ({}) });
  return {
    ...actual,
    routeLoader$: identity,
    routeLoaderQrl: identity,
    routeAction$: identity,
    routeActionQrl: identity,
    zod$: identity,
    Form: () => null,
    Link: () => null,
    useLocation: () => ({ url: new URL("http://localhost/meetings") }),
    useNavigate: () => () => Promise.resolve(),
    z: {
      object: (shape: unknown) => ({
        ...shape,
        extend: () => ({}),
        and: () => ({}),
      }),
      string: stringSchema,
    },
  };
});

vi.mock("~/lib/shared", () => ({
  ApiErrorAlert: () => null,
}));

vi.mock("~/lib/domains/rooms", () => ({
  fetchRooms: mockFetchRooms,
  fetchRoom: mockFetchRoom,
  createRoomSchema: {},
  updateRoomSchema: { extend: () => ({}) },
}));

vi.mock("~/lib/domains/meetings", () => ({
  MeetingForm: () => null,
  MeetingList: () => null,
  ParticipantPanel: () => null,
  MeetingServiceError: MockMeetingServiceError,
  assignParticipant: mockAssignParticipant,
  assignParticipantSchema: { extend: () => ({}) },
  bulkAssignParticipants: mockBulkAssignParticipants,
  bulkAssignParticipantsSchema: { extend: () => ({}) },
  cancelMeeting: mockCancelMeeting,
  createMeeting: mockCreateMeeting,
  createMeetingSchema: { and: () => ({}) },
  fetchMeetings: mockFetchMeetings,
  fetchMeeting: mockFetchMeeting,
  fetchParticipants: mockFetchParticipants,
  searchUsers: mockSearchUsers,
  unassignParticipant: mockUnassignParticipant,
  updateMeeting: mockUpdateMeeting,
  updateMeetingSchema: { and: () => ({}) },
  updateParticipantRole: mockUpdateParticipantRole,
  updateParticipantRoleSchema: { extend: () => ({}) },
}));

vi.mock("~/lib/domains/invites", () => ({
  InviteForm: () => null,
  InviteList: () => null,
  InviteServiceError: MockInviteServiceError,
  createInvite: mockCreateInvite,
  createInviteSchema: { extend: () => ({}) },
  fetchInvites: mockFetchInvites,
  revokeInvite: mockRevokeInvite,
}));

interface RouteCtx {
  sharedMap: Map<string, unknown>;
  cookie: { get: (name: string) => { value?: string } | undefined };
  query: URLSearchParams;
  fail: (status: number, payload: unknown) => unknown;
}

function createCtx(overrides?: Partial<RouteCtx>): RouteCtx {
  return {
    sharedMap: new Map<string, unknown>([
      ["user", { tenant: "tenant-a" }],
      ["apiUrl", "http://localhost:8080/api/v1"],
    ]),
    cookie: {
      get: (name: string) => {
        if (name === "JSESSIONID") return { value: "sess-1" };
        if (name === "XSRF-TOKEN") return { value: "csrf-1" };
        return undefined;
      },
    },
    query: new URLSearchParams(),
    fail: (status, payload) => ({ failed: true, status, payload }),
    ...overrides,
  };
}

describe("meetings route runtime", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("loads bounded previews including closed rooms without confusing failures with empty schedules", async () => {
    mockFetchRooms.mockResolvedValueOnce({
      content: [
        { roomId: "r1", status: "active" },
        { roomId: "r2", status: "closed" },
      ],
      page: 0,
      pageSize: 6,
      totalElements: 7,
      totalPages: 2,
    });
    mockFetchMeetings.mockResolvedValueOnce({
      content: [{ meetingId: "m1", roomId: "r1" }],
      totalElements: 9,
    });
    mockFetchMeetings.mockRejectedValueOnce(new Error("unavailable"));
    const mod = await import("~/routes/meetings/loaders");
    const result = await mod.useWorkspaceRooms(createCtx());
    expect(result.content).toHaveLength(2);
    expect(result.previews.r1).toEqual({
      content: [{ meetingId: "m1", roomId: "r1" }],
      totalElements: 9,
    });
    expect(result.previews.r2).toEqual({ error: true });
    expect(mockFetchRooms).toHaveBeenCalledWith(
      expect.anything(),
      "tenant-a",
      0,
      6,
    );
    expect(mockFetchMeetings).toHaveBeenCalledWith(
      expect.anything(),
      "r1",
      0,
      3,
    );
  });

  it("useWorkspaceRooms includes closed rooms and preserves backend failures", async () => {
    mockFetchRooms.mockResolvedValueOnce({
      content: [
        { roomId: "r1", status: "active" },
        { roomId: "r2", status: "closed" },
      ],
      page: 0,
      pageSize: 20,
      totalElements: 2,
      totalPages: 1,
    });
    mockFetchRooms.mockRejectedValueOnce(new Error("fail"));

    const mod = await import("~/routes/meetings/index");
    const ctx = createCtx();

    const filtered = await mod.useWorkspaceRooms({
      sharedMap: ctx.sharedMap,
      cookie: ctx.cookie,
    } as never);
    expect(filtered.content).toHaveLength(2);
    expect(filtered.content[0].roomId).toBe("r1");
    await expect(
      mod.useWorkspaceRooms({
        sharedMap: ctx.sharedMap,
        cookie: ctx.cookie,
      } as never),
    ).rejects.toThrow("fail");
  });

  it("keeps a selected room available beyond the current room page", async () => {
    const roomId = "11111111-1111-4111-8111-111111111111";
    mockFetchRooms.mockResolvedValue({
      content: [],
      page: 0,
      pageSize: 20,
      totalElements: 21,
      totalPages: 2,
    });
    mockFetchRoom.mockResolvedValue({ roomId, status: "active" });
    const mod = await import("~/routes/meetings/index");
    const result = await mod.useWorkspaceRooms(
      createCtx({
        query: new URLSearchParams(`roomId=${roomId}`),
      }) as never,
    );

    expect(result.content[0].roomId).toBe(roomId);
    expect(mockFetchRoom).toHaveBeenCalledWith(expect.anything(), roomId);
  });

  it("useMeetings handles roomId missing and success", async () => {
    mockFetchMeetings.mockResolvedValue({
      content: [{ meetingId: "m1" }],
      page: 0,
      pageSize: 20,
      totalElements: 1,
      totalPages: 1,
    });

    const mod = await import("~/routes/meetings/index");
    const noRoom = await mod.useMeetings(
      createCtx({ query: new URLSearchParams() }) as never,
    );
    const withRoom = await mod.useMeetings(
      createCtx({ query: new URLSearchParams("roomId=r1") }) as never,
    );

    expect(noRoom.content).toEqual([]);
    expect(withRoom.content).toHaveLength(1);
    expect(mockFetchMeetings).toHaveBeenCalledWith(
      {
        sessionCookie: "sess-1",
        csrfToken: "csrf-1",
        apiUrl: "http://localhost:8080/api/v1",
        headers: { Cookie: "JSESSIONID=sess-1" },
      },
      "r1",
      0,
    );
  });

  it("loaders request independent rooms, meetings and invites pages", async () => {
    const page = {
      content: [],
      page: 1,
      pageSize: 20,
      totalElements: 21,
      totalPages: 2,
    };
    mockFetchRooms.mockResolvedValue(page);
    mockFetchMeetings.mockResolvedValue(page);
    mockFetchInvites.mockResolvedValue(page);
    const mod = await import("~/routes/meetings/index");
    const ctx = createCtx({
      query: new URLSearchParams(
        "roomId=r1&invitesMeetingId=m1&roomsPage=1&meetingsPage=2&invitesPage=3",
      ),
    });

    await mod.useWorkspaceRooms(ctx as never);
    await mod.useMeetings(ctx as never);
    await mod.useInvites(ctx as never);

    expect(mockFetchRooms).toHaveBeenCalledWith(
      expect.anything(),
      "tenant-a",
      1,
      6,
    );
    expect(mockFetchMeetings).toHaveBeenCalledWith(expect.anything(), "r1", 2);
    expect(mockFetchInvites).toHaveBeenCalledWith(expect.anything(), "m1", 3);
  });

  it("loads selected meeting detail outside the current page", async () => {
    mockFetchMeeting.mockResolvedValue({
      meetingId: "11111111-1111-4111-8111-111111111111",
      roomId: "r1",
    });
    const mod = await import("~/routes/meetings/index");
    const result = await mod.useSelectedMeeting(
      createCtx({
        query: new URLSearchParams(
          "roomId=r1&meetingId=11111111-1111-4111-8111-111111111111",
        ),
      }) as never,
    );

    expect(result?.meetingId).toBe("11111111-1111-4111-8111-111111111111");
    expect(mockFetchMeeting).toHaveBeenCalledWith(
      expect.anything(),
      "11111111-1111-4111-8111-111111111111",
    );
  });

  it("useMeetings preserves backend failures instead of rendering a false empty state", async () => {
    mockFetchMeetings.mockRejectedValue(new Error("backend down"));

    const mod = await import("~/routes/meetings/index");
    await expect(
      mod.useMeetings(
        createCtx({ query: new URLSearchParams("roomId=r1") }) as never,
      ),
    ).rejects.toThrow("backend down");
  });

  it("useParticipants validates meetingId and returns [] for invalid", async () => {
    mockFetchParticipants.mockResolvedValue([{ subjectId: "u1" }]);

    const mod = await import("~/routes/meetings/index");
    const invalid = await mod.useParticipants(
      createCtx({
        query: new URLSearchParams("roomId=r1&meetingId=bad"),
      }) as never,
    );
    const valid = await mod.useParticipants(
      createCtx({
        query: new URLSearchParams(
          "roomId=r1&meetingId=11111111-1111-4111-8111-111111111111",
        ),
      }) as never,
    );

    expect(invalid).toEqual([]);
    expect(valid).toEqual([{ subjectId: "u1" }]);
  });

  it("useParticipants preserves backend failures instead of rendering a false empty state", async () => {
    mockFetchParticipants.mockRejectedValue(new Error("timeout"));

    const mod = await import("~/routes/meetings/index");
    await expect(
      mod.useParticipants(
        createCtx({
          query: new URLSearchParams(
            "roomId=r1&meetingId=11111111-1111-4111-8111-111111111111",
          ),
        }) as never,
      ),
    ).rejects.toThrow("timeout");
  });

  it("useAssignableUsers loads tenant-scoped user list for the active meeting", async () => {
    mockSearchUsers.mockResolvedValue([
      {
        subjectId: "u1",
        fullName: "Иванов Иван",
        organization: "ЦРБ",
        position: "Врач",
      },
    ]);

    const mod = await import("~/routes/meetings/index");
    const result = await mod.useAssignableUsers(
      createCtx({
        query: new URLSearchParams(
          "meetingId=11111111-1111-4111-8111-111111111111&participantQuery=иван",
        ),
      }) as never,
    );

    expect(result).toEqual([
      {
        subjectId: "u1",
        fullName: "Иванов Иван",
        organization: "ЦРБ",
        position: "Врач",
      },
    ]);
    expect(mockSearchUsers).toHaveBeenCalledWith(
      {
        sessionCookie: "sess-1",
        csrfToken: "csrf-1",
        apiUrl: "http://localhost:8080/api/v1",
        headers: { Cookie: "JSESSIONID=sess-1" },
      },
      "tenant-a",
      "иван",
      undefined,
    );
  });

  it("useAssignableUsers returns empty array when no valid meeting is selected", async () => {
    const mod = await import("~/routes/meetings/index");
    const result = await mod.useAssignableUsers(
      createCtx({ query: new URLSearchParams() }) as never,
    );

    expect(result).toEqual([]);
    expect(mockSearchUsers).not.toHaveBeenCalled();
  });

  it("useAssignableUsers preserves backend failures", async () => {
    mockSearchUsers.mockRejectedValue(new Error("directory unavailable"));

    const mod = await import("~/routes/meetings/index");
    await expect(
      mod.useAssignableUsers(
        createCtx({
          query: new URLSearchParams(
            "meetingId=11111111-1111-4111-8111-111111111111",
          ),
        }) as never,
      ),
    ).rejects.toThrow("directory unavailable");
  });

  it("useInvites handles absent meetingId and success", async () => {
    mockFetchInvites.mockResolvedValue({
      content: [{ id: "i1" }],
      page: 0,
      pageSize: 20,
      totalElements: 1,
      totalPages: 1,
    });

    const mod = await import("~/routes/meetings/index");
    const absent = await mod.useInvites(
      createCtx({ query: new URLSearchParams() }) as never,
    );
    const present = await mod.useInvites(
      createCtx({ query: new URLSearchParams("invitesMeetingId=m1") }) as never,
    );

    expect(absent.content).toEqual([]);
    expect(present.content).toHaveLength(1);
  });

  it("useInvites preserves backend failures", async () => {
    mockFetchInvites.mockRejectedValue(new Error("invites unavailable"));

    const mod = await import("~/routes/meetings/index");
    await expect(
      mod.useInvites(
        createCtx({
          query: new URLSearchParams("invitesMeetingId=m1"),
        }) as never,
      ),
    ).rejects.toThrow("invites unavailable");
  });

  it("useCreateInvite returns success and maps InviteServiceError", async () => {
    vi.spyOn(globalThis.crypto, "randomUUID").mockReturnValue("idem-1");
    mockCreateInvite.mockResolvedValueOnce({ id: "i1" });
    mockCreateInvite.mockRejectedValueOnce(
      new MockInviteServiceError({
        title: "bad",
        detail: "invite err",
        errorCode: "INVITE_EXPIRED",
      }),
    );

    const mod = await import("~/routes/meetings/index");
    const ctx = createCtx();

    const ok = await mod.useCreateInvite(
      { meetingId: "m1", role: "participant", maxUses: 1, expiresInHours: 24 },
      ctx as never,
    );
    const fail = await mod.useCreateInvite(
      { meetingId: "m1", role: "participant", maxUses: 1, expiresInHours: 24 },
      ctx as never,
    );

    expect(ok).toEqual({ success: true, invite: { id: "i1" } });
    expect(fail).toEqual({
      failed: true,
      status: 400,
      payload: {
        error: {
          title: "bad",
          detail: "invite err",
          errorCode: "INVITE_EXPIRED",
        },
      },
    });
  });

  it("useRevokeInvite maps unknown errors to fail(500)", async () => {
    vi.spyOn(console, "error").mockImplementation(() => undefined);
    mockRevokeInvite.mockRejectedValue(new Error("boom"));

    const mod = await import("~/routes/meetings/index");
    const result = await mod.useRevokeInvite(
      { meetingId: "m1", inviteId: "i1" },
      createCtx() as never,
    );

    expect(result).toEqual({
      failed: true,
      status: 500,
      payload: {
        error: {
          title: "Ошибка",
          detail: "Неизвестная ошибка",
          errorCode: "INVITE_UNKNOWN",
        },
      },
    });
  });

  it("useRevokeInvite maps InviteServiceError to fail(400)", async () => {
    mockRevokeInvite.mockRejectedValue(
      new MockInviteServiceError({
        title: "Not found",
        detail: "Invite missing",
        errorCode: "INVITE_NOT_FOUND",
      }),
    );

    const mod = await import("~/routes/meetings/index");
    const result = await mod.useRevokeInvite(
      { meetingId: "m1", inviteId: "i1" },
      createCtx() as never,
    );

    expect(result).toEqual({
      failed: true,
      status: 400,
      payload: {
        error: {
          title: "Not found",
          detail: "Invite missing",
          errorCode: "INVITE_NOT_FOUND",
        },
      },
    });
  });

  it("meeting actions cover success and service-error branches", async () => {
    vi.spyOn(globalThis.crypto, "randomUUID").mockReturnValue("idem-1");
    vi.spyOn(console, "error").mockImplementation(() => undefined);
    mockCreateMeeting.mockResolvedValue({ meetingId: "m1" });
    mockUpdateMeeting.mockRejectedValue(
      new MockMeetingServiceError({
        title: "bad",
        detail: "update err",
        errorCode: "VALIDATION_ERROR",
      }),
    );
    mockCancelMeeting.mockResolvedValue({
      meetingId: "m1",
      status: "canceled",
    });
    mockAssignParticipant.mockResolvedValue({
      subjectId: "u1",
      role: "participant",
    });
    mockBulkAssignParticipants.mockResolvedValue([
      { subjectId: "u2", role: "participant" },
    ]);
    mockUpdateParticipantRole.mockRejectedValue(new Error("boom"));
    mockUnassignParticipant.mockRejectedValue(
      new MockMeetingServiceError({
        title: "bad",
        detail: "unassign err",
        errorCode: "ASSIGNMENT_NOT_FOUND",
      }),
    );

    const mod = await import("~/routes/meetings/index");
    const ctx = createCtx();

    const created = await mod.useCreateMeeting(
      {
        roomId: "r1",
        title: "t",
        description: "d",
        meetingType: "planned",
        startsAt: "2026-03-03T10:00:00Z",
        endsAt: "2026-03-03T11:00:00Z",
        allowGuests: true,
        recordingEnabled: false,
      },
      ctx as never,
    );
    const updated = await mod.useUpdateMeeting(
      {
        meetingId: "m1",
        title: "t",
        description: "d",
        meetingType: "planned",
        startsAt: "2026-03-03T10:00:00Z",
        endsAt: "2026-03-03T11:00:00Z",
        allowGuests: true,
        recordingEnabled: false,
      },
      ctx as never,
    );
    const canceled = await mod.useCancelMeeting(
      { meetingId: "m1" },
      ctx as never,
    );
    const assigned = await mod.useAssignParticipant(
      { meetingId: "m1", subjectId: "u1", role: "participant" },
      ctx as never,
    );
    const bulkAssigned = await mod.useBulkAssignParticipants(
      { meetingId: "m1", subjectIds: ["u2"], defaultRole: "participant" },
      ctx as never,
    );
    const updateRole = await mod.useUpdateParticipantRole(
      { meetingId: "m1", subjectId: "u1", role: "moderator" },
      ctx as never,
    );
    const unassigned = await mod.useUnassignParticipant(
      { meetingId: "m1", subjectId: "u1" },
      ctx as never,
    );

    expect(created).toEqual({ success: true, meeting: { meetingId: "m1" } });
    expect(updated).toEqual({
      failed: true,
      status: 400,
      payload: {
        error: {
          title: "bad",
          detail: "update err",
          errorCode: "VALIDATION_ERROR",
        },
      },
    });
    expect(canceled).toEqual({
      success: true,
      meeting: { meetingId: "m1", status: "canceled" },
    });
    expect(assigned).toEqual({
      success: true,
      assignment: { subjectId: "u1", role: "participant" },
    });
    expect(bulkAssigned).toEqual({
      success: true,
      assignments: [{ subjectId: "u2", role: "participant" }],
    });
    expect(updateRole).toEqual({
      failed: true,
      status: 500,
      payload: {
        error: {
          title: "Ошибка",
          detail: "Неизвестная ошибка",
          errorCode: "MEETING_UNKNOWN",
        },
      },
    });
    expect(unassigned).toEqual({
      failed: true,
      status: 400,
      payload: {
        error: {
          title: "bad",
          detail: "unassign err",
          errorCode: "ASSIGNMENT_NOT_FOUND",
        },
      },
    });
  });
});
