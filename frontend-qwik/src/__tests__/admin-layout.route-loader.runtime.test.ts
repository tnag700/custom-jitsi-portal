/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
import { beforeEach, describe, expect, it, vi } from "vitest";

const mockResolveAuthRecoveryRedirectPath = vi.fn();
const mockFetchAdminFrameworkVersions = vi.fn();

vi.mock("@qwik.dev/core", async (importOriginal) => {
  const actual = await importOriginal();
  const identity = <T>(value: T): T => value;
  return {
    ...actual,
    component$: identity,
    Slot: () => null,
  };
});

vi.mock("@qwik.dev/router", async (importOriginal) => {
  const actual = await importOriginal();
  const identity = <T>(value: T): T => value;
  return {
    ...actual,
    routeLoader$: identity,
    routeLoaderQrl: identity,
    useLocation: () => ({
      url: new URL("http://localhost:3000/admin"),
    }),
  };
});

vi.mock("~/lib/domains/auth", () => ({
  resolveAuthRecoveryRedirectPath: mockResolveAuthRecoveryRedirectPath,
}));

vi.mock("~/lib/domains/admin", async () => {
  const helpers = await import(
    "~/lib/domains/admin/admin-layout.route-helpers"
  );
  return {
    ...helpers,
    fetchAdminFrameworkVersions: mockFetchAdminFrameworkVersions,
  };
});

function createCtx(user: unknown) {
  return {
    sharedMap: new Map<string, unknown>([["user", user]]),
    url: new URL("http://localhost:3000/admin?period=1h"),
    redirect: (status: number, to: string) => ({
      type: "redirect",
      status,
      to,
    }),
    cookie: {
      get: () => undefined,
    },
  };
}

describe("admin layout route loader runtime", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockResolveAuthRecoveryRedirectPath.mockReturnValue(
      "/auth?returnTo=%2Fadmin%3Fperiod%3D1h",
    );
    mockFetchAdminFrameworkVersions.mockReset();
  });

  it("returns an authenticated admin profile", async () => {
    const profile = {
      id: "admin-1",
      claims: ["ROLE_ADMIN"],
    };
    const mod = await import("~/routes/admin/layout");

    await expect(mod.useAdminGuard(createCtx(profile) as never)).resolves.toBe(
      profile,
    );
  });

  it("redirects a missing session through the recovery flow", async () => {
    const mod = await import("~/routes/admin/layout");

    await expect(mod.useAdminGuard(createCtx(null) as never)).rejects.toEqual({
      type: "redirect",
      status: 302,
      to: "/auth?returnTo=%2Fadmin%3Fperiod%3D1h",
    });
    expect(mockResolveAuthRecoveryRedirectPath).toHaveBeenCalledWith(
      undefined,
      "/admin?period=1h",
    );
  });

  it("redirects authenticated users without admin cabinet access", async () => {
    const mod = await import("~/routes/admin/layout");

    await expect(
      mod.useAdminGuard(
        createCtx({
          id: "participant-1",
          claims: ["ROLE_PARTICIPANT"],
        }) as never,
      ),
    ).rejects.toEqual({
      type: "redirect",
      status: 302,
      to: "/",
    });
  });

  it("loads the cached framework alert only for admin-cabinet users", async () => {
    const snapshot = {
      criticalUpdateRequired: true,
      criticalVulnerabilityCount: 1,
    };
    mockFetchAdminFrameworkVersions.mockResolvedValue(snapshot);
    const mod = await import("~/routes/admin/layout");

    await expect(
      mod.useFrameworkVersionAlert(
        createCtx({
          id: "admin-1",
          claims: ["ROLE_ADMIN"],
        }) as never,
      ),
    ).resolves.toBe(snapshot);
    expect(mockFetchAdminFrameworkVersions).toHaveBeenCalledOnce();

    await expect(
      mod.useFrameworkVersionAlert(
        createCtx({
          id: "participant-1",
          claims: ["ROLE_PARTICIPANT"],
        }) as never,
      ),
    ).resolves.toBeNull();
    expect(mockFetchAdminFrameworkVersions).toHaveBeenCalledOnce();
  });
});
