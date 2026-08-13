import { afterEach, describe, expect, it, vi } from "vitest";
import {
  AuthServiceError,
  adaptProblemDetails,
  fetchAuthMe,
} from "../lib/domains/auth/auth.service";
import { mapAuthErrorCodeToPayload } from "../lib/domains/auth/auth-error-map";
import {
  AUTH_PUBLIC_PATHS,
  buildAuthLoginHref,
  isPublicAuthPath,
  resolveAuthRecoveryRedirectPath,
  resolveAuthRedirectPath,
  resolvePostAuthRedirectPath,
  shouldAutoResumeAuth,
} from "../lib/domains/auth/auth-guard";

afterEach(() => {
  vi.restoreAllMocks();
});

describe("Auth service behavior", () => {
  it("adaptProblemDetails maps status fallback and defaults", async () => {
    const response = new Response(
      JSON.stringify({ detail: "Session expired" }),
      {
        status: 401,
        headers: {
          "content-type": "application/problem+json",
        },
      },
    );

    const payload = await adaptProblemDetails(response);

    expect(payload.errorCode).toBe("AUTH_REQUIRED");
    expect(payload.title).toBe("Ошибка аутентификации");
    expect(payload.reason).toBe("Session expired");
    expect(payload.actions).toBe("Выполните вход через SSO.");
  });

  it("fetchAuthMe returns normalized profile on success", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        return new Response(
          JSON.stringify({
            id: "u-1",
            displayName: "Dev Admin",
            email: "dev@acme.local",
            tenant: "acme",
            claims: ["host", "moderator", "participant"],
          }),
          { status: 200 },
        );
      }),
    );

    const profile = await fetchAuthMe("abc", "http://localhost:8080/api/v1");

    expect(profile).toEqual({
      id: "u-1",
      displayName: "Dev Admin",
      email: "dev@acme.local",
      tenant: "acme",
      claims: ["host", "moderator", "participant"],
    });
  });

  it("fetchAuthMe throws AuthServiceError with problem details on non-2xx", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        return new Response(
          JSON.stringify({
            title: "Access denied",
            detail: "No permissions",
            errorCode: "ACCESS_DENIED",
          }),
          {
            status: 403,
            headers: { "content-type": "application/problem+json" },
          },
        );
      }),
    );

    await expect(
      fetchAuthMe("abc", "http://localhost:8080/api/v1"),
    ).rejects.toMatchObject({
      payload: {
        errorCode: "ACCESS_DENIED",
        reason: "No permissions",
      },
    });
  });

  it("fetchAuthMe fails fast when required profile fields are missing", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        return new Response(
          JSON.stringify({
            id: "",
            displayName: "",
            email: "",
            tenant: "acme",
            claims: [],
          }),
          { status: 200 },
        );
      }),
    );

    await expect(
      fetchAuthMe("abc", "http://localhost:8080/api/v1"),
    ).rejects.toMatchObject({
      payload: {
        errorCode: "AUTH_PROFILE_INVALID",
      },
    });
  });
});

describe("Auth page error mapping", () => {
  it("preserves unknown backend error code for diagnostics", () => {
    const payload = mapAuthErrorCodeToPayload("BACKEND_TIMEOUT");

    expect(payload.errorCode).toBe("BACKEND_TIMEOUT");
    expect(payload.title).toBe("Ошибка входа");
  });

  it("keeps AUTH_REQUIRED semantics for missing code", () => {
    const payload = mapAuthErrorCodeToPayload("");

    expect(payload.errorCode).toBe("AUTH_REQUIRED");
    expect(payload.title).toBe("Требуется вход");
  });
});

