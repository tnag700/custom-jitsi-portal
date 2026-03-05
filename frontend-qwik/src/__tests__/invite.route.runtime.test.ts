import { beforeEach, describe, expect, it, vi } from "vitest";

const mockValidateInviteToken = vi.fn();
const mockExchangeInvite = vi.fn();

class MockInviteExchangeError extends Error {
  payload: { title: string; detail: string; errorCode: string; traceId?: string };

  constructor(payload: { title: string; detail: string; errorCode: string; traceId?: string }) {
    super(payload.detail);
    this.name = "InviteExchangeError";
    this.payload = payload;
  }
}

vi.mock("@qwik.dev/core", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@qwik.dev/core")>();
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
  const actual = await importOriginal<typeof import("@qwik.dev/router")>();
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

interface InviteLoaderCtx {
  params: { inviteToken: string };
  sharedMap: Map<string, unknown>;
}

interface InviteActionCtx {
  sharedMap: Map<string, unknown>;
  redirect: (status: number, to: string) => unknown;
  fail: (status: number, payload: unknown) => unknown;
}

function createLoaderCtx(overrides?: Partial<InviteLoaderCtx>): InviteLoaderCtx {
  return {
    params: { inviteToken: "token-1" },
    sharedMap: new Map<string, unknown>(),
    ...overrides,
  };
}

function createActionCtx(overrides?: Partial<InviteActionCtx>): InviteActionCtx {
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
  });

  it("useInviteTokenLoader validates token using default API url", async () => {
    mockValidateInviteToken.mockResolvedValue({ valid: true, meetingId: "m-1" });

    const mod = await import("~/routes/invite/[inviteToken]/index");
    const result = await mod.useInviteTokenLoader(createLoaderCtx() as never);

    expect(mockValidateInviteToken).toHaveBeenCalledWith("http://localhost:8080/api/v1", "token-1");
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
      createLoaderCtx({ sharedMap: new Map([["apiUrl", "http://api.local/v1"]]) }) as never,
    );

    expect(mockValidateInviteToken).toHaveBeenCalledWith("http://api.local/v1", "token-1");
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

    await expect(mod.useInviteTokenLoader(createLoaderCtx() as never)).rejects.toThrow("network down");
  });

  it("useExchangeInviteAction redirects to joinUrl on success", async () => {
    mockExchangeInvite.mockResolvedValue({ joinUrl: "https://meet/join/abc" });

    const mod = await import("~/routes/invite/[inviteToken]/index");
    const ctx = createActionCtx({ sharedMap: new Map([["apiUrl", "http://api.local/v1"]]) });

    await expect(
      mod.useExchangeInviteAction({ inviteToken: "token-1", displayName: "Jane" }, ctx as never),
    ).rejects.toEqual({ type: "redirect", status: 302, to: "https://meet/join/abc" });

    expect(mockExchangeInvite).toHaveBeenCalledWith("http://api.local/v1", "token-1", "Jane");
  });

  it("useExchangeInviteAction uses default API URL when sharedMap has no apiUrl", async () => {
    mockExchangeInvite.mockResolvedValue({ joinUrl: "https://meet/join/default" });

    const mod = await import("~/routes/invite/[inviteToken]/index");

    await expect(
      mod.useExchangeInviteAction({ inviteToken: "token-1", displayName: "Jane" }, createActionCtx() as never),
    ).rejects.toEqual({ type: "redirect", status: 302, to: "https://meet/join/default" });

    expect(mockExchangeInvite).toHaveBeenCalledWith("http://localhost:8080/api/v1", "token-1", "Jane");
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

  it("useExchangeInviteAction rethrows unknown errors", async () => {
    mockExchangeInvite.mockRejectedValue(new Error("backend unavailable"));

    const mod = await import("~/routes/invite/[inviteToken]/index");

    await expect(
      mod.useExchangeInviteAction({ inviteToken: "token-1", displayName: "Jane" }, createActionCtx() as never),
    ).rejects.toThrow("backend unavailable");
  });
});
