/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
import { beforeEach, describe, expect, it, vi } from "vitest";

const mockFetchAuthMe = vi.fn();
const mockResolveAuthRedirectPath = vi.fn();
const mockResolvePostAuthRedirectPath = vi.fn();
const mockBuildServerRequestContext = vi.fn();

vi.mock("@qwik.dev/core", async (importOriginal) => {
  const actual = await importOriginal();
  const identity = <T>(value: T): T => value;
  return {
    ...actual,
    component$: identity,
  };
});

vi.mock("@qwik.dev/router", async (importOriginal) => {
  const actual = await importOriginal();
  const identity = <T>(value: T): T => value;
  return {
    ...actual,
    routeLoader$: identity,
    routeLoaderQrl: identity,
  };
});

vi.mock("~/lib/domains/auth", () => ({
  fetchAuthMe: mockFetchAuthMe,
  resolveAuthRedirectPath: mockResolveAuthRedirectPath,
  resolvePostAuthRedirectPath: mockResolvePostAuthRedirectPath,
}));

vi.mock("~/lib/shared/routes/server-handlers", () => ({
  buildServerRequestContext: mockBuildServerRequestContext,
}));

function createCtx() {
  return {
    sharedMap: new Map<string, unknown>(),
    url: new URL("http://localhost:3000/auth/continue?returnTo=%2Fprofile"),
    cookie: {
      get: vi.fn(),
    },
    redirect: (status: number, to: string) => ({
      type: "redirect",
      status,
      to,
    }),
  };
}

describe("auth continue route runtime", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.clearAllMocks();
    mockBuildServerRequestContext.mockReturnValue({
      apiUrl: "http://localhost:8080/api/v1",
      sessionCookie: "sess-1",
      csrfToken: "csrf-1",
      headers: {
        Cookie: "JSESSIONID=sess-1",
      },
    });
    mockResolveAuthRedirectPath.mockReturnValue(
      "/auth?error=AUTH_REQUIRED&returnTo=%2Fprofile",
    );
    mockResolvePostAuthRedirectPath.mockReturnValue("/profile");
    mockFetchAuthMe.mockResolvedValue({
      id: "u-1",
      displayName: "Dev Admin",
      email: "dev@acme.local",
      tenant: "acme",
      claims: ["participant"],
    });
  });

  it("falls back to stable manual auth entry when continuation has no session cookie", async () => {
    mockBuildServerRequestContext.mockReturnValue({
      apiUrl: "http://localhost:8080/api/v1",
      sessionCookie: "",
      csrfToken: "",
      headers: {},
    });

    const mod = await import("~/routes/auth/continue/index");

    await expect(mod.useAuthContinue(createCtx() as never)).rejects.toEqual({
      type: "redirect",
      status: 302,
      to: "/auth?error=AUTH_REQUIRED&returnTo=%2Fprofile",
    });

    expect(mockResolveAuthRedirectPath).toHaveBeenCalledWith(
      undefined,
      "/profile",
    );
  });

  it("falls back to stable manual auth entry when post-login profile fetch fails", async () => {
    const authError = new Error("expired");
    mockFetchAuthMe.mockRejectedValue(authError);

    const mod = await import("~/routes/auth/continue/index");

    await expect(mod.useAuthContinue(createCtx() as never)).rejects.toEqual({
      type: "redirect",
      status: 302,
      to: "/auth?error=AUTH_REQUIRED&returnTo=%2Fprofile",
    });

    expect(mockResolveAuthRedirectPath).toHaveBeenCalledWith(
      authError,
      "/profile",
    );
  });

  it("stores the established user session and redirects to the safe destination", async () => {
    const sharedMap = new Map<string, unknown>();
    const mod = await import("~/routes/auth/continue/index");

    await expect(
      mod.useAuthContinue({ ...createCtx(), sharedMap } as never),
    ).rejects.toEqual({
      type: "redirect",
      status: 302,
      to: "/profile",
    });

    expect(sharedMap.get("user")).toEqual({
      id: "u-1",
      displayName: "Dev Admin",
      email: "dev@acme.local",
      tenant: "acme",
      claims: ["participant"],
    });
    expect(mockResolvePostAuthRedirectPath).toHaveBeenCalledWith("/profile");
  });
});
