/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
import { describe, expect, it, vi } from "vitest";

vi.mock("@qwik.dev/core", async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    component$: <T>(value: T): T => value,
  };
});

vi.mock("@qwik.dev/router", async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    routeLoader$: <T>(value: T): T => value,
  };
});

vi.mock("~/lib/domains/join", () => ({
  fetchJoinReadiness: vi.fn(),
}));

function createRequestContext(claims: string[]) {
  return {
    sharedMap: new Map([
      [
        "user",
        {
          id: "operator-1",
          claims,
        },
      ],
    ]),
    redirect: (status: number, to: string) => ({
      type: "redirect",
      status,
      to,
    }),
  };
}

describe("admin Jitsi route access", () => {
  it("allows a platform admin", async () => {
    const mod = await import("~/routes/admin/jitsi");

    expect(() =>
      mod.onRequest(createRequestContext(["ROLE_ADMIN"]) as never),
    ).not.toThrow();
  });

  it("redirects support and security operators to the admin overview", async () => {
    const mod = await import("~/routes/admin/jitsi");

    expect(() =>
      mod.onRequest(createRequestContext(["ROLE_SUPPORT-ENGINEER"]) as never),
    ).toThrow();

    try {
      void mod.onRequest(
        createRequestContext(["ROLE_SECURITY-ADMIN"]) as never,
      );
    } catch (error) {
      expect(error).toEqual({
        type: "redirect",
        status: 302,
        to: "/admin",
      });
    }
  });
});
