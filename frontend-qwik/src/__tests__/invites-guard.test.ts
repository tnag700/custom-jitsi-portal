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

describe("Invites Guard: types (AC: 1, 2)", () => {
  it("types.ts should define Invite domain interfaces", () => {
    const ts = readSrc("lib/domains/invites/types.ts");
    expect(ts).toContain("Invite");
    expect(ts).toContain("PagedInviteResponse");
    expect(ts).toContain("CreateInviteRequest");
    expect(ts).toContain("InviteErrorPayload");
  });
});

describe("Invites Guard: services (AC: 1, 2, 4, 5)", () => {
  it("invites.service.ts should contain fetchInvites, createInvite, revokeInvite", () => {
    const ts = readSrc("lib/domains/invites/invites.service.ts");
    expect(ts).toContain("fetchInvites");
    expect(ts).toContain("createInvite");
    expect(ts).toContain("revokeInvite");
  });

  it("invite-exchange.service.ts should contain exchangeInvite", () => {
    const ts = readSrc("lib/domains/invites/invite-exchange.service.ts");
    expect(ts).toContain("exchangeInvite");
  });
});

describe("Invites Guard: zod schemas (AC: 2, 5)", () => {
  it("invites.zod.ts should contain createInviteSchema", () => {
    const ts = readSrc("lib/domains/invites/invites.zod.ts");
    expect(ts).toContain("createInviteSchema");
  });
});

describe("Invites Guard: components (AC: 1, 2)", () => {
  it("InviteList.tsx should contain component$", () => {
    const tsx = readSrc("lib/domains/invites/components/InviteList.tsx");
    expect(tsx).toContain("component$");
  });

  it("InviteForm.tsx should contain component$", () => {
    const tsx = readSrc("lib/domains/invites/components/InviteForm.tsx");
    expect(tsx).toContain("component$");
  });

  it("InviteForm.tsx should use shared ApiErrorAlert for API errors", () => {
    const tsx = readSrc("lib/domains/invites/components/InviteForm.tsx");
    expect(tsx).toContain("ApiErrorAlert");
  });

  it("InviteCard.tsx should contain component$", () => {
    const tsx = readSrc("lib/domains/invites/components/InviteCard.tsx");
    expect(tsx).toContain("component$");
  });
});

describe("Invites Guard: barrel export (AC: all)", () => {
  it("index.ts should exist as invites barrel export", () => {
    const ts = readSrc("lib/domains/invites/index.ts");
    expect(ts).toBeDefined();
  });
});

describe("Invites Guard: routes (AC: 1-6)", () => {
  it("routes/meetings/index.tsx should contain invite actions/loaders", () => {
    const tsx = readSrc("routes/meetings/index.tsx");
    expect(tsx).toContain("fetchInvites");
    expect(tsx).toContain("createInvite");
    expect(tsx).toContain("revokeInvite");
  });

  it("routes/invite/[inviteToken]/index.tsx should contain routeLoader$", () => {
    const tsx = readSrc("routes/invite/[inviteToken]/index.tsx");
    expect(tsx).toContain("routeLoader$");
  });

  it("routes/invite/[inviteToken]/index.tsx should use ApiErrorAlert for invite errors", () => {
    const tsx = readSrc("routes/invite/[inviteToken]/index.tsx");
    expect(tsx).toContain("ApiErrorAlert");
    expect(tsx).toContain("Ошибка инвайта");
  });
}
);
