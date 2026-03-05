import { beforeEach, describe, expect, it, vi } from "vitest";

const mockFetchRooms = vi.fn();
const mockCreateRoom = vi.fn();
const mockUpdateRoom = vi.fn();
const mockCloseRoom = vi.fn();
const mockDeleteRoom = vi.fn();

class MockRoomServiceError extends Error {
  payload: { title: string; detail: string; errorCode: string; traceId?: string };

  constructor(payload: { title: string; detail: string; errorCode: string; traceId?: string }) {
    super(payload.detail);
    this.name = "RoomServiceError";
    this.payload = payload;
  }
}

vi.mock("@qwik.dev/core", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@qwik.dev/core")>();
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
  };
});

vi.mock("@qwik.dev/router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@qwik.dev/router")>();
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
    z: {
      object: (shape: unknown) => shape,
      string: stringSchema,
    },
  };
});

vi.mock("~/lib/shared", () => ({
  ApiErrorAlert: () => null,
}));

vi.mock("~/lib/domains/rooms", () => ({
  fetchRooms: mockFetchRooms,
  createRoom: mockCreateRoom,
  updateRoom: mockUpdateRoom,
  closeRoom: mockCloseRoom,
  deleteRoom: mockDeleteRoom,
  RoomServiceError: MockRoomServiceError,
  createRoomSchema: {},
  updateRoomSchema: { extend: () => ({}) },
  RoomList: () => null,
  RoomForm: () => null,
}));

interface RouteCtx {
  sharedMap: Map<string, unknown>;
  cookie: { get: (name: string) => { value?: string } | undefined };
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
    fail: (status, payload) => ({ failed: true, status, payload }),
    ...overrides,
  };
}

describe("rooms route runtime", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.clearAllMocks();
    vi.spyOn(globalThis.crypto, "randomUUID").mockReturnValue("idem-1");
  });

  it("useRooms loads rooms for tenant", async () => {
    const payload = { content: [], page: 0, pageSize: 20, totalElements: 0, totalPages: 0 };
    mockFetchRooms.mockResolvedValue(payload);

    const mod = await import("~/routes/rooms/index");
    const result = await mod.useRooms({ sharedMap: createCtx().sharedMap, cookie: createCtx().cookie } as never);

    expect(mockFetchRooms).toHaveBeenCalledWith("sess-1", "http://localhost:8080/api/v1", "tenant-a");
    expect(result).toEqual(payload);
  });

  it("useCreateRoom returns success payload", async () => {
    const room = { roomId: "r1", name: "Room 1" };
    mockCreateRoom.mockResolvedValue(room);

    const mod = await import("~/routes/rooms/index");
    const ctx = createCtx();
    const result = await mod.useCreateRoom({ name: "Room 1", timezone: "UTC" }, ctx as never);

    expect(mockCreateRoom).toHaveBeenCalledWith(
      "sess-1",
      "http://localhost:8080/api/v1",
      "csrf-1",
      "idem-1",
      expect.objectContaining({ tenantId: "tenant-a", name: "Room 1" }),
    );
    expect(result).toEqual({ success: true, room });
  });

  it("useUpdateRoom maps RoomServiceError to fail(400)", async () => {
    mockUpdateRoom.mockRejectedValue(
      new MockRoomServiceError({ title: "Bad request", detail: "room invalid", errorCode: "VALIDATION_ERROR" }),
    );

    const mod = await import("~/routes/rooms/index");
    const ctx = createCtx();
    const result = await mod.useUpdateRoom({ roomId: "r1", name: "X" }, ctx as never);

    expect(result).toEqual({
      failed: true,
      status: 400,
      payload: {
        error: {
          title: "Bad request",
          detail: "room invalid",
          errorCode: "VALIDATION_ERROR",
        },
      },
    });
  });

  it("useUpdateRoom strips roomId from payload and returns success", async () => {
    const room = { roomId: "r1", name: "Updated" };
    mockUpdateRoom.mockResolvedValue(room);

    const mod = await import("~/routes/rooms/index");
    const ctx = createCtx();
    const result = await mod.useUpdateRoom({ roomId: "r1", name: "Updated", timezone: "UTC" }, ctx as never);

    expect(mockUpdateRoom).toHaveBeenCalledWith(
      "sess-1",
      "http://localhost:8080/api/v1",
      "csrf-1",
      "idem-1",
      "r1",
      { name: "Updated", timezone: "UTC" },
    );
    expect(result).toEqual({ success: true, room });
  });

  it("useCloseRoom maps unknown errors to fail(500)", async () => {
    mockCloseRoom.mockRejectedValue(new Error("boom"));

    const mod = await import("~/routes/rooms/index");
    const ctx = createCtx();
    const result = await mod.useCloseRoom({ roomId: "r1" }, ctx as never);

    expect(result).toEqual({
      failed: true,
      status: 500,
      payload: {
        error: {
          title: "Ошибка",
          detail: "Неизвестная ошибка",
          errorCode: "ROOM_UNKNOWN",
        },
      },
    });
  });

  it("useDeleteRoom returns success on delete", async () => {
    mockDeleteRoom.mockResolvedValue(undefined);

    const mod = await import("~/routes/rooms/index");
    const ctx = createCtx();
    const result = await mod.useDeleteRoom({ roomId: "r1" }, ctx as never);

    expect(mockDeleteRoom).toHaveBeenCalledWith("sess-1", "http://localhost:8080/api/v1", "csrf-1", "idem-1", "r1");
    expect(result).toEqual({ success: true });
  });

  it("useDeleteRoom maps RoomServiceError to fail(400)", async () => {
    mockDeleteRoom.mockRejectedValue(
      new MockRoomServiceError({
        title: "Conflict",
        detail: "Room has active meetings",
        errorCode: "ROOM_HAS_ACTIVE_MEETINGS",
      }),
    );

    const mod = await import("~/routes/rooms/index");
    const result = await mod.useDeleteRoom({ roomId: "r1" }, createCtx() as never);

    expect(result).toEqual({
      failed: true,
      status: 400,
      payload: {
        error: {
          title: "Conflict",
          detail: "Room has active meetings",
          errorCode: "ROOM_HAS_ACTIVE_MEETINGS",
        },
      },
    });
  });
});
