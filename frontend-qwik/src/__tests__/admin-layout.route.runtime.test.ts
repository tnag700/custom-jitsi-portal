import { describe, expect, it } from "vitest";
import type { SafeUserProfile } from "../lib/domains/auth";
import {
  buildAdminPrimaryNavItems,
  buildAdminSecondaryNavItems,
  hasAdminCabinetAccess,
  isActiveAdminNavItem,
  withAdminEnvironment,
} from "../lib/domains/admin/admin-layout.route-helpers";

describe("admin layout route helpers", () => {
  it("detects admin cabinet access from normalized claims", () => {
    expect(hasAdminCabinetAccess({ claims: ["viewer", " Role_Support-Engineer "] } as SafeUserProfile)).toBe(true);
    expect(hasAdminCabinetAccess({ claims: ["viewer"] } as SafeUserProfile)).toBe(false);
  });

  it("adds environment only when present for primary nav items", () => {
    expect(withAdminEnvironment("/admin", "prod")).toBe("/admin?environment=prod");
    expect(buildAdminPrimaryNavItems("prod")).toEqual([
      { href: "/admin?environment=prod", match: "/admin", label: "Сводка" },
      { href: "/admin/incidents?environment=prod", match: "/admin/incidents", label: "Инциденты" },
    ]);
    expect(buildAdminPrimaryNavItems(null)[0]?.href).toBe("/admin");
  });

  it("builds secondary nav items with preserved returnTo context", () => {
    const currentUrl = new URL("https://portal.example.test/admin/incidents?environment=dev&view=critical");

    expect(buildAdminSecondaryNavItems(currentUrl, "dev")).toEqual([
      {
        href: "/admin/role-history?environment=dev&returnTo=%2Fadmin%2Fincidents%3Fenvironment%3Ddev%26view%3Dcritical",
        match: "/admin/role-history",
        label: "История ролей",
      },
      {
        href: "/admin/config-sets?environment=dev&returnTo=%2Fadmin%2Fincidents%3Fenvironment%3Ddev%26view%3Dcritical",
        match: "/admin/config-sets",
        label: "Конфиг-наборы",
      },
    ]);
  });

  it("marks active admin navigation items by exact and nested route match", () => {
    expect(isActiveAdminNavItem("/admin", "/admin")).toBe(true);
    expect(isActiveAdminNavItem("/admin/incidents/incident-1", "/admin/incidents")).toBe(true);
    expect(isActiveAdminNavItem("/admin/role-history", "/admin/incidents")).toBe(false);
  });
});