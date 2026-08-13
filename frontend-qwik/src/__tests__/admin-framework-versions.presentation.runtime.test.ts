import { describe, expect, it } from "vitest";
import fs from "node:fs";
import path from "node:path";
import {
  canRefreshFrameworkVersions,
  frameworkScanStatusLabel,
  frameworkSecurityStatusLabel,
  hasCriticalFrameworkAlert,
  resolveFrameworkStatusTone,
} from "../lib/domains/admin/admin-framework-versions.presentation";

describe("admin framework version presentation", () => {
  it("limits manual refresh to the platform admin role", () => {
    expect(canRefreshFrameworkVersions(["ROLE_ADMIN"])).toBe(true);
    expect(canRefreshFrameworkVersions(["ROLE_SECURITY-ADMIN"])).toBe(false);
    expect(canRefreshFrameworkVersions(["ROLE_SUPPORT-ENGINEER"])).toBe(false);
  });

  it("maps scan and security states to explicit operator labels", () => {
    expect(frameworkScanStatusLabel("stale")).toBe("Устаревший снимок");
    expect(frameworkSecurityStatusLabel("critical")).toBe(
      "Требуется обновление",
    );
    expect(resolveFrameworkStatusTone("critical")).toBe("danger");
    expect(resolveFrameworkStatusTone("unavailable")).toBe("neutral");
  });

  it("shows a global alert only for critical findings", () => {
    expect(
      hasCriticalFrameworkAlert({
        criticalUpdateRequired: true,
      } as never),
    ).toBe(true);
    expect(hasCriticalFrameworkAlert(null)).toBe(false);
  });

  it("does not present a clean CVE result as proof of the latest release", () => {
    const source = fs.readFileSync(
      path.resolve(
        "src/lib/domains/admin/components/AdminFrameworkVersionsOverview.tsx",
      ),
      "utf8",
    );

    expect(source).toContain(
      "Отсутствие CVE не означает, что версия последняя",
    );
    expect(source).toContain("stack-version audit");
  });
});
