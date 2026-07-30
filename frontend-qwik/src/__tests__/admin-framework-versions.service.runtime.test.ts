import { afterEach, describe, expect, it, vi } from "vitest";
import {
  fetchAdminFrameworkVersions,
  refreshAdminFrameworkVersions,
} from "../lib/domains/admin/admin.service";

function snapshotPayload() {
  return {
    generatedAt: "2026-07-30T10:00:00Z",
    lastSuccessfulCheckAt: "2026-07-30T10:00:00Z",
    cacheExpiresAt: "2026-07-30T16:00:00Z",
    scanStatus: "current",
    statusMessage: "Versions checked.",
    criticalUpdateRequired: true,
    vulnerabilityCount: 1,
    criticalVulnerabilityCount: 1,
    components: [
      {
        key: "qwik",
        displayName: "Qwik",
        ecosystem: "npm",
        packageName: "@qwik.dev/core",
        currentVersion: "2.0.0-beta.38",
        versionSource: "build-config",
        scanStatus: "current",
        securityStatus: "critical",
        vulnerabilityCount: 1,
        criticalVulnerabilityCount: 1,
        advisories: [
          {
            id: "GHSA-test",
            aliases: ["CVE-2026-12345"],
            summary: "Critical issue",
            severity: "critical",
            fixedVersions: ["2.0.0"],
            advisoryUrl: "https://osv.dev/vulnerability/GHSA-test",
            modifiedAt: "2026-07-29T10:00:00Z",
          },
        ],
      },
    ],
  };
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("admin framework version service", () => {
  it("loads and validates the cached admin snapshot", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify(snapshotPayload()), {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );

    const result = await fetchAdminFrameworkVersions(
      "session-1",
      "http://localhost:8080/api/v1",
    );

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/admin/framework-versions",
      expect.objectContaining({ method: "GET" }),
    );
    expect(result.criticalUpdateRequired).toBe(true);
    expect(result.components[0]?.advisories[0]?.fixedVersions).toEqual([
      "2.0.0",
    ]);
  });

  it("refreshes through the protected mutation endpoint", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify(snapshotPayload()), {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );

    await refreshAdminFrameworkVersions(
      "session-1",
      "http://localhost:8080/api/v1",
      "csrf-1",
    );

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/admin/framework-versions/refresh",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({
          "X-XSRF-TOKEN": "csrf-1",
        }),
      }),
    );
  });
});
