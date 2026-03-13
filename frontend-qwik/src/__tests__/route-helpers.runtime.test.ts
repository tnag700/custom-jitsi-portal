/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  DEFAULT_SERVER_API_URL,
  buildMutationRequestContext,
  buildServerRequestContext,
  mapRouteActionError,
} from "~/lib/shared/routes/server-handlers";
import { RoomServiceError } from "~/lib/domains/rooms/rooms.service";

afterEach(() => {
  vi.restoreAllMocks();
});

describe("shared route server helpers", () => {
  it("buildServerRequestContext reads apiUrl and cookies without mutation-only fields", () => {
    const result = buildServerRequestContext({
      sharedMap: new Map<string, unknown>([["apiUrl", "http://localhost:8080/api/v1"]]),
      cookie: {
        get(name: string) {
          if (name === "JSESSIONID") {
            return { value: "sess-1" };
          }
          if (name === "XSRF-TOKEN") {
            return { value: "csrf-1" };
          }
          return undefined;
        },
      },
    });

    expect(result).toEqual({
      apiUrl: "http://localhost:8080/api/v1",
      sessionCookie: "sess-1",
      csrfToken: "csrf-1",
      headers: {
        Cookie: "JSESSIONID=sess-1",
      },
    });
  });

  it("buildServerRequestContext falls back to default API url when sharedMap is empty", () => {
    const result = buildServerRequestContext({
      sharedMap: new Map<string, unknown>(),
      cookie: {
        get() {
          return undefined;
        },
      },
    });

    expect(result.apiUrl).toBe(DEFAULT_SERVER_API_URL);
    expect(result.sessionCookie).toBe("");
    expect(result.csrfToken).toBe("");
    expect(result.headers).toEqual({
      Cookie: "JSESSIONID=",
    });
  });

  it("buildServerRequestContext falls back to the raw cookie header when cookie accessor is empty", () => {
    const result = buildServerRequestContext({
      sharedMap: new Map<string, unknown>([
        ["apiUrl", "http://localhost:8080/api/v1"],
        ["requestCookieHeader", "theme=light; JSESSIONID=sess-header-1; XSRF-TOKEN=csrf-header-1"],
      ]),
      cookie: {
        get() {
          return undefined;
        },
      },
    });

    expect(result).toEqual({
      apiUrl: "http://localhost:8080/api/v1",
      sessionCookie: "sess-header-1",
      csrfToken: "csrf-header-1",
      headers: {
        Cookie: "JSESSIONID=sess-header-1",
      },
    });
  });

  it("buildMutationRequestContext adds idempotency key only for mutation flows", async () => {
    vi.spyOn(globalThis.crypto, "randomUUID").mockReturnValue("idem-shared-1");

    const result = await buildMutationRequestContext({
      sharedMap: new Map<string, unknown>([["apiUrl", "http://localhost:8080/api/v1"]]),
      cookie: {
        get() {
          return undefined;
        },
      },
    });

    expect(result).toEqual({
      apiUrl: "http://localhost:8080/api/v1",
      sessionCookie: "",
      csrfToken: "",
      csrfCookieToken: "",
      headers: {
        "Content-Type": "application/json",
        Cookie: "JSESSIONID=; XSRF-TOKEN=",
        "Idempotency-Key": "idem-shared-1",
        "X-XSRF-TOKEN": "",
      },
      idempotencyKey: "idem-shared-1",
    });
  });

  it("buildMutationRequestContext bootstraps csrf request and cookie tokens from backend", async () => {
    vi.spyOn(globalThis.crypto, "randomUUID").mockReturnValue("idem-shared-2");
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue({
      ok: true,
      headers: {
        getSetCookie: () => ["XSRF-TOKEN=csrf-cookie-2; Path=/; SameSite=Lax"],
      },
      json: async () => ({ token: "csrf-request-2", headerName: "X-XSRF-TOKEN" }),
    } as Response);
    const set = vi.fn();

    const result = await buildMutationRequestContext({
      sharedMap: new Map<string, unknown>([["apiUrl", "http://localhost:8080/api/v1"]]),
      cookie: {
        get(name: string) {
          if (name === "JSESSIONID") {
            return { value: "sess-2" };
          }
          return undefined;
        },
        set,
      },
    });

    expect(fetchMock).toHaveBeenCalledWith("http://localhost:8080/api/v1/auth/csrf", {
      method: "GET",
      headers: {
        Cookie: "JSESSIONID=sess-2",
      },
    });
    expect(set).toHaveBeenCalledWith("XSRF-TOKEN", "csrf-cookie-2", {
      path: "/",
      sameSite: "lax",
    });
    expect(result).toEqual({
      apiUrl: "http://localhost:8080/api/v1",
      sessionCookie: "sess-2",
      csrfToken: "csrf-request-2",
      csrfCookieToken: "csrf-cookie-2",
      headers: {
        "Content-Type": "application/json",
        Cookie: "JSESSIONID=sess-2; XSRF-TOKEN=csrf-cookie-2",
        "Idempotency-Key": "idem-shared-2",
        "X-XSRF-TOKEN": "csrf-request-2",
      },
      idempotencyKey: "idem-shared-2",
    });
  });

  it("buildMutationRequestContext falls back to request token when backend did not send csrf cookie", async () => {
    vi.spyOn(globalThis.crypto, "randomUUID").mockReturnValue("idem-shared-3");
    vi.spyOn(globalThis, "fetch").mockResolvedValue({
      ok: true,
      headers: {
        getSetCookie: () => [],
      },
      json: async () => ({ token: "csrf-request-3" }),
    } as Response);
    const set = vi.fn();

    const result = await buildMutationRequestContext({
      sharedMap: new Map<string, unknown>([["apiUrl", "http://localhost:8080/api/v1"]]),
      cookie: {
        get(name: string) {
          if (name === "JSESSIONID") {
            return { value: "sess-3" };
          }
          return undefined;
        },
        set,
      },
    });

    expect(set).toHaveBeenCalledWith("XSRF-TOKEN", "csrf-request-3", {
      path: "/",
      sameSite: "lax",
    });
    expect(result).toEqual({
      apiUrl: "http://localhost:8080/api/v1",
      sessionCookie: "sess-3",
      csrfToken: "csrf-request-3",
      csrfCookieToken: "csrf-request-3",
      headers: {
        "Content-Type": "application/json",
        Cookie: "JSESSIONID=sess-3; XSRF-TOKEN=csrf-request-3",
        "Idempotency-Key": "idem-shared-3",
        "X-XSRF-TOKEN": "csrf-request-3",
      },
      idempotencyKey: "idem-shared-3",
    });
  });

  it("mapRouteActionError preserves real domain error payload and status 400", () => {
    const fail = vi.fn((status: number, payload: unknown) => ({ failed: true, status, payload }));

    const result = mapRouteActionError(
      new RoomServiceError({
        title: "Validation error",
        detail: "room invalid",
        errorCode: "VALIDATION_ERROR",
        traceId: "trace-1",
      }),
      RoomServiceError,
      fail,
      "ROOM_UNKNOWN",
    );

    expect(fail).toHaveBeenCalledWith(400, {
      error: {
        title: "Validation error",
        detail: "room invalid",
        errorCode: "VALIDATION_ERROR",
        traceId: "trace-1",
      },
    });
    expect(result).toEqual({
      failed: true,
      status: 400,
      payload: {
        error: {
          title: "Validation error",
          detail: "room invalid",
          errorCode: "VALIDATION_ERROR",
          traceId: "trace-1",
        },
      },
    });
  });

  it("mapRouteActionError maps unknown errors to fail(500) fallback payload", () => {
    const fail = vi.fn((status: number, payload: unknown) => ({ failed: true, status, payload }));

    const result = mapRouteActionError(new Error("boom"), RoomServiceError, fail, "MEETING_UNKNOWN");

    expect(fail).toHaveBeenCalledWith(500, {
      error: {
        title: "Ошибка",
        detail: "Неизвестная ошибка",
        errorCode: "MEETING_UNKNOWN",
      },
    });
    expect(result).toEqual({
      failed: true,
      status: 500,
      payload: {
        error: {
          title: "Ошибка",
          detail: "Неизвестная ошибка",
          errorCode: "MEETING_UNKNOWN",
        },
      },
    });
  });
});