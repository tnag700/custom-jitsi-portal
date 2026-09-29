import { describe, expect, it } from "vitest";
import {
  canRefreshFrameworkVersions,
  frameworkReleaseStatusLabel,
  frameworkScanStatusLabel,
  frameworkSecurityStatusLabel,
  hasCriticalFrameworkAlert,
  hasFrameworkReleaseAlert,
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

  it("shows a release alert without requiring a CVE finding", () => {
    expect(
      hasFrameworkReleaseAlert({
        criticalUpdateRequired: false,
        updateAvailableCount: 1,
      } as never),
    ).toBe(true);
    expect(hasFrameworkReleaseAlert({ updateAvailableCount: 0 } as never)).toBe(
      false,
    );
  });

  it("does not call a failed release lookup current", () => {
    expect(frameworkReleaseStatusLabel("stale")).toBe("Данные устарели");
    expect(frameworkReleaseStatusLabel("unavailable")).toBe(
      "Проверка недоступна",
    );
    expect(frameworkReleaseStatusLabel("update_available")).toBe(
      "Есть обновление",
    );
  });
});
