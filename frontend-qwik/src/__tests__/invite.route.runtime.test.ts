/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
/* eslint-disable @typescript-eslint/await-thenable */
import { beforeEach, describe, expect, it, vi } from "vitest";

const mockValidateInviteToken = vi.fn();
const mockExchangeInvite = vi.fn();
const mockFetchJoinReadiness = vi.fn();

class MockInviteExchangeError extends Error {
  payload: {
    title: string;
    detail: string;
    errorCode: string;
    traceId?: string;
  };

  constructor(payload: {
    title: string;
    detail: string;
    errorCode: string;
    traceId?: string;
  }) {
    super(payload.detail);
    this.name = "InviteExchangeError";
    this.payload = payload;
  }
}

vi.mock("@qwik.dev/core", async (importOriginal) => {
  const actual = await importOriginal();
  const identity = <T>(value: T): T => value;
  return {
    ...actual,
    $: identity,
    component$: identity,
    componentQrl: identity,
    inlinedQrl: identity,
    inlinedQrlDEV: identity,
  };
});

vi.mock("@qwik.dev/router", async (importOriginal) => {
  const actual = await importOriginal();
  const identity = <T>(value: T): T => value;
  const stringSchema = () => ({ min: () => ({}) });
  return {
    ...actual,
    routeLoader$: identity,
    routeLoaderQrl: identity,
    routeAction$: identity,
    routeActionQrl: identity,
    zod$: identity,
    Form: () => null,
    useLocation: () => ({ url: new URL("http://localhost/invite/token-1") }),
    z: {
      object: (shape: unknown) => ({ ...shape, extend: () => ({}) }),
      string: stringSchema,
    },
  };
});

vi.mock("~/lib/shared", () => ({
  ApiErrorAlert: () => null,
}));

vi.mock("~/lib/domains/invites", () => ({
  InviteExchangeError: MockInviteExchangeError,
  exchangeInvite: mockExchangeInvite,
  exchangeInviteSchema: { extend: () => ({}) },
  validateInviteToken: mockValidateInviteToken,
}));

vi.mock("~/lib/domains/join", async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    fetchJoinReadiness: mockFetchJoinReadiness,
  };
});

interface InviteLoaderCtx {
  params: { inviteToken: string };
  sharedMap: Map<string, unknown>;
}

interface InviteActionCtx {
  sharedMap: Map<string, unknown>;
  redirect: (status: number, to: string) => unknown;
  fail: (status: number, payload: unknown) => unknown;
}

function createLoaderCtx(
  overrides?: Partial<InviteLoaderCtx>,
): InviteLoaderCtx {
  return {
    params: { inviteToken: "token-1" },
    sharedMap: new Map<string, unknown>(),
    ...overrides,
  };
}

function createActionCtx(
  overrides?: Partial<InviteActionCtx>,
): InviteActionCtx {
  return {
    sharedMap: new Map<string, unknown>(),
    redirect: (status, to) => ({ type: "redirect", status, to }),
    fail: (status, payload) => ({ failed: true, status, payload }),
    ...overrides,
  };
}

