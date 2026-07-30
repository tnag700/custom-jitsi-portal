import { describe, expect, it } from "vitest";
import {
  describeAdminRoleTransition,
  formatAdminEnvironment,
  formatAdminMeetingRole,
  formatAdminRoleHistoryDateTime,
  hasAdminRoleHistoryAdvancedFilters,
} from "../lib/domains/admin/admin-role-history.presentation";
import type { AdminRoleHistoryFilters } from "../lib/domains/admin/admin-role-history.route-helpers";

function filters(
  overrides: Partial<AdminRoleHistoryFilters> = {},
): AdminRoleHistoryFilters {
  return {
    environment: "",
    q: "",
    from: "",
    to: "",
    actionType: "",
    role: "",
    actorId: "",
    subjectId: "",
    roomId: "",
    meetingId: "",
    page: "0",
    pageSize: "20",
    returnTo: "",
    ...overrides,
  };
}

describe("admin role history presentation", () => {
  it("localizes known meeting roles and preserves unknown roles", () => {
    expect(formatAdminMeetingRole("HOST")).toBe("Организатор");
    expect(formatAdminMeetingRole("moderator")).toBe("Модератор");
    expect(formatAdminMeetingRole("participant")).toBe("Участник");
    expect(formatAdminMeetingRole("observer")).toBe("observer");
    expect(formatAdminMeetingRole(null)).toBe("не назначена");
  });

  it("localizes supported environment values without changing unknown values", () => {
    expect(formatAdminEnvironment("DEV")).toBe("Разработка");
    expect(formatAdminEnvironment("production")).toBe("Рабочая среда");
    expect(formatAdminEnvironment("sandbox")).toBe("sandbox");
  });

  it("describes a role transition with localized labels", () => {
    expect(describeAdminRoleTransition("participant", "moderator")).toBe(
      "Участник → Модератор",
    );
  });

  it("formats a valid timestamp in UTC and preserves invalid values", () => {
    const formatted = formatAdminRoleHistoryDateTime("2026-03-19T09:00:00Z");
    expect(formatted).toContain("09:00");
    expect(formatted).toContain("UTC");
    expect(formatAdminRoleHistoryDateTime("not-a-date")).toBe("not-a-date");
  });

  it("opens advanced filters only when a secondary filter differs from defaults", () => {
    expect(hasAdminRoleHistoryAdvancedFilters(filters())).toBe(false);
    expect(hasAdminRoleHistoryAdvancedFilters(filters({ q: "Иванов" }))).toBe(
      false,
    );
    expect(
      hasAdminRoleHistoryAdvancedFilters(filters({ environment: "prod" })),
    ).toBe(false);
    expect(
      hasAdminRoleHistoryAdvancedFilters(filters({ pageSize: "50" })),
    ).toBe(true);
  });
});
