/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
import { beforeEach, describe, expect, it, vi } from "vitest";

const mockLogoutFromAuthSession = vi.fn();
const mockResolveAuthRedirectPath = vi.fn();
const mockBuildMutationRequestContext = vi.fn();

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
    useStore: <T extends object>(value: T) => value,
    useContextProvider: noop,
    Slot: noop,
  };
});

vi.mock("@qwik.dev/router", async (importOriginal) => {
  const actual = await importOriginal();
  const identity = <T>(value: T): T => value;
  return {
    ...actual,
    routeLoader$: identity,
    routeLoaderQrl: identity,
    routeAction$: identity,
    routeActionQrl: identity,
  };
});

vi.mock("~/lib/shared/components", () => ({
  AppHeader: () => null,
  Sidebar: () => null,
}));

vi.mock("~/lib/shared/stores/theme-context", () => ({
  ThemeContext: "ThemeContext",
}));

vi.mock("~/lib/domains/auth", () => ({
  AuthContext: "AuthContext",
  fetchAuthMe: vi.fn(),
  isPublicAuthPath: vi.fn(() => false),
  logoutFromAuthSession: mockLogoutFromAuthSession,
  resolveAuthRedirectPath: mockResolveAuthRedirectPath,
}));

vi.mock("~/lib/shared/routes/server-handlers", () => ({
  buildMutationRequestContext: mockBuildMutationRequestContext,
  buildServerRequestContext: vi.fn(() => ({ sessionCookie: "sess-1" })),
}));

function createActionCtx() {
  return {
    sharedMap: new Map<string, unknown>([["apiUrl", "http://localhost:8080/api/v1"]]),
    cookie: {
      get: (name: string) => {
        if (name === "JSESSIONID") return { value: "sess-1" };
        if (name === "XSRF-TOKEN") return { value: "csrf-1" };
        return undefined;
      },
      set: vi.fn(),
    },
    redirect: (status: number, to: string) => ({ type: "redirect", status, to }),
  };
}

describe("layout route runtime", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.clearAllMocks();
    mockBuildMutationRequestContext.mockResolvedValue({
      apiUrl: "http://localhost:8080/api/v1",
      sessionCookie: "sess-1",
      csrfToken: "csrf-1",
      csrfCookieToken: "csrf-1",
      idempotencyKey: "idem-1",
      headers: {
        Cookie: "JSESSIONID=sess-1; XSRF-TOKEN=csrf-1",
        "Content-Type": "application/json",
        "X-XSRF-TOKEN": "csrf-1",
        "Idempotency-Key": "idem-1",
      },
    });
  });

  it("useLogout preserves successful provider redirect", async () => {
    mockLogoutFromAuthSession.mockResolvedValue("https://issuer.example.test/protocol/openid-connect/logout");

    const mod = await import("~/routes/layout");

    await expect(mod.useLogout({}, createActionCtx() as never)).rejects.toEqual({
      type: "redirect",
      status: 302,
      to: "https://issuer.example.test/protocol/openid-connect/logout",
    });

    expect(mockResolveAuthRedirectPath).not.toHaveBeenCalled();
  });

  it("useLogout falls back to centralized auth redirect on auth errors", async () => {
    const authError = new Error("expired");
    mockLogoutFromAuthSession.mockRejectedValue(authError);
    mockResolveAuthRedirectPath.mockReturnValue("/auth?error=AUTH_REQUIRED");

    const mod = await import("~/routes/layout");

    await expect(mod.useLogout({}, createActionCtx() as never)).rejects.toEqual({
      type: "redirect",
      status: 302,
      to: "/auth?error=AUTH_REQUIRED",
    });

    expect(mockResolveAuthRedirectPath).toHaveBeenCalledWith(authError);
  });
});