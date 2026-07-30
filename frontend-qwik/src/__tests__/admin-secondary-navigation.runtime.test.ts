import { describe, expect, it } from "vitest";
import {
  buildAdminSecondaryHref,
  resolveIncidentReturnTo,
} from "~/lib/domains/admin/admin-incidents.route-helpers";

describe("admin secondary navigation runtime", () => {
  it("buildAdminSecondaryHref preserves environment and queue context for role-history", () => {
    const currentUrl = new URL(
      "https://portal.example.test/admin/incidents?environment=prod&view=critical",
    );

    expect(
      buildAdminSecondaryHref(currentUrl, "/admin/role-history", "prod"),
    ).toBe(
      "/admin/role-history?environment=prod&returnTo=%2Fadmin%2Fincidents%3Fenvironment%3Dprod%26view%3Dcritical",
    );
  });

  it("buildAdminSecondaryHref keeps detail-scoped identifiers for role-history drill-through", () => {
    const currentUrl = new URL(
      "https://portal.example.test/admin/incidents/incident-1?environment=test&subjectId=sub-1&roomId=room-1&meetingId=meeting-1&returnTo=%2Fadmin%2Fincidents%3Fenvironment%3Dtest",
    );

    expect(
      buildAdminSecondaryHref(currentUrl, "/admin/role-history", "test"),
    ).toBe(
      "/admin/role-history?environment=test&subjectId=sub-1&roomId=room-1&meetingId=meeting-1&returnTo=%2Fadmin%2Fincidents%2Fincident-1%3Fenvironment%3Dtest%26subjectId%3Dsub-1%26roomId%3Droom-1%26meetingId%3Dmeeting-1%26returnTo%3D%252Fadmin%252Fincidents%253Fenvironment%253Dtest",
    );
  });

  it("buildAdminSecondaryHref keeps config-sets bounded to environment and safe triage return path", () => {
    const currentUrl = new URL(
      "https://portal.example.test/admin/incidents/incident-1?environment=dev&subjectId=sub-1&roomId=room-1&meetingId=meeting-1",
    );

    expect(
      buildAdminSecondaryHref(currentUrl, "/admin/config-sets", "dev"),
    ).toBe(
      "/admin/config-sets?environment=dev&returnTo=%2Fadmin%2Fincidents%2Fincident-1%3Fenvironment%3Ddev%26subjectId%3Dsub-1%26roomId%3Droom-1%26meetingId%3Dmeeting-1",
    );
  });

  it("resolveIncidentReturnTo falls back to queue when a secondary module has no explicit returnTo", () => {
    const currentUrl = new URL(
      "https://portal.example.test/admin/role-history?environment=dev&subjectId=sub-1",
    );

    expect(resolveIncidentReturnTo(currentUrl, "dev")).toBe(
      "/admin/incidents?environment=dev&subjectId=sub-1",
    );
  });
});
