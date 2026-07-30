import { describe, expect, it } from "vitest";
import {
  createInviteSchema,
  exchangeInviteSchema,
} from "../lib/domains/invites/invites.zod";

describe("guest invite policy", () => {
  it("accepts only participant links with an explicit expiration", () => {
    expect(
      createInviteSchema.parse({
        role: "participant",
        maxUses: "1",
        expiresInHours: "24",
      }),
    ).toEqual({
      role: "participant",
      maxUses: 1,
      expiresInHours: 24,
    });

    expect(
      createInviteSchema.safeParse({
        role: "moderator",
        maxUses: 1,
        expiresInHours: 24,
      }).success,
    ).toBe(false);
    expect(
      createInviteSchema.safeParse({
        role: "participant",
        maxUses: 1,
        expiresInHours: "",
      }).success,
    ).toBe(false);
  });

  it("requires a normalized guest display name", () => {
    expect(
      exchangeInviteSchema.parse({
        inviteToken: "token-1",
        displayName: "  Иван Гость  ",
      }),
    ).toEqual({
      inviteToken: "token-1",
      displayName: "Иван Гость",
    });

    expect(
      exchangeInviteSchema.safeParse({
        inviteToken: "token-1",
        displayName: " ",
      }).success,
    ).toBe(false);
  });
});
