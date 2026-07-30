/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
import { beforeEach, describe, expect, it, vi } from "vitest";

const mockBuildAuthLoginHref = vi.fn();
const mockMapAuthErrorCodeToPayload = vi.fn();
const mockResolveAuthRedirectPath = vi.fn();
const mockResolvePostAuthRedirectPath = vi.fn();
const mockShouldAutoResumeAuth = vi.fn();

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
  AuthErrorPanel: () => null,
  buildAuthLoginHref: mockBuildAuthLoginHref,
  mapAuthErrorCodeToPayload: mockMapAuthErrorCodeToPayload,
  resolveAuthRedirectPath: mockResolveAuthRedirectPath,
  resolvePostAuthRedirectPath: mockResolvePostAuthRedirectPath,
  shouldAutoResumeAuth: mockShouldAutoResumeAuth,
}));

function createCtx(
  urlValue: string,
  sharedEntries: Array<[string, unknown]> = [],
) {
  return {
    sharedMap: new Map<string, unknown>(sharedEntries),
    url: new URL(urlValue),
    env: {
      get: (key: string) => {
        if (key === "PUBLIC_API_URL") {
          return "http://localhost:8080/api/v1";
        }
        return undefined;
      },
    },
    redirect: (status: number, to: string) => ({
      type: "redirect",
      status,
      to,
    }),
  };
}

describe("auth page route runtime", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.clearAllMocks();
    mockBuildAuthLoginHref.mockReturnValue(
      "http://localhost:8080/api/v1/auth/login?returnTo=%2Fprofile",
    );
    mockResolveAuthRedirectPath.mockReturnValue("/auth?returnTo=%2Fprofile");
    mockResolvePostAuthRedirectPath.mockReturnValue("/profile");
    mockMapAuthErrorCodeToPayload.mockReturnValue({
      title: "Требуется вход",
      reason: "Сессия не найдена или истекла.",
      actions: "Выполните вход через SSO.",
      errorCode: "AUTH_REQUIRED",
    });
    mockShouldAutoResumeAuth.mockReturnValue(false);
  });

  it("redirects authenticated users to their safe post-auth destination", async () => {
    const mod = await import("~/routes/auth/index");

    await expect(
      mod.useAuthPage(
        createCtx("http://localhost:3000/auth?returnTo=%2Fprofile", [
          ["user", { id: "u-1" }],
        ]) as never,
      ),
    ).rejects.toEqual({
      type: "redirect",
      status: 302,
      to: "/profile",
    });

    expect(mockResolvePostAuthRedirectPath).toHaveBeenCalledWith("/profile");
  });

  it("auto-resumes recoverable auth entry requests into the existing login flow", async () => {
    mockShouldAutoResumeAuth.mockReturnValue(true);
    const mod = await import("~/routes/auth/index");

    await expect(
      mod.useAuthPage(
        createCtx(
          "http://localhost:3000/auth?mode=recover&returnTo=%2Fprofile",
        ) as never,
      ),
    ).rejects.toEqual({
      type: "redirect",
      status: 302,
      to: "http://localhost:8080/api/v1/auth/login?returnTo=%2Fprofile",
    });

    expect(mockShouldAutoResumeAuth).toHaveBeenCalledWith(null, "recover");
  });

  it("keeps direct auth entry manual and returns page state", async () => {
    const mod = await import("~/routes/auth/index");

    await expect(
      mod.useAuthPage(
        createCtx("http://localhost:3000/auth?returnTo=%2Fprofile") as never,
      ),
    ).resolves.toEqual({
      loginHref: "http://localhost:8080/api/v1/auth/login?returnTo=%2Fprofile",
      retryHref: "/auth?returnTo=%2Fprofile",
      error: null,
    });
  });

  it("renders terminal auth errors instead of auto-resuming them", async () => {
    mockMapAuthErrorCodeToPayload.mockReturnValue({
      title: "Доступ запрещен",
      reason: "У текущего пользователя недостаточно прав для входа.",
      actions: "Обратитесь к администратору.",
      errorCode: "ACCESS_DENIED",
    });
    const mod = await import("~/routes/auth/index");

    await expect(
      mod.useAuthPage(
        createCtx(
          "http://localhost:3000/auth?mode=recover&error=ACCESS_DENIED&returnTo=%2Fadmin",
        ) as never,
      ),
    ).resolves.toEqual({
      loginHref: "http://localhost:8080/api/v1/auth/login?returnTo=%2Fprofile",
      retryHref: "/auth?returnTo=%2Fprofile",
      error: {
        title: "Доступ запрещен",
        reason: "У текущего пользователя недостаточно прав для входа.",
        actions: "Обратитесь к администратору.",
        errorCode: "ACCESS_DENIED",
      },
    });

    expect(mockShouldAutoResumeAuth).toHaveBeenCalledWith(
      "ACCESS_DENIED",
      "recover",
    );
  });
});
