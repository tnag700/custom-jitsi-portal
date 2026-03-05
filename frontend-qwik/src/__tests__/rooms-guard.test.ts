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
    expect(ts).toContain("idempotencyKey?: string");
    expect(ts).toContain("headers[\"Idempotency-Key\"] = idempotencyKey");
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
  it("routes/rooms/index.tsx should contain routeLoader$ and routeAction$", () => {
    const tsx = readSrc("routes/rooms/index.tsx");
    expect(tsx).toContain("routeLoader$");
    expect(tsx).toContain("routeAction$");
  });

  it("routes/rooms/index.tsx should use Form actions for close/delete and RoomForm for create/update", () => {
    const tsx = readSrc("routes/rooms/index.tsx");
    expect(tsx).toContain("<Form action={closeAction}>");
    expect(tsx).toContain("<Form action={deleteAction}>");
    expect(tsx).toContain("action={createAction}");
    expect(tsx).toContain("action={updateAction}");
  });

  it("routes/rooms/index.tsx should use ApiErrorAlert for close/delete confirmation errors", () => {
    const tsx = readSrc("routes/rooms/index.tsx");
    expect(tsx).toContain("ApiErrorAlert");
    expect(tsx).toContain("Ошибка закрытия комнаты");
    expect(tsx).toContain("Ошибка удаления комнаты");
  });

  it("routes/rooms/index.tsx should define explicit roomId schema", () => {
    const tsx = readSrc("routes/rooms/index.tsx");
    expect(tsx).toContain("const roomIdSchema = z.object");
  });
});
