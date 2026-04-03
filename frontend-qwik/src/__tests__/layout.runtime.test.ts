/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
import { beforeEach, describe, expect, it, vi } from "vitest";

const mockFetchAuthMe = vi.fn();
const mockIsPublicAuthPath = vi.fn();
const mockLogoutFromAuthSession = vi.fn();
const mockResolveAuthRedirectPath = vi.fn();
const mockResolveAuthRecoveryRedirectPath = vi.fn();
const mockBuildMutationRequestContext = vi.fn();
const mockBuildServerRequestContext = vi.fn();

class MockAuthServiceError extends Error {
  payload: { title: string; reason: string; actions: string; errorCode: string };

  constructor(payload: { title: string; reason: string; actions: string; errorCode: string }) {
    super(payload.reason);
    this.name = "AuthServiceError";
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
  AuthServiceError: MockAuthServiceError,
  fetchAuthMe: mockFetchAuthMe,
  isPublicAuthPath: mockIsPublicAuthPath,
  logoutFromAuthSession: mockLogoutFromAuthSession,
  resolveAuthRecoveryRedirectPath: mockResolveAuthRecoveryRedirectPath,
  resolveAuthRedirectPath: mockResolveAuthRedirectPath,
}));

vi.mock("~/lib/shared/routes/server-handlers", () => ({
  buildMutationRequestContext: mockBuildMutationRequestContext,
  buildServerRequestContext: mockBuildServerRequestContext,
}));

function createRequestCtx() {
  return {
    sharedMap: new Map<string, unknown>(),
    env: {
      get: (key: string) => {
        if (key === "API_URL" || key === "PUBLIC_API_URL") {
          return "http://localhost:8080/api/v1";
        }
        return undefined;
      },
    },
    url: new URL("http://localhost:3000/admin/config-sets?environment=dev"),
    request: {
      headers: new Headers({
        cookie: "JSESSIONID=sess-1; XSRF-TOKEN=csrf-1",
      }),
    },
    cookie: {
      get: (name: string) => {
        if (name === "JSESSIONID") return { value: "sess-1" };
        if (name === "XSRF-TOKEN") return { value: "csrf-1" };
        if (name === "theme") return undefined;
        return undefined;
      },
      set: vi.fn(),
    },
    redirect: (status: number, to: string) => ({ type: "redirect", status, to }),
  };
}

