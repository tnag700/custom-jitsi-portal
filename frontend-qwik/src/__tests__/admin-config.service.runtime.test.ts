import { afterEach, describe, expect, it, vi } from "vitest";
import {
  AdminConfigServiceError,
  checkAdminConfigSetCompatibility,
  createAdminConfigSet,
  fetchAdminConfigSet,
  fetchAdminConfigSets,
  fetchLatestAdminConfigSetRollout,
  rollbackAdminConfigSet,
  rolloutAdminConfigSet,
  updateAdminConfigSet,
} from "../lib/domains/admin/admin-config.service";

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json",
    },
  });
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("admin-config.service runtime", () => {
  it("fetchAdminConfigSets calls existing config-sets list endpoint with tenant and pagination filters", async () => {
    const payload = {
      content: [
        {
          configSetId: "cfg-1",
          name: "Primary DEV",
          tenantId: "tenant-1",
          environmentType: "dev",
          issuer: "issuer",
          audience: "aud",
          algorithm: "HS256",
          roleClaim: "roles",
          signingSecret: "***",
          jwksUri: null,
          accessTtlMinutes: 15,
          refreshTtlMinutes: 60,
          meetingsServiceUrl: "https://meet.dev",
          status: "active",
          createdAt: "2026-03-18T10:00:00Z",
          updatedAt: "2026-03-18T11:00:00Z",
        },
      ],
      page: 0,
      pageSize: 20,
      totalElements: 1,
      totalPages: 1,
    };

    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(payload, 200));

    const result = await fetchAdminConfigSets("sess-1", "http://localhost:8080/api/v1", {
      tenantId: "tenant-1",
      page: 0,
      size: 20,
    });

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/config-sets?tenantId=tenant-1&page=0&size=20",
      expect.objectContaining({ method: "GET" }),
    );
    expect(result.items).toHaveLength(1);
  });

  it("fetchAdminConfigSets accepts an existing server request context object", async () => {
    const payload = {
      content: [],
      page: 0,
      pageSize: 20,
      totalElements: 0,
      totalPages: 0,
    };

    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(payload, 200));

    await fetchAdminConfigSets({
      apiUrl: "http://localhost:8080/api/v1",
      sessionCookie: "sess-ctx",
      csrfToken: "csrf-ctx",
      headers: {
        Cookie: "JSESSIONID=sess-ctx",
        Accept: "application/json",
      },
    }, {
      tenantId: "tenant-ctx",
      page: 1,
      size: 10,
    });

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/config-sets?tenantId=tenant-ctx&page=1&size=10",
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({
          Cookie: "JSESSIONID=sess-ctx",
        }),
      }),
    );
  });

  it("fetchAdminConfigSet composes detail, compatibility and latest rollout using existing config-sets endpoints", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(jsonResponse({
        configSetId: "cfg-1",
        name: "Primary DEV",
        tenantId: "tenant-1",
        environmentType: "dev",
        issuer: "issuer",
        audience: "aud",
        algorithm: "HS256",
        roleClaim: "roles",
        signingSecret: "***",
        jwksUri: null,
        accessTtlMinutes: 15,
        refreshTtlMinutes: 60,
        meetingsServiceUrl: "https://meet.dev",
        status: "active",
        createdAt: "2026-03-18T10:00:00Z",
        updatedAt: "2026-03-18T11:00:00Z",
      }, 200))
      .mockResolvedValueOnce(jsonResponse({
        status: "COMPATIBLE",
        mismatches: [],
        checkedAt: "2026-03-18T11:05:00Z",
        traceId: "trace-compat-1",
      }, 200))
      .mockResolvedValueOnce(jsonResponse({
        rolloutId: "rollout-1",
        configSetId: "cfg-1",
        previousConfigSetId: null,
        tenantId: "tenant-1",
        environmentType: "DEV",
        status: "SUCCEEDED",
        validationErrors: null,
        startedAt: "2026-03-18T10:30:00Z",
        completedAt: "2026-03-18T10:31:00Z",
        actorId: "alice.admin",
      }, 200));

    const result = await fetchAdminConfigSet("sess-1", "http://localhost:8080/api/v1", {
      configSetId: "cfg-1",
      tenantId: "tenant-1",
    });

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      "http://localhost:8080/api/v1/config-sets/cfg-1",
      expect.objectContaining({ method: "GET" }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      "http://localhost:8080/api/v1/config-sets/cfg-1/compatibility?tenantId=tenant-1",
      expect.objectContaining({ method: "GET" }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      3,
      "http://localhost:8080/api/v1/config-sets/rollouts/latest?tenantId=tenant-1&environmentType=DEV",
      expect.objectContaining({ method: "GET" }),
    );
    expect(result.latestRollout?.actorId).toBe("alice.admin");
  });

  it("create, update, compatibility, rollout and rollback reuse config-sets mutation endpoints with mutation headers", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(jsonResponse({ configSetId: "cfg-1", name: "Primary DEV", tenantId: "tenant-1", environmentType: "dev", issuer: "issuer", audience: "aud", algorithm: "HS256", roleClaim: "roles", signingSecret: "***", jwksUri: null, accessTtlMinutes: 15, refreshTtlMinutes: 60, meetingsServiceUrl: "https://meet.dev", status: "draft", createdAt: "2026-03-18T10:00:00Z", updatedAt: "2026-03-18T10:00:00Z" }, 201))
      .mockResolvedValueOnce(jsonResponse({ configSetId: "cfg-1", name: "Primary DEV 2", tenantId: "tenant-1", environmentType: "dev", issuer: "issuer", audience: "aud", algorithm: "HS256", roleClaim: "roles", signingSecret: "***", jwksUri: null, accessTtlMinutes: 15, refreshTtlMinutes: 60, meetingsServiceUrl: "https://meet.dev", status: "draft", createdAt: "2026-03-18T10:00:00Z", updatedAt: "2026-03-18T10:01:00Z" }, 200))
      .mockResolvedValueOnce(jsonResponse({ status: "INCOMPATIBLE", mismatches: [{ code: "URL", message: "Bad URL", expected: "https", actual: "http" }], checkedAt: "2026-03-18T10:10:00Z", traceId: "trace-compat-2" }, 200))
      .mockResolvedValueOnce(jsonResponse({ rolloutId: "rollout-2", configSetId: "cfg-1", previousConfigSetId: null, tenantId: "tenant-1", environmentType: "DEV", status: "PENDING", validationErrors: null, startedAt: "2026-03-18T10:12:00Z", completedAt: null, actorId: "alice.admin" }, 200))
      .mockResolvedValueOnce(jsonResponse({ rolloutId: "rollout-3", configSetId: "cfg-1", previousConfigSetId: "cfg-prev", tenantId: "tenant-1", environmentType: "DEV", status: "ROLLED_BACK", validationErrors: null, startedAt: "2026-03-18T10:13:00Z", completedAt: "2026-03-18T10:14:00Z", actorId: "alice.admin" }, 200));

    const mutationContext = {
      apiUrl: "http://localhost:8080/api/v1",
      sessionCookie: "sess-1",
      csrfToken: "csrf-1",
      csrfCookieToken: "csrf-cookie-1",
      idempotencyKey: "idem-1",
      headers: {
        Cookie: "JSESSIONID=sess-1; XSRF-TOKEN=csrf-cookie-1",
        "X-CSRF-TOKEN": "csrf-1",
        "X-XSRF-TOKEN": "csrf-cookie-1",
        "Idempotency-Key": "idem-1",
        Accept: "application/json",
        "Content-Type": "application/json",
      },
    };

    await createAdminConfigSet(mutationContext, {
      name: "Primary DEV",
      tenantId: "tenant-1",
      environmentType: "DEV",
      issuer: "issuer",
      audience: "aud",
      algorithm: "HS256",
      roleClaim: "roles",
      signingSecret: "secret",
      jwksUri: "",
      accessTtlMinutes: 15,
      refreshTtlMinutes: 60,
      meetingsServiceUrl: "https://meet.dev",
    });
    await updateAdminConfigSet(mutationContext, "cfg-1", {
      name: "Primary DEV 2",
      tenantId: "tenant-1",
      environmentType: "DEV",
      issuer: "issuer",
      audience: "aud",
      algorithm: "HS256",
      roleClaim: "roles",
      signingSecret: "secret",
      jwksUri: "",
      accessTtlMinutes: 15,
      refreshTtlMinutes: 60,
      meetingsServiceUrl: "https://meet.dev",
    });
    await checkAdminConfigSetCompatibility("sess-1", "http://localhost:8080/api/v1", {
      configSetId: "cfg-1",
      tenantId: "tenant-1",
    });
    await rolloutAdminConfigSet(mutationContext, { configSetId: "cfg-1", tenantId: "tenant-1" });
    await rollbackAdminConfigSet(mutationContext, {
      configSetId: "cfg-1",
      tenantId: "tenant-1",
      environmentType: "DEV",
    });

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      "http://localhost:8080/api/v1/config-sets",
      expect.objectContaining({ method: "POST", headers: expect.objectContaining({ "Idempotency-Key": "idem-1" }) }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      "http://localhost:8080/api/v1/config-sets/cfg-1",
      expect.objectContaining({ method: "PUT", headers: expect.objectContaining({ "X-CSRF-TOKEN": "csrf-1" }) }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      3,
      "http://localhost:8080/api/v1/config-sets/cfg-1/compatibility?tenantId=tenant-1",
      expect.objectContaining({ method: "GET" }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      4,
      "http://localhost:8080/api/v1/config-sets/cfg-1/rollout?tenantId=tenant-1",
      expect.objectContaining({ method: "POST" }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      5,
      "http://localhost:8080/api/v1/config-sets/cfg-1/rollback?tenantId=tenant-1&environmentType=DEV",
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("throws AdminConfigServiceError for access denied problem details", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse(
        {
          title: "Forbidden",
          detail: "Access denied",
          errorCode: "ACCESS_DENIED",
          traceId: "trace-403",
        },
        403,
      ),
    );

    await expect(
      fetchLatestAdminConfigSetRollout("sess-1", "http://localhost:8080/api/v1", {
        tenantId: "tenant-1",
        environmentType: "DEV",
      }),
    ).rejects.toBeInstanceOf(AdminConfigServiceError);
  });
});