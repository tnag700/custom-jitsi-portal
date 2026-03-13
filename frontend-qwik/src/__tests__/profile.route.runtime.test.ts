/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
/* eslint-disable @typescript-eslint/await-thenable */
import { beforeEach, describe, expect, it, vi } from "vitest";

const mockFetchMyProfile = vi.fn();
const mockUpsertMyProfile = vi.fn();
const mockBuildServerRequestContext = vi.fn();
const mockBuildMutationRequestContext = vi.fn();

class MockProfileServiceError extends Error {
  payload: { title: string; detail: string; errorCode: string; traceId?: string };

  constructor(payload: { title: string; detail: string; errorCode: string; traceId?: string }) {
    super(payload.detail);
    this.name = "ProfileServiceError";
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
    useTask$: noop,
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
    zod$: identity,
  };
});

vi.mock("~/lib/domains/profile", () => ({
  fetchMyProfile: mockFetchMyProfile,
  profileFormSchema: {},
  upsertMyProfile: mockUpsertMyProfile,
  ProfileServiceError: MockProfileServiceError,
  ProfileForm: () => null,
}));

vi.mock("~/lib/shared/routes/server-handlers", () => ({
  buildServerRequestContext: mockBuildServerRequestContext,
  buildMutationRequestContext: mockBuildMutationRequestContext,
}));

interface LoaderCtx {
  sharedMap: Map<string, unknown>;
  cookie: { get: (name: string) => { value?: string } | undefined };
  redirect: (status: number, to: string) => unknown;
}

interface ActionCtx extends LoaderCtx {
  fail: (status: number, payload: unknown) => unknown;
}

function createCtx(overrides?: Partial<ActionCtx>): ActionCtx {
  return {
    sharedMap: new Map<string, unknown>([["apiUrl", "http://localhost:8080/api/v1"]]),
    cookie: {
      get: (name: string) => {
        if (name === "JSESSIONID") return { value: "sess-1" };
        if (name === "XSRF-TOKEN") return { value: "csrf-1" };
        return undefined;
      },
    },
    redirect: (status, to) => ({ type: "redirect", status, to }),
    fail: (status, payload) => ({ failed: true, status, payload }),
    ...overrides,
  };
}

