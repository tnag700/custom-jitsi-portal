import { afterEach, describe, expect, it, vi } from "vitest";
import {
  AuthServiceError,
  adaptProblemDetails,
  fetchAuthMe,
} from "../lib/domains/auth/auth.service";
import { mapAuthErrorCodeToPayload } from "../lib/domains/auth/auth-error-map";
import {
  AUTH_PUBLIC_PATHS,
  isPublicAuthPath,
  resolveAuthRedirectPath,
} from "../lib/domains/auth/auth-guard";

afterEach(() => {
  vi.restoreAllMocks();
});

describe("Auth service behavior", () => {
  it("adaptProblemDetails maps status fallback and defaults", async () => {
    const response = new Response(JSON.stringify({ detail: "Session expired" }), {
      status: 401,
      headers: {
        "content-type": "application/problem+json",
      },
    });

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
            claims: ["host", "moderator", 123],
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
      claims: ["host", "moderator"],
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

  it("maps AuthServiceError to /auth with encoded error code", () => {
    const error = new AuthServiceError({
      title: "Denied",
      reason: "No permissions",
      actions: "Contact admin",
      errorCode: "ACCESS DENIED",
    });

    expect(resolveAuthRedirectPath(error)).toBe("/auth?error=ACCESS%20DENIED");
  });

  it("maps unknown errors to generic /auth redirect", () => {
    expect(resolveAuthRedirectPath(new Error("boom"))).toBe("/auth");
  });
});