describe("Auth guard routing behavior", () => {
  it("keeps expected public paths list", () => {
    expect(AUTH_PUBLIC_PATHS).toEqual(["/auth", "/auth/continue", "/invite"]);
  });

  it("matches public paths by exact segment, not prefix collisions", () => {
    expect(isPublicAuthPath("/auth")).toBe(true);
    expect(isPublicAuthPath("/auth/continue")).toBe(true);
    expect(isPublicAuthPath("/invite/abc")).toBe(true);
    expect(isPublicAuthPath("/authx")).toBe(false);
    expect(isPublicAuthPath("/rooms")).toBe(false);
  });

  it.each([
    "/INVITE/secret-token",
    "/%69nvite/secret-token",
    "/%2569nvite/secret-token",
    "//invite/secret-token",
    "///%69nvite/secret-token",
    "/%2Finvite/secret-token",
  ])(
    "recognizes router-normalized bearer invite path as public: %s",
    (path) => {
      expect(isPublicAuthPath(path)).toBe(true);
    },
  );

  it("maps AuthServiceError to /auth with encoded error code", () => {
    const error = new AuthServiceError({
      title: "Denied",
      reason: "No permissions",
      actions: "Contact admin",
      errorCode: "ACCESS DENIED",
    });

    expect(
      resolveAuthRedirectPath(error, "/admin/config-sets?environment=dev"),
    ).toBe(
      "/auth?error=ACCESS+DENIED&returnTo=%2Fadmin%2Fconfig-sets%3Fenvironment%3Ddev",
    );
  });

  it("maps unknown errors to generic /auth redirect while preserving returnTo", () => {
    expect(
      resolveAuthRedirectPath(
        new Error("boom"),
        "/admin/incidents?environment=prod",
      ),
    ).toBe("/auth?returnTo=%2Fadmin%2Fincidents%3Fenvironment%3Dprod");
  });

  it("builds recovery auth redirect with explicit mode and safe returnTo", () => {
    const error = new AuthServiceError({
      title: "Expired",
      reason: "Session expired",
      actions: "Login again",
      errorCode: "AUTH_REQUIRED",
    });

    expect(
      resolveAuthRecoveryRedirectPath(error, "/profile?tab=settings"),
    ).toBe(
      "/auth?error=AUTH_REQUIRED&mode=recover&returnTo=%2Fprofile%3Ftab%3Dsettings",
    );
  });

  it("drops unsafe returnTo values from auth redirects", () => {
    expect(
      resolveAuthRedirectPath(new Error("boom"), "https://evil.example/phish"),
    ).toBe("/auth");
  });

  it.each([
    ["network-path authority", "//evil.example/phish"],
    ["triple-slash authority", "///evil.example/phish"],
    ["backslash authority", "/\\evil.example/phish"],
    ["repeated-backslash authority", "/\\\\evil.example/phish"],
    ["encoded backslash", "/%5Cevil.example/phish"],
    ["lowercase encoded backslash", "/%5cevil.example/phish"],
    ["double-encoded backslash", "/%255Cevil.example/phish"],
    ["composed encoded backslash", "/%25%35%43evil.example/phish"],
    ["encoded network-path authority", "/%2F%2Fevil.example/phish"],
    ["encoded CRLF", "/profile%0D%0ALocation:%20https://evil.example"],
    ["double-encoded line feed", "/profile%250Aignored"],
    ["literal CRLF", "/profile\r\nLocation: https://evil.example"],
    ["literal null", `/profile${String.fromCharCode(0)}`],
    ["literal delete", `/profile${String.fromCharCode(127)}`],
    ["malformed percent escape", "/profile%ZZ"],
  ])("rejects %s returnTo consistently", (_caseName, returnTo) => {
    expect(resolvePostAuthRedirectPath(returnTo)).toBe("/");
    expect(resolveAuthRedirectPath(new Error("boom"), returnTo)).toBe("/auth");
    expect(buildAuthLoginHref("https://auth.example/api/v1", returnTo)).toBe(
      "https://auth.example/api/v1/auth/login",
    );
  });

  it.each([
    "/",
    "/profile",
    "/admin/config-sets?environment=dev#status",
    "/meetings/?roomId=804f097a-60c7-487a-9078-40da53df2d87",
    "/receipt?discount=100%25",
    "/search?q=%D0%98%D0%B2%D0%B0%D0%BD%20%D0%98%D0%B2%D0%B0%D0%BD%D0%BE%D0%B2",
  ])("preserves valid local returnTo %s", (returnTo) => {
    expect(resolvePostAuthRedirectPath(returnTo)).toBe(returnTo);
  });

  it("drops unsafe returnTo values from recovery redirects", () => {
    expect(
      resolveAuthRecoveryRedirectPath(
        new Error("boom"),
        "https://evil.example/phish",
      ),
    ).toBe("/auth?mode=recover");
  });

  it("builds auth login href with encoded returnTo", () => {
    expect(
      buildAuthLoginHref(
        "http://localhost:8080/api/v1",
        "/admin/config-sets?environment=dev",
      ),
    ).toBe(
      "http://localhost:8080/api/v1/auth/login?returnTo=%2Fadmin%2Fconfig-sets%3Fenvironment%3Ddev",
    );
  });

  it("resolves post-auth redirect to safe returnTo only", () => {
    expect(
      resolvePostAuthRedirectPath("/admin/config-sets?environment=dev"),
    ).toBe("/admin/config-sets?environment=dev");
    expect(resolvePostAuthRedirectPath("https://evil.example/phish")).toBe("/");
  });

  it("auto-resumes only for recoverable auth states", () => {
    expect(shouldAutoResumeAuth(undefined, "recover")).toBe(true);
    expect(shouldAutoResumeAuth("AUTH_REQUIRED", "recover")).toBe(true);
    expect(shouldAutoResumeAuth("ACCESS_DENIED", "recover")).toBe(false);
    expect(shouldAutoResumeAuth("AUTH_REQUIRED", null)).toBe(false);
  });
});