describe("profile route runtime", () => {
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
    mockBuildMutationRequestContext.mockResolvedValue({
      apiUrl: "http://localhost:8080/api/v1",
      sessionCookie: "sess-1",
      csrfToken: "csrf-request-1",
      csrfCookieToken: "csrf-cookie-1",
      idempotencyKey: "idem-1",
      headers: {
        Cookie: "JSESSIONID=sess-1; XSRF-TOKEN=csrf-cookie-1",
        "Content-Type": "application/json",
        "X-XSRF-TOKEN": "csrf-request-1",
        "Idempotency-Key": "idem-1",
      },
    });
  });

  it("useMyProfile marks first run when backend returns null", async () => {
    mockFetchMyProfile.mockResolvedValue(null);

    const mod = await import("~/routes/profile/index");
    const ctx = createCtx();
    const result = await mod.useMyProfile(ctx as never);

    expect(mockFetchMyProfile).toHaveBeenCalledWith({
      apiUrl: "http://localhost:8080/api/v1",
      sessionCookie: "sess-1",
      csrfToken: "csrf-1",
      headers: {
        Cookie: "JSESSIONID=sess-1",
      },
    });
    expect(result).toEqual({ profile: null, isFirstRun: true, loadError: null });
  });

  it("useMyProfile returns existing profile with isFirstRun=false", async () => {
    const profile = {
      subjectId: "sub-1",
      tenantId: "tenant-a",
      fullName: "Jane Doe",
      organization: "Acme",
      position: "Lead",
      createdAt: "2026-03-03T10:00:00Z",
      updatedAt: "2026-03-03T10:10:00Z",
    };
    mockFetchMyProfile.mockResolvedValue(profile);

    const mod = await import("~/routes/profile/index");
    const result = await mod.useMyProfile(createCtx() as never);

    expect(result).toEqual({ profile, isFirstRun: false, loadError: null });
  });

  it("useMyProfile redirects to /auth on AUTH_REQUIRED", async () => {
    mockFetchMyProfile.mockRejectedValue(
      new MockProfileServiceError({
        title: "Unauthorized",
        detail: "Session missing",
        errorCode: "AUTH_REQUIRED",
      }),
    );

    const mod = await import("~/routes/profile/index");
    const ctx = createCtx();

    await expect(mod.useMyProfile(ctx as never)).rejects.toEqual({
      type: "redirect",
      status: 302,
      to: "/auth",
    });
  });

  it("useMyProfile returns service payload for non-auth profile errors", async () => {
    mockFetchMyProfile.mockRejectedValue(
      new MockProfileServiceError({
        title: "Validation",
        detail: "Bad profile",
        errorCode: "PROFILE_VALIDATION_FAILED",
      }),
    );

    const mod = await import("~/routes/profile/index");
    const result = await mod.useMyProfile(createCtx() as never);

    expect(result).toEqual({
      profile: null,
      isFirstRun: false,
      loadError: {
        title: "Validation",
        detail: "Bad profile",
        errorCode: "PROFILE_VALIDATION_FAILED",
      },
    });
  });

  it("useMyProfile returns fallback error for unexpected exceptions", async () => {
    mockFetchMyProfile.mockRejectedValue(new Error("network down"));

    const mod = await import("~/routes/profile/index");
    const result = await mod.useMyProfile(createCtx() as never);

    expect(result).toEqual({
      profile: null,
      isFirstRun: false,
      loadError: {
        title: "Ошибка загрузки",
        detail: "Не удалось загрузить профиль.",
        errorCode: "PROFILE_SERVICE_UNAVAILABLE",
      },
    });
  });

  it("useUpsertProfile passes csrf/session and returns success payload", async () => {
    const profile = {
      subjectId: "sub-1",
      tenantId: "tenant-a",
      fullName: "Jane Doe",
      organization: "Acme",
      position: "Lead",
      createdAt: "2026-03-03T10:00:00Z",
      updatedAt: "2026-03-03T10:10:00Z",
    };
    mockUpsertMyProfile.mockResolvedValue(profile);

    const mod = await import("~/routes/profile/index");
    const ctx = createCtx();
    const result = await mod.useUpsertProfile(
      { fullName: "Jane Doe", organization: "Acme", position: "Lead" },
      ctx as never,
    );

    expect(mockUpsertMyProfile).toHaveBeenCalledWith(
      {
        apiUrl: "http://localhost:8080/api/v1",
        sessionCookie: "sess-1",
        csrfToken: "csrf-request-1",
        csrfCookieToken: "csrf-cookie-1",
        idempotencyKey: "idem-1",
        headers: {
          Cookie: "JSESSIONID=sess-1; XSRF-TOKEN=csrf-cookie-1",
          "Content-Type": "application/json",
          "X-XSRF-TOKEN": "csrf-request-1",
          "Idempotency-Key": "idem-1",
        },
      },
      { fullName: "Jane Doe", organization: "Acme", position: "Lead" },
    );
    expect(result).toEqual({ success: true, profile });
  });

  it("useUpsertProfile maps ProfileServiceError to fail(400)", async () => {
    mockUpsertMyProfile.mockRejectedValue(
      new MockProfileServiceError({
        title: "Bad request",
        detail: "Validation failed",
        errorCode: "PROFILE_VALIDATION_FAILED",
      }),
    );

    const mod = await import("~/routes/profile/index");
    const ctx = createCtx();
    const result = await mod.useUpsertProfile(
      { fullName: "Jane", organization: "Acme", position: "Lead" },
      ctx as never,
    );

    expect(result).toEqual({
      failed: true,
      status: 400,
      payload: {
        error: {
          title: "Bad request",
          detail: "Validation failed",
          errorCode: "PROFILE_VALIDATION_FAILED",
        },
      },
    });
  });

  it("useUpsertProfile redirects on AUTH_REQUIRED", async () => {
    mockUpsertMyProfile.mockRejectedValue(
      new MockProfileServiceError({
        title: "Unauthorized",
        detail: "Session expired",
        errorCode: "AUTH_REQUIRED",
      }),
    );

    const mod = await import("~/routes/profile/index");
    const ctx = createCtx();

    await expect(
      mod.useUpsertProfile(
        { fullName: "Jane", organization: "Acme", position: "Lead" },
        ctx as never,
      ),
    ).rejects.toEqual({ type: "redirect", status: 302, to: "/auth" });
  });

  it("useUpsertProfile maps unexpected errors to fail(500)", async () => {
    mockUpsertMyProfile.mockRejectedValue(new Error("boom"));

    const mod = await import("~/routes/profile/index");
    const result = await mod.useUpsertProfile(
      { fullName: "Jane", organization: "Acme", position: "Lead" },
      createCtx() as never,
    );

    expect(result).toEqual({
      failed: true,
      status: 500,
      payload: {
        error: {
          title: "Ошибка",
          detail: "Неизвестная ошибка",
          errorCode: "PROFILE_UNKNOWN",
        },
      },
    });
  });
});
