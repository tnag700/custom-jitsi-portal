import { describe, expect, it } from "vitest";
import {
  filterNavItemsForClaims,
  navItems,
} from "~/lib/shared/components/sidebar-nav-items";
import { hasPlatformAdminAccess } from "~/lib/shared/security/access-claims";

describe("navigation permissions", () => {
  it("keeps participant navigation away from admin-only routes", () => {
    const visibleHrefs = filterNavItemsForClaims(navItems, [
      "role_participant",
    ]).map((item) => item.href);

    expect(visibleHrefs).toEqual(["/", "/profile"]);
    expect(hasPlatformAdminAccess(["role_participant"])).toBe(false);
  });

  it("shows room and meeting management to platform admins", () => {
    const visibleHrefs = filterNavItemsForClaims(navItems, ["ROLE_ADMIN"]).map(
      (item) => item.href,
    );

    expect(visibleHrefs).toEqual([
      "/",
      "/rooms",
      "/meetings",
      "/profile",
      "/admin",
    ]);
    expect(hasPlatformAdminAccess(["ROLE_ADMIN"])).toBe(true);
  });

  it("keeps support users in the admin cabinet without exposing mutation modules", () => {
    const visibleHrefs = filterNavItemsForClaims(navItems, [
      "role_support-engineer",
    ]).map((item) => item.href);

    expect(visibleHrefs).toEqual(["/", "/profile", "/admin"]);
  });
});
