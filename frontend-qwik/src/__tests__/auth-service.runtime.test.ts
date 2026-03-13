import { afterEach, describe, expect, it, vi } from "vitest";
import {
  adaptProblemDetails,
  AuthServiceError,
  fetchAuthMe,
} from "../lib/domains/auth/auth.service";

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json",
    },
  });
}

describe("auth.service runtime: adaptProblemDetails", () => {
  it("maps RFC7807 fields and preserves explicit errorCode", async () => {
    const response = jsonResponse(
      {
        title: "Unauthorized",
        detail: "Session expired",
        errorCode: "AUTH_REQUIRED",
      },
      401,
    );

    await expect(adaptProblemDetails(response)).resolves.toEqual({
      title: "Unauthorized",
      reason: "Session expired",
      actions: "Выполните вход через SSO.",
      errorCode: "AUTH_REQUIRED",
    });
  });

  it("falls back to ACCESS_DENIED for 403 when errorCode is absent", async () => {
    const response = jsonResponse({}, 403);

    await expect(adaptProblemDetails(response)).resolves.toEqual({
      title: "Ошибка аутентификации",
      reason: "Не удалось получить профиль пользователя.",
      actions: "Обратитесь к администратору.",
      errorCode: "ACCESS_DENIED",
    });
  });

  it("falls back to AUTH_SERVICE_UNAVAILABLE for 5xx", async () => {
    const response = jsonResponse({}, 503);

    await expect(adaptProblemDetails(response)).resolves.toEqual({
      title: "Ошибка аутентификации",
      reason: "Не удалось получить профиль пользователя.",
      actions: "Повторите попытку позже.",
      errorCode: "AUTH_SERVICE_UNAVAILABLE",
    });
  });

  it("handles non-JSON body and uses status fallback", async () => {
    const response = new Response("not-json", {
      status: 401,
      headers: {
        "content-type": "application/problem+json",
      },
    });

    await expect(adaptProblemDetails(response)).resolves.toEqual({
      title: "Ошибка аутентификации",
      reason: "Не удалось получить профиль пользователя.",
      actions: "Выполните вход через SSO.",
      errorCode: "AUTH_REQUIRED",
    });
  });

  it("uses AUTH_UNKNOWN for non-mapped 4xx", async () => {
    const response = jsonResponse({}, 418);

    await expect(adaptProblemDetails(response)).resolves.toEqual({
      title: "Ошибка аутентификации",
      reason: "Не удалось получить профиль пользователя.",
      actions: "Выполните вход через SSO.",
      errorCode: "AUTH_UNKNOWN",
    });
  });
});

describe("auth.service runtime: fetchAuthMe", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("calls /auth/me with JSESSIONID cookie and normalizes profile payload", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(
        jsonResponse(
          {
            id: "u-1",
            displayName: "Dev Admin",
            email: "dev@example.com",
            tenant: "acme",
            claims: ["host", "moderator"],
          },
          200,
        ),
      );

    const profile = await fetchAuthMe("session-123", "http://localhost:8080/api/v1");

    expect(fetchMock).toHaveBeenCalledWith("http://localhost:8080/api/v1/auth/me", {
      method: "GET",
      headers: {
        Cookie: "JSESSIONID=session-123",
      },
    });

    expect(profile).toEqual({
      id: "u-1",
      displayName: "Dev Admin",
      email: "dev@example.com",
      tenant: "acme",
      claims: ["host", "moderator"],
    });
  });

  it("throws AUTH_RESPONSE_INVALID when claims contract drifts from string array", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse(
        {
          id: "u-1",
          displayName: "Dev Admin",
          email: "dev@example.com",
          tenant: "acme",
          claims: { ROLE_admin: "ROLE_admin" },
        },
        200,
      ),
    );

    await expect(
      fetchAuthMe("session-123", "http://localhost:8080/api/v1"),
    ).rejects.toMatchObject({
      name: "AuthServiceError",
      payload: {
        errorCode: "AUTH_RESPONSE_INVALID",
      },
    });
  });

  it("fails fast when backend omits required profile fields", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse({}, 200));

    await expect(
      fetchAuthMe("session-xyz", "http://localhost:8080/api/v1"),
    ).rejects.toMatchObject({
      name: "AuthServiceError",
      payload: {
        errorCode: "AUTH_PROFILE_INVALID",
      },
    });
  });

  it("throws AuthServiceError with mapped payload for problem details response", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse(
        {
          title: "Forbidden",
          detail: "No tenant access",
          errorCode: "ACCESS_DENIED",
        },
        403,
      ),
    );

    await expect(
      fetchAuthMe("session-xyz", "http://localhost:8080/api/v1"),
    ).rejects.toMatchObject({
      name: "AuthServiceError",
      payload: {
        title: "Forbidden",
        reason: "No tenant access",
        actions: "Обратитесь к администратору.",
        errorCode: "ACCESS_DENIED",
      },
    });
  });

  it("throws AuthServiceError with fallback when problem body is invalid", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response("<html>upstream error</html>", {
        status: 503,
        headers: {
          "content-type": "text/html",
        },
      }),
    );

    try {
      await fetchAuthMe("session-xyz", "http://localhost:8080/api/v1");
      throw new Error("Expected fetchAuthMe to throw AuthServiceError");
    } catch (error) {
      expect(error).toBeInstanceOf(AuthServiceError);
      const authError = error as AuthServiceError;
      expect(authError.payload).toEqual({
        title: "Ошибка аутентификации",
        reason: "Не удалось получить профиль пользователя.",
        actions: "Повторите попытку позже.",
        errorCode: "AUTH_SERVICE_UNAVAILABLE",
      });
    }
  });
});