function createActionCtx() {
  return {
    sharedMap: new Map<string, unknown>([["apiUrl", "http://localhost:8080/api/v1"]]),
    env: {
      get: (name: string) => {
        if (name === "AUTH_LOGOUT_ALLOWED_ORIGINS") {
          return "";
        }
        return undefined;
      },
    },
    url: new URL("http://localhost:3000/admin/config-sets?environment=dev"),
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
    mockIsPublicAuthPath.mockReturnValue(false);
    mockFetchAuthMe.mockResolvedValue({
      id: "u-1",
      displayName: "Dev Admin",
      email: "dev@acme.local",
      tenant: "acme",
      claims: ["participant"],
    });
    mockBuildServerRequestContext.mockReturnValue({
      apiUrl: "http://localhost:8080/api/v1",
      sessionCookie: "sess-1",
      csrfToken: "csrf-1",
      headers: {
        Cookie: "JSESSIONID=sess-1",
      },
    });
    mockResolveAuthRecoveryRedirectPath.mockReturnValue(
      "/auth?error=AUTH_REQUIRED&mode=recover&returnTo=%2Fadmin%2Fconfig-sets%3Fenvironment%3Ddev",
    );
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

  it("onRequest loads authenticated users without eager mutation bootstrap", async () => {
    const mod = await import("~/routes/layout");
    const ctx = createRequestCtx();

    await expect(mod.onRequest(ctx as never)).resolves.toBeUndefined();

    expect(mockFetchAuthMe).toHaveBeenCalledWith({
      apiUrl: "http://localhost:8080/api/v1",
      sessionCookie: "sess-1",
      csrfToken: "csrf-1",
      headers: {
        Cookie: "JSESSIONID=sess-1",
      },
    });
    expect(ctx.sharedMap.get("user")).toEqual({
      id: "u-1",
      displayName: "Dev Admin",
      email: "dev@acme.local",
      tenant: "acme",
      claims: ["participant"],
    });
    expect(mockBuildMutationRequestContext).not.toHaveBeenCalled();
  });

  it("onRequest sends missing protected sessions to recovery auth flow", async () => {
    mockBuildServerRequestContext.mockReturnValue({
      apiUrl: "http://localhost:8080/api/v1",
      sessionCookie: "",
      csrfToken: "",
      headers: {},
    });

    const mod = await import("~/routes/layout");

    await expect(mod.onRequest(createRequestCtx() as never)).rejects.toEqual({
      type: "redirect",
      status: 302,
      to: "/auth?error=AUTH_REQUIRED&mode=recover&returnTo=%2Fadmin%2Fconfig-sets%3Fenvironment%3Ddev",
    });

    expect(mockResolveAuthRecoveryRedirectPath).toHaveBeenCalledWith(
      undefined,
      "/admin/config-sets?environment=dev",
    );
    expect(mockResolveAuthRedirectPath).not.toHaveBeenCalled();
    expect(mockBuildMutationRequestContext).not.toHaveBeenCalled();
  });

  it("onRequest retries via recovery auth flow only for AUTH_REQUIRED fetch failures", async () => {
    const authError = new MockAuthServiceError({
      title: "Требуется вход",
      reason: "Session expired",
      actions: "Выполните вход через SSO.",
      errorCode: "AUTH_REQUIRED",
    });
    mockFetchAuthMe.mockRejectedValue(authError);

    const mod = await import("~/routes/layout");

    await expect(mod.onRequest(createRequestCtx() as never)).rejects.toEqual({
      type: "redirect",
      status: 302,
      to: "/auth?error=AUTH_REQUIRED&mode=recover&returnTo=%2Fadmin%2Fconfig-sets%3Fenvironment%3Ddev",
    });

    expect(mockResolveAuthRecoveryRedirectPath).toHaveBeenCalledWith(
      authError,
      "/admin/config-sets?environment=dev",
    );
    expect(mockBuildMutationRequestContext).not.toHaveBeenCalled();
  });

  it("onRequest keeps non-recoverable auth failures on the manual auth redirect path", async () => {
    const authError = new MockAuthServiceError({
      title: "Доступ запрещен",
      reason: "No permissions",
      actions: "Обратитесь к администратору.",
      errorCode: "ACCESS_DENIED",
    });
    mockFetchAuthMe.mockRejectedValue(authError);
    mockResolveAuthRedirectPath.mockReturnValue(
      "/auth?error=ACCESS_DENIED&returnTo=%2Fadmin%2Fconfig-sets%3Fenvironment%3Ddev",
    );

    const mod = await import("~/routes/layout");

    await expect(mod.onRequest(createRequestCtx() as never)).rejects.toEqual({
      type: "redirect",
      status: 302,
      to: "/auth?error=ACCESS_DENIED&returnTo=%2Fadmin%2Fconfig-sets%3Fenvironment%3Ddev",
    });

    expect(mockResolveAuthRedirectPath).toHaveBeenCalledWith(
      authError,
      "/admin/config-sets?environment=dev",
    );
    expect(mockResolveAuthRecoveryRedirectPath).not.toHaveBeenCalled();
    expect(mockBuildMutationRequestContext).not.toHaveBeenCalled();
  });

  it("useLogout preserves successful provider redirect", async () => {
    mockLogoutFromAuthSession.mockResolvedValue("https://issuer.example.test/protocol/openid-connect/logout");
    const ctx = createActionCtx();
    ctx.env.get = (name: string) =>
      name === "AUTH_LOGOUT_ALLOWED_ORIGINS" ? "https://issuer.example.test" : undefined;

    const mod = await import("~/routes/layout");

    await expect(mod.useLogout({}, ctx as never)).rejects.toEqual({
      type: "redirect",
      status: 302,
      to: "https://issuer.example.test/protocol/openid-connect/logout",
    });

    expect(mockResolveAuthRedirectPath).not.toHaveBeenCalled();
    expect(mockResolveAuthRecoveryRedirectPath).not.toHaveBeenCalled();
  });

  it("useLogout falls back to centralized auth redirect on auth errors", async () => {
    const authError = new Error("expired");
    mockLogoutFromAuthSession.mockRejectedValue(authError);
    mockResolveAuthRedirectPath.mockReturnValue("/auth?returnTo=%2Fadmin%2Fconfig-sets%3Fenvironment%3Ddev");

    const mod = await import("~/routes/layout");

    await expect(mod.useLogout({}, createActionCtx() as never)).rejects.toEqual({
      type: "redirect",
      status: 302,
      to: "/auth?returnTo=%2Fadmin%2Fconfig-sets%3Fenvironment%3Ddev",
    });

    expect(mockResolveAuthRedirectPath).toHaveBeenCalledWith(
      authError,
      "/admin/config-sets?environment=dev",
    );
    expect(mockResolveAuthRecoveryRedirectPath).not.toHaveBeenCalled();
  });

  it("useLogout rejects unsafe external redirect targets", async () => {
    mockLogoutFromAuthSession.mockResolvedValue("http://evil.example.test/logout");
    mockResolveAuthRedirectPath.mockReturnValue("/auth?returnTo=%2Fadmin%2Fconfig-sets%3Fenvironment%3Ddev");

    const mod = await import("~/routes/layout");

    await expect(mod.useLogout({}, createActionCtx() as never)).rejects.toEqual({
      type: "redirect",
      status: 302,
      to: "/auth?returnTo=%2Fadmin%2Fconfig-sets%3Fenvironment%3Ddev",
    });

    expect(mockResolveAuthRedirectPath).toHaveBeenCalled();
  });

  it("useLogout allows configured external logout origin", async () => {
    mockLogoutFromAuthSession.mockResolvedValue("https://issuer.example.test/protocol/openid-connect/logout");
    const ctx = createActionCtx();
    ctx.env.get = (name: string) =>
      name === "AUTH_LOGOUT_ALLOWED_ORIGINS" ? "https://issuer.example.test" : undefined;

    const mod = await import("~/routes/layout");

    await expect(mod.useLogout({}, ctx as never)).rejects.toEqual({
      type: "redirect",
      status: 302,
      to: "https://issuer.example.test/protocol/openid-connect/logout",
    });
  });
});
