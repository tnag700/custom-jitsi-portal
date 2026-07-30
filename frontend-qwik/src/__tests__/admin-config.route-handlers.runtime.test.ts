/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
import { beforeEach, describe, expect, it, vi } from "vitest";

const mockFetchAdminConfigSets = vi.fn();
const mockFetchAdminConfigSet = vi.fn();
const mockLoadAdminConfigLatestRollouts = vi.fn();
const mockCreateAdminConfigSet = vi.fn();
const mockUpdateAdminConfigSet = vi.fn();
const mockCheckAdminConfigSetCompatibility = vi.fn();
const mockRolloutAdminConfigSet = vi.fn();
const mockRollbackAdminConfigSet = vi.fn();
const mockBuildServerRequestContext = vi.fn();
const mockBuildMutationRequestContext = vi.fn();
const mockResolveAuthRecoveryRedirectPath = vi.fn();

class MockAdminConfigServiceError extends Error {
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
    this.name = "AdminConfigServiceError";
    this.payload = payload;
  }
}

vi.mock("@qwik.dev/router", async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    routeLoader$: <T>(value: T): T => value,
    routeLoaderQrl: <T>(value: T): T => value,
    routeAction$: <T>(value: T): T => value,
    routeActionQrl: <T>(value: T): T => value,
    zod$: <T>(value: T): T => value,
  };
});

vi.mock("~/lib/domains/auth", () => ({
  resolveAuthRecoveryRedirectPath: mockResolveAuthRecoveryRedirectPath,
}));

vi.mock("~/lib/shared/routes/server-handlers", () => ({
  buildServerRequestContext: mockBuildServerRequestContext,
  buildMutationRequestContext: mockBuildMutationRequestContext,
}));

vi.mock("~/lib/domains/admin", async () => {
  const routeHelpers = await import(
    "~/lib/domains/admin/admin-config.route-helpers"
  );
  const configTypes = await import("~/lib/domains/admin/admin-config.types");
  return {
    ...routeHelpers,
    adminConfigSetFormSchema: configTypes.adminConfigSetFormSchema,
    AdminConfigServiceError: MockAdminConfigServiceError,
    fetchAdminConfigSets: mockFetchAdminConfigSets,
    fetchAdminConfigSet: mockFetchAdminConfigSet,
    loadAdminConfigLatestRollouts: mockLoadAdminConfigLatestRollouts,
    createAdminConfigSet: mockCreateAdminConfigSet,
    updateAdminConfigSet: mockUpdateAdminConfigSet,
    checkAdminConfigSetCompatibility: mockCheckAdminConfigSetCompatibility,
    rolloutAdminConfigSet: mockRolloutAdminConfigSet,
    rollbackAdminConfigSet: mockRollbackAdminConfigSet,
  };
});

const adminUser = {
  id: "admin-1",
  displayName: "Alice Admin",
  email: "alice@example.test",
  tenant: "tenant-1",
  claims: ["ROLE_ADMIN"],
};

function createSummary() {
  return {
    configSetId: "cfg-1",
    name: "Primary DEV",
    tenantId: "tenant-1",
    environmentType: "DEV",
    issuer: "issuer",
    audience: "aud",
    algorithm: "HS256",
    roleClaim: "role",
    signingSecret: "***",
    jwksUri: null,
    accessTtlMinutes: 15,
    refreshTtlMinutes: 60,
    meetingsServiceUrl: "https://meet.dev",
    status: "active",
    createdAt: "2026-07-29T10:00:00Z",
    updatedAt: "2026-07-29T11:00:00Z",
    latestRollout: null,
    compatibilityStatus: null,
    compatibilityTraceId: null,
  };
}

