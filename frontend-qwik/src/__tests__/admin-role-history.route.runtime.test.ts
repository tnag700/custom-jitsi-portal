import { describe, expect, it } from "vitest";
import { buildAdminQueryHref } from "../lib/domains/admin/admin-route-query";
import {
  buildAdminRoleHistoryFilters,
  buildAdminRoleHistoryPageQueryUpdates,
  buildAdminRoleHistoryResetQueryUpdates,
  hasAdminRoleHistoryPrimaryFilter,
} from "../lib/domains/admin/admin-role-history.route-helpers";

describe("admin role-history route helpers", () => {
  it("builds trimmed filters with bounded query defaults preserved as strings", () => {
    const query = new URL(
      "https://portal.example.test/admin/role-history?environment=%20dev%20&q=%20alice%20&actorId=%20adm-1%20&page=%20-5%20&pageSize=%20%20&returnTo=%20%2Fadmin%2Fincidents%3Fenvironment%3Ddev%20",
    ).searchParams;

    expect(buildAdminRoleHistoryFilters(query)).toEqual({
      environment: "dev",
      q: "alice",
      from: "",
      to: "",
      actionType: "",
      role: "",
      actorId: "adm-1",
      subjectId: "",
      roomId: "",
      meetingId: "",
      page: "-5",
      pageSize: "20",
      returnTo: "/admin/incidents?environment=dev",
    });
  });

  it("detects primary filters only from supported drill-through fields", () => {
    expect(hasAdminRoleHistoryPrimaryFilter({
      environment: "dev",
      q: "",
      from: "",
      to: "",
      actionType: "assign",
      role: "moderator",
      actorId: "admin-1",
      subjectId: "",
      roomId: "",
      meetingId: "",
      page: "0",
      pageSize: "20",
      returnTo: "",
    })).toBe(false);

    expect(hasAdminRoleHistoryPrimaryFilter({
      environment: "dev",
      q: "",
      from: "",
      to: "",
      actionType: "",
      role: "",
      actorId: "",
      subjectId: "subject-1",
      roomId: "",
      meetingId: "",
      page: "0",
      pageSize: "20",
      returnTo: "",
    })).toBe(true);
  });

  it("clears filter fields while preserving returnTo and pageSize in reset links", () => {
    const currentUrl = new URL(
      "https://portal.example.test/admin/role-history?environment=dev&q=alice&subjectId=sub-1&actorId=adm-1&roomId=room-1&meetingId=meeting-1&actionType=assign&role=moderator&from=2026-03-19T09%3A00%3A00Z&to=2026-03-19T10%3A00%3A00Z&page=3&pageSize=50&returnTo=%2Fadmin%2Fincidents%3Fenvironment%3Ddev",
    );

    expect(buildAdminQueryHref(currentUrl, buildAdminRoleHistoryResetQueryUpdates())).toBe(
      "/admin/role-history?page=0&pageSize=50&returnTo=%2Fadmin%2Fincidents%3Fenvironment%3Ddev",
    );
  });

  it("updates only the page query value for pagination links", () => {
    const currentUrl = new URL(
      "https://portal.example.test/admin/role-history?subjectId=sub-1&page=1&pageSize=20&returnTo=%2Fadmin%2Fincidents%3Fenvironment%3Dprod",
    );

    expect(buildAdminQueryHref(currentUrl, buildAdminRoleHistoryPageQueryUpdates(2))).toBe(
      "/admin/role-history?subjectId=sub-1&page=2&pageSize=20&returnTo=%2Fadmin%2Fincidents%3Fenvironment%3Dprod",
    );
  });
});