describe("invite route runtime", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.clearAllMocks();
    mockFetchJoinReadiness.mockResolvedValue({
      publicJoinUrl: "https://meet.example.test/",
    });
  });

  it("marks invite responses as private, non-cacheable, and non-indexable", async () => {
    const mod = await import("~/routes/invite/[inviteToken]/index");
    const headers = new Headers();

    await mod.onRequest({ headers } as never);

    expect(headers.get("Cache-Control")).toBe("private, no-store, max-age=0");
    expect(headers.get("X-Robots-Tag")).toBe("noindex, nofollow, noarchive");
    expect(headers.get("Referrer-Policy")).toBe("no-referrer");
  });

  it("useInviteTokenLoader validates token using default API url", async () => {
    mockValidateInviteToken.mockResolvedValue({
      valid: true,
      meetingId: "m-1",
    });

    const mod = await import("~/routes/invite/[inviteToken]/index");
    const result = await mod.useInviteTokenLoader(createLoaderCtx() as never);

    expect(mockValidateInviteToken).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1",
      "token-1",
    );
    expect(result).toEqual({
      inviteToken: "token-1",
      isValid: true,
      validationError: undefined,
    });
  });

  it("useInviteTokenLoader returns validationError on InviteExchangeError", async () => {
    mockValidateInviteToken.mockRejectedValue(
      new MockInviteExchangeError({
        title: "Invite expired",
        detail: "token expired",
        errorCode: "INVITE_EXPIRED",
      }),
    );

    const mod = await import("~/routes/invite/[inviteToken]/index");
    const result = await mod.useInviteTokenLoader(
      createLoaderCtx({
        sharedMap: new Map([["apiUrl", "http://api.local/v1"]]),
      }) as never,
    );

    expect(mockValidateInviteToken).toHaveBeenCalledWith(
      "http://api.local/v1",
      "token-1",
    );
    expect(result).toEqual({
      inviteToken: "token-1",
      isValid: false,
      validationError: {
        title: "Invite expired",
        detail: "token expired",
        errorCode: "INVITE_EXPIRED",
      },
    });
  });

  it("useInviteTokenLoader rethrows unknown errors", async () => {
    mockValidateInviteToken.mockRejectedValue(new Error("network down"));

    const mod = await import("~/routes/invite/[inviteToken]/index");

    await expect(
      mod.useInviteTokenLoader(createLoaderCtx() as never),
    ).rejects.toThrow("network down");
  });

  it("useExchangeInviteAction redirects to joinUrl on success", async () => {
    mockExchangeInvite.mockResolvedValue({
      joinUrl: "https://meet.example.test/join/abc",
    });

    const mod = await import("~/routes/invite/[inviteToken]/index");
    const ctx = createActionCtx({
      sharedMap: new Map([["apiUrl", "http://api.local/v1"]]),
    });

    await expect(
      mod.useExchangeInviteAction(
        { inviteToken: "token-1", displayName: "Jane" },
        ctx as never,
      ),
    ).rejects.toEqual({
      type: "redirect",
      status: 302,
      to: "https://meet.example.test/join/abc",
    });

    expect(mockFetchJoinReadiness).toHaveBeenCalledWith("http://api.local/v1");

    expect(mockExchangeInvite).toHaveBeenCalledWith(
      "http://api.local/v1",
      "token-1",
      "Jane",
    );
  });

  it("useExchangeInviteAction uses default API URL when sharedMap has no apiUrl", async () => {
    mockExchangeInvite.mockResolvedValue({
      joinUrl: "https://meet.example.test/join/default",
    });

    const mod = await import("~/routes/invite/[inviteToken]/index");

    await expect(
      mod.useExchangeInviteAction(
        { inviteToken: "token-1", displayName: "Jane" },
        createActionCtx() as never,
      ),
    ).rejects.toEqual({
      type: "redirect",
      status: 302,
      to: "https://meet.example.test/join/default",
    });

    expect(mockExchangeInvite).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1",
      "token-1",
      "Jane",
    );
  });

  it("fails closed before consuming the invite when canonical meet origin is missing", async () => {
    mockFetchJoinReadiness.mockResolvedValue({ publicJoinUrl: null });

    const mod = await import("~/routes/invite/[inviteToken]/index");
    const result = await mod.useExchangeInviteAction(
      { inviteToken: "token-1", displayName: "Jane" },
      createActionCtx() as never,
    );

    expect(result).toEqual({
      failed: true,
      status: 502,
      payload: {
        error: expect.objectContaining({
          errorCode: "JOIN_RESPONSE_INVALID",
        }),
      },
    });
    expect(mockExchangeInvite).not.toHaveBeenCalled();
  });

  it("returns a controlled 502 without consuming the invite when readiness is unavailable", async () => {
    mockFetchJoinReadiness.mockRejectedValue(new Error("readiness timeout"));

    const mod = await import("~/routes/invite/[inviteToken]/index");
    const result = await mod.useExchangeInviteAction(
      { inviteToken: "token-1", displayName: "Jane" },
      createActionCtx() as never,
    );

    expect(result).toEqual({
      failed: true,
      status: 502,
      payload: {
        error: {
          title: "Не удалось проверить адрес конференции",
          detail: "Сервис готовности Jitsi временно недоступен.",
          errorCode: "JOIN_READINESS_UNAVAILABLE",
        },
      },
    });
    expect(mockExchangeInvite).not.toHaveBeenCalled();
  });

  it.each([
    "https://attacker.example/room",
    "https://user:secret@meet.example.test/room",
    "http://meet.example.test/room",
    "javascript:alert(1)",
  ])("rejects unsafe guest join redirect %s", async (joinUrl) => {
    mockExchangeInvite.mockResolvedValue({ joinUrl });

    const mod = await import("~/routes/invite/[inviteToken]/index");
    const result = await mod.useExchangeInviteAction(
      { inviteToken: "token-1", displayName: "Jane" },
      createActionCtx() as never,
    );

    expect(result).toEqual({
      failed: true,
      status: 502,
      payload: {
        error: expect.objectContaining({
          errorCode: "JOIN_RESPONSE_INVALID",
        }),
      },
    });
  });

  it("useExchangeInviteAction maps InviteExchangeError to fail(400)", async () => {
    mockExchangeInvite.mockRejectedValue(
      new MockInviteExchangeError({
        title: "Invite revoked",
        detail: "revoked by owner",
        errorCode: "INVITE_REVOKED",
      }),
    );

    const mod = await import("~/routes/invite/[inviteToken]/index");
    const result = await mod.useExchangeInviteAction(
      { inviteToken: "token-1", displayName: "Jane" },
      createActionCtx() as never,
    );

    expect(result).toEqual({
      failed: true,
      status: 400,
      payload: {
        error: {
          title: "Invite revoked",
          detail: "revoked by owner",
          errorCode: "INVITE_REVOKED",
        },
      },
    });
  });

  it("maps an invalid successful backend contract to fail(502)", async () => {
    mockExchangeInvite.mockRejectedValue(
      new MockInviteExchangeError({
        title: "Invalid exchange response",
        detail: "missing join response fields",
        errorCode: "INVITE_RESPONSE_INVALID",
      }),
    );

    const mod = await import("~/routes/invite/[inviteToken]/index");
    const result = await mod.useExchangeInviteAction(
      { inviteToken: "token-1", displayName: "Jane" },
      createActionCtx() as never,
    );

    expect(result).toEqual({
      failed: true,
      status: 502,
      payload: {
        error: {
          title: "Invalid exchange response",
          detail: "missing join response fields",
          errorCode: "INVITE_RESPONSE_INVALID",
        },
      },
    });
  });

  it("useExchangeInviteAction rethrows unknown errors", async () => {
    mockExchangeInvite.mockRejectedValue(new Error("backend unavailable"));

    const mod = await import("~/routes/invite/[inviteToken]/index");

    await expect(
      mod.useExchangeInviteAction(
        { inviteToken: "token-1", displayName: "Jane" },
        createActionCtx() as never,
      ),
    ).rejects.toThrow("backend unavailable");
  });
});