function createLoaderCtx(
  urlValue = "http://localhost:3000/admin/config-sets?environment=dev&configSetId=cfg-1",
  user = adminUser,
) {
  const redirect = vi.fn((status: number, to: string) => ({
    type: "redirect",
    status,
    to,
  }));
  return {
    sharedMap: new Map<string, unknown>([
      ["user", user],
      ["apiUrl", "http://localhost:8080/api/v1"],
    ]),
    cookie: {},
    query: new URL(urlValue).searchParams,
    url: new URL(urlValue),
    redirect,
  };
}

function createActionCtx(
  urlValue = "http://localhost:3000/admin/config-sets?configSetId=cfg-1",
) {
  const redirect = vi.fn((status: number, to: string) => ({
    type: "redirect",
    status,
    to,
  }));
  const fail = vi.fn((status: number, value: unknown) => ({
    failed: true,
    status,
    ...value,
  }));
  return {
    sharedMap: new Map<string, unknown>([["user", adminUser]]),
    cookie: {},
    url: new URL(urlValue),
    redirect,
    fail,
  };
}

describe("admin config route handlers runtime", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.clearAllMocks();
    mockBuildServerRequestContext.mockReturnValue({
      apiUrl: "http://localhost:8080/api/v1",
      sessionCookie: "sess-1",
      headers: {
        Cookie: "JSESSIONID=sess-1",
      },
    });
    mockBuildMutationRequestContext.mockResolvedValue({
      apiUrl: "http://localhost:8080/api/v1",
      sessionCookie: "sess-1",
      csrfToken: "csrf-1",
      headers: {
        Cookie: "JSESSIONID=sess-1",
        "X-CSRF-TOKEN": "csrf-1",
      },
    });
    mockResolveAuthRecoveryRedirectPath.mockReturnValue(
      "/auth?error=AUTH_REQUIRED&mode=recover",
    );
  });

  it("loads summaries, rollout metadata and selected detail for an admin claim", async () => {
    const summary = createSummary();
    const detail = {
      ...summary,
      compatibility: null,
      latestRollout: null,
    };
    mockFetchAdminConfigSets.mockResolvedValue({
      items: [summary],
      page: 0,
      pageSize: 20,
      totalElements: 1,
      totalPages: 1,
    });
    mockLoadAdminConfigLatestRollouts.mockResolvedValue(
      new Map([
        [
          "DEV",
          {
            rolloutId: "rollout-1",
            status: "SUCCEEDED",
            actorId: "Alice Admin",
          },
        ],
      ]),
    );
    mockFetchAdminConfigSet.mockResolvedValue(detail);

    const mod = await import("~/routes/admin/config-sets/route-handlers");
    // eslint-disable-next-line @typescript-eslint/await-thenable
    const result = await mod.useAdminConfigSets(createLoaderCtx() as never);

    expect(mockFetchAdminConfigSets).toHaveBeenCalledWith(
      expect.objectContaining({ apiUrl: "http://localhost:8080/api/v1" }),
      {
        tenantId: "tenant-1",
        page: 0,
        size: 20,
      },
    );
    expect(mockFetchAdminConfigSet).toHaveBeenCalledWith(expect.any(Object), {
      configSetId: "cfg-1",
      tenantId: "tenant-1",
    });
    expect(result.capability).toEqual({
      role: "admin",
      canMutate: true,
      reason: null,
    });
    expect(result.items[0].latestRollout?.status).toBe("SUCCEEDED");
    expect(result.selectedConfig).toEqual(detail);
  });

  it("redirects missing sessions and preserves typed loader failures", async () => {
    const mod = await import("~/routes/admin/config-sets/route-handlers");
    const missingUserCtx = createLoaderCtx(
      "http://localhost:3000/admin/config-sets",
      null,
    );

    await expect(
      mod.useAdminConfigSets(missingUserCtx as never),
    ).rejects.toEqual({
      type: "redirect",
      status: 302,
      to: "/auth?error=AUTH_REQUIRED&mode=recover",
    });

    mockFetchAdminConfigSets.mockRejectedValue(
      new MockAdminConfigServiceError({
        title: "Invalid response",
        detail: "Config contract mismatch",
        errorCode: "ADMIN_CONFIG_RESPONSE_INVALID",
        traceId: "trace-config-error",
      }),
    );
    // eslint-disable-next-line @typescript-eslint/await-thenable
    const result = await mod.useAdminConfigSets(
      createLoaderCtx(
        "http://localhost:3000/admin/config-sets?environment=prod",
      ) as never,
    );

    expect(result.loadError).toEqual({
      title: "Invalid response",
      detail: "Config contract mismatch",
      errorCode: "ADMIN_CONFIG_RESPONSE_INVALID",
      traceId: "trace-config-error",
    });
    expect(result.filters.environment).toBe("PROD");
  });

  it("normalizes optional save fields and attaches the authenticated tenant", async () => {
    const saved = {
      ...createSummary(),
      status: "draft",
    };
    mockCreateAdminConfigSet.mockResolvedValue(saved);
    const mod = await import("~/routes/admin/config-sets/route-handlers");
    const ctx = createActionCtx();
    const data = {
      mode: "create",
      name: "Primary DEV",
      environmentType: "DEV",
      issuer: "issuer",
      audience: "aud",
      algorithm: "HS256",
      roleClaim: "  role  ",
      signingSecret: "   ",
      jwksUri: "   ",
      accessTtlMinutes: 15,
      refreshTtlMinutes: 60,
      meetingsServiceUrl: "https://meet.dev",
    };

    // eslint-disable-next-line @typescript-eslint/await-thenable
    const result = await mod.useSaveConfigSet(data as never, ctx as never);

    expect(mockCreateAdminConfigSet).toHaveBeenCalledWith(
      expect.objectContaining({ csrfToken: "csrf-1" }),
      expect.objectContaining({
        tenantId: "tenant-1",
        roleClaim: "role",
        signingSecret: undefined,
        jwksUri: undefined,
      }),
    );
    expect(result.operation).toEqual(
      expect.objectContaining({
        kind: "save",
        message: "Конфиг-набор создан.",
        actorId: "Alice Admin",
      }),
    );
  });

  it("maps compatibility, rollout and rollback results to audit-friendly operations", async () => {
    mockCheckAdminConfigSetCompatibility.mockResolvedValue({
      status: "COMPATIBLE",
      traceId: "trace-compat",
      mismatches: [],
    });
    mockRolloutAdminConfigSet.mockResolvedValue({
      status: "SUCCEEDED",
      actorId: "Alice Admin",
    });
    mockRollbackAdminConfigSet.mockResolvedValue({
      status: "ROLLED_BACK",
      actorId: "Alice Admin",
    });
    const mod = await import("~/routes/admin/config-sets/route-handlers");

    // eslint-disable-next-line @typescript-eslint/await-thenable
    const compatibility = await mod.useCompatibilityCheck(
      { configSetId: "cfg-1" } as never,
      createActionCtx() as never,
    );
    // eslint-disable-next-line @typescript-eslint/await-thenable
    const rollout = await mod.useRolloutConfigSet(
      { configSetId: "cfg-1" } as never,
      createActionCtx() as never,
    );
    // eslint-disable-next-line @typescript-eslint/await-thenable
    const rollback = await mod.useRollbackConfigSet(
      { configSetId: "cfg-1", environmentType: "DEV" } as never,
      createActionCtx() as never,
    );

    expect(compatibility.operation).toEqual(
      expect.objectContaining({
        kind: "compatibility",
        status: "COMPATIBLE",
        traceId: "trace-compat",
      }),
    );
    expect(rollout.operation).toEqual(
      expect.objectContaining({ kind: "rollout", status: "SUCCEEDED" }),
    );
    expect(rollback.operation).toEqual(
      expect.objectContaining({ kind: "rollback", status: "ROLLED_BACK" }),
    );
  });
});
