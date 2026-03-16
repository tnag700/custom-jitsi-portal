import { describe, it, expect } from "vitest";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

const SRC_DIR = join(__dirname, "..");

function readSrc(relativePath: string): string {
  const full = join(SRC_DIR, relativePath);
  if (!existsSync(full)) {
    throw new Error(`File not found: ${relativePath}`);
  }
  return readFileSync(full, "utf-8");
}

describe("Rooms Guard: types (AC: 1)", () => {
  it("types.ts should define Room, PagedRoomResponse, CreateRoomRequest, UpdateRoomRequest", () => {
    const ts = readSrc("lib/domains/rooms/types.ts");
    expect(ts).toContain("Room");
    expect(ts).toContain("PagedRoomResponse");
    expect(ts).toContain("CreateRoomRequest");
    expect(ts).toContain("UpdateRoomRequest");
  });
});

describe("Rooms Guard: service (AC: 1, 3-6)", () => {
  it("rooms.service.ts should contain fetchRooms, createRoom, updateRoom, closeRoom, deleteRoom", () => {
    const ts = readSrc("lib/domains/rooms/rooms.service.ts");
    expect(ts).toContain("fetchRooms");
    expect(ts).toContain("createRoom");
    expect(ts).toContain("updateRoom");
    expect(ts).toContain("closeRoom");
    expect(ts).toContain("deleteRoom");
  });

  it("rooms.service.ts should include Idempotency-Key support in mutation headers", () => {
    const ts = readSrc("lib/domains/rooms/rooms.service.ts");
    expect(ts).toContain("idempotencyKey");
    expect(ts).toContain("asMutationRequestContext");
  });
});

describe("Rooms Guard: zod schemas (AC: 3, 4)", () => {
  it("rooms.zod.ts should contain createRoomSchema, updateRoomSchema", () => {
    const ts = readSrc("lib/domains/rooms/rooms.zod.ts");
    expect(ts).toContain("createRoomSchema");
    expect(ts).toContain("updateRoomSchema");
  });
});

describe("Rooms Guard: components (AC: 1, 2, 3, 4)", () => {
  it("RoomCard.tsx should contain component$", () => {
    const tsx = readSrc("lib/domains/rooms/components/RoomCard.tsx");
    expect(tsx).toContain("component$");
  });

  it("RoomForm.tsx should contain component$", () => {
    const tsx = readSrc("lib/domains/rooms/components/RoomForm.tsx");
    expect(tsx).toContain("component$");
  });

  it("RoomForm.tsx should use shared ApiErrorAlert for API errors", () => {
    const tsx = readSrc("lib/domains/rooms/components/RoomForm.tsx");
    expect(tsx).toContain("ApiErrorAlert");
  });

  it("RoomList.tsx should contain component$", () => {
    const tsx = readSrc("lib/domains/rooms/components/RoomList.tsx");
    expect(tsx).toContain("component$");
  });
});

describe("Rooms Guard: barrel export (AC: all)", () => {
  it("index.ts should exist as barrel export", () => {
    const ts = readSrc("lib/domains/rooms/index.ts");
    expect(ts).toBeDefined();
  });
});

describe("Rooms Guard: route (AC: 1-7)", () => {
  it("routes/rooms/index.tsx should re-export route handlers and page boundary", () => {
    const tsx = readSrc("routes/rooms/index.tsx");
    expect(tsx).toContain("useRooms");
    expect(tsx).toContain("useCreateRoom");
    expect(tsx).toContain("./route-handlers");
    expect(tsx).toContain("./rooms-page");
  });

  it("routes/rooms/route-handlers.ts should contain routeLoader$ and routeAction$ with explicit roomId schema", () => {
    const ts = readSrc("routes/rooms/route-handlers.ts");
    expect(ts).toContain("routeLoader$");
    expect(ts).toContain("routeAction$");
    expect(ts).toContain("const roomIdSchema = z.object");
  });

  it("routes/rooms/rooms-page.tsx should wire RoomForm actions and confirmation slots with ApiErrorAlert", () => {
    const tsx = readSrc("routes/rooms/rooms-page.tsx");
    expect(tsx).toContain("<Form q:slot=\"actions\" action={closeAction}>");
    expect(tsx).toContain("<Form q:slot=\"actions\" action={deleteAction}>");
    expect(tsx).toContain("action={createAction}");
    expect(tsx).toContain("action={updateAction}");
    expect(tsx).toContain("AppDialog");
    expect(tsx).toContain("ApiErrorAlert");
    expect(tsx).toContain("Ошибка закрытия комнаты");
    expect(tsx).toContain("Ошибка удаления комнаты");
  });
});
