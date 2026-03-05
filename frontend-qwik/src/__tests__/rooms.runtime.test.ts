import { afterEach, describe, expect, it, vi } from "vitest";
import {
  RoomServiceError,
  closeRoom,
  createRoom,
  deleteRoom,
  fetchRooms,
  updateRoom,
} from "../lib/domains/rooms/rooms.service";

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

describe("rooms.service runtime: fetchRooms", () => {
  it("calls rooms endpoint with tenant/page/size and cookie header", async () => {
    const payload = {
      content: [],
      page: 2,
      pageSize: 10,
      totalElements: 0,
      totalPages: 0,
    };

    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(payload, 200));

    const result = await fetchRooms("sess-1", "http://localhost:8080/api/v1", "tenant a/b", 2, 10);

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/rooms?tenantId=tenant%20a%2Fb&page=2&size=10",
      {
        method: "GET",
        headers: {
          Cookie: "JSESSIONID=sess-1",
          "Content-Type": "application/json",
        },
      },
    );
    expect(result).toEqual(payload);
  });

  it("throws RoomServiceError with fallback 404 code on non-JSON payload", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response("not-json", {
        status: 404,
        headers: {
          "content-type": "text/plain",
        },
      }),
    );

    await expect(fetchRooms("sess-1", "http://localhost:8080/api/v1", "tenant-1")).rejects.toMatchObject({
      name: "RoomServiceError",
      payload: {
        errorCode: "ROOM_NOT_FOUND",
      },
    });
  });
});

describe("rooms.service runtime: mutations", () => {
  it("createRoom sends csrf + idempotency headers and returns created room", async () => {
    const room = {
      roomId: "r-1",
      name: "Room A",
      description: null,
      tenantId: "tenant-1",
      configSetId: "config-1",
      status: "active",
      createdAt: "2026-03-03T10:00:00Z",
      updatedAt: "2026-03-03T10:00:00Z",
    };
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(room, 201));

    const result = await createRoom("sess-1", "http://localhost:8080/api/v1", "csrf-1", "idem-1", {
      name: "Room A",
      tenantId: "tenant-1",
      configSetId: "config-1",
    });

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/rooms",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({
          Cookie: "JSESSIONID=sess-1",
          "X-XSRF-TOKEN": "csrf-1",
          "Idempotency-Key": "idem-1",
        }),
      }),
    );
    expect(result).toEqual(room);
  });

  it("updateRoom encodes roomId and maps explicit problem details", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse(
        {
          title: "Conflict",
          detail: "Name already exists",
          errorCode: "ROOM_NAME_CONFLICT",
          traceId: "trace-1",
        },
        409,
      ),
    );

    await expect(
      updateRoom("sess-1", "http://localhost:8080/api/v1", "csrf-1", "idem-2", "room a/b", {
        name: "Room B",
      }),
    ).rejects.toMatchObject({
      name: "RoomServiceError",
      payload: {
        title: "Conflict",
        detail: "Name already exists",
        errorCode: "ROOM_NAME_CONFLICT",
        traceId: "trace-1",
      },
    });

    expect(globalThis.fetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/rooms/room%20a%2Fb",
      expect.objectContaining({ method: "PUT" }),
    );
  });

  it("closeRoom maps 5xx fallback error code", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response("oops", {
        status: 503,
        headers: {
          "content-type": "text/plain",
        },
      }),
    );

    await expect(
      closeRoom("sess-1", "http://localhost:8080/api/v1", "csrf-1", "idem-3", "room-1"),
    ).rejects.toMatchObject({
      name: "RoomServiceError",
      payload: {
        errorCode: "ROOM_SERVICE_UNAVAILABLE",
      },
    });
  });

  it("deleteRoom sends DELETE and resolves with void on success", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(null, { status: 204 }));

    await expect(
      deleteRoom("sess-1", "http://localhost:8080/api/v1", "csrf-1", "idem-4", "room-1"),
    ).resolves.toBeUndefined();

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/rooms/room-1",
      expect.objectContaining({
        method: "DELETE",
        headers: expect.objectContaining({
          "X-XSRF-TOKEN": "csrf-1",
          "Idempotency-Key": "idem-4",
        }),
      }),
    );
  });

  it("deleteRoom throws RoomServiceError instance on backend error", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse({}, 500));

    try {
      await deleteRoom("sess-1", "http://localhost:8080/api/v1", "csrf-1", "idem-5", "room-1");
      throw new Error("Expected deleteRoom to throw RoomServiceError");
    } catch (error) {
      expect(error).toBeInstanceOf(RoomServiceError);
      const roomError = error as RoomServiceError;
      expect(roomError.payload.errorCode).toBe("ROOM_SERVICE_UNAVAILABLE");
    }
  });
});
