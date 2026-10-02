import { describe, expect, it } from "vitest";
import type { SafeUserProfile } from "../lib/domains/auth";
import {
  buildAdminNavGroups,
  hasAdminCabinetAccess,
  isActiveAdminNavItem,
  withAdminEnvironment,
} from "../lib/domains/admin/admin-layout.route-helpers";

describe("admin layout route helpers", () => {
  it("groups every visible destination by purpose without changing deep links", () => {
    const url = new URL(
      "https://portal.example.test/admin/incidents/incident-1?environment=prod&roomId=room-1",
    );
    const groups = buildAdminNavGroups(url, true);

    expect(groups.map((group) => group.label)).toEqual([
      "Обзор и диагностика",
      "Пользователи и доступ",
      "Настройки системы",
    ]);
    expect(
      groups.map((group) => group.items.map((item) => item.match)),
    ).toEqual([
      ["/admin", "/admin/incidents", "/admin/metrics"],
      ["/admin/users", "/admin/role-history"],
      ["/admin/jitsi", "/admin/config-sets", "/admin/framework-versions"],
    ]);
    expect(
      groups
        .flatMap((group) => group.items)
        .every((item) => item.description.length > 0),
    ).toBe(true);
    const history = groups[1].items.find(
      (item) => item.match === "/admin/role-history",
    );
    expect(new URL(history!.href, url).searchParams.get("returnTo")).toBe(
      `${url.pathname}${url.search}`,
    );
    expect(new URL(history!.href, url).searchParams.get("roomId")).toBe(
      "room-1",
    );
    expect(
      groups
        .flatMap((group) => group.items)
        .every(
          (item) =>
            new URL(item.href, url).searchParams.get("environment") === "prod",
        ),
    ).toBe(true);
  });

  it("keeps platform-only tools out of the grouped operator navigation", () => {
    const groups = buildAdminNavGroups(
      new URL("https://portal.example.test/admin"),
      false,
    );
    expect(
      groups.flatMap((group) => group.items).map((item) => item.match),
    ).toEqual([
      "/admin",
      "/admin/incidents",
      "/admin/role-history",
      "/admin/config-sets",
      "/admin/framework-versions",
    ]);
  });

  it("detects admin cabinet access from normalized claims", () => {
    expect(
      hasAdminCabinetAccess({
        claims: ["viewer", " Role_Support-Engineer "],
      } as SafeUserProfile),
    ).toBe(true);
    expect(
      hasAdminCabinetAccess({ claims: ["viewer"] } as SafeUserProfile),
    ).toBe(false);
  });

  it("adds environment only when present for primary nav items", () => {
    expect(withAdminEnvironment("/admin", "prod")).toBe(
      "/admin?environment=prod",
    );
    expect(withAdminEnvironment("/admin", null)).toBe("/admin");
  });

  it("builds secondary nav items with preserved returnTo context", () => {
    const currentUrl = new URL(
      "https://portal.example.test/admin/incidents?environment=dev&view=critical",
    );

    expect(
      buildAdminNavGroups(currentUrl, true).flatMap((group) => group.items),
    ).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          href: "/admin?environment=dev",
          match: "/admin",
        }),
        expect.objectContaining({
          href: "/admin/incidents?environment=dev",
          match: "/admin/incidents",
        }),
        expect.objectContaining({
          href: "/admin/role-history?environment=dev&returnTo=%2Fadmin%2Fincidents%3Fenvironment%3Ddev%26view%3Dcritical",
          match: "/admin/role-history",
          label: "История ролей",
        }),
        expect.objectContaining({
          href: "/admin/config-sets?environment=dev&returnTo=%2Fadmin%2Fincidents%3Fenvironment%3Ddev%26view%3Dcritical",
          match: "/admin/config-sets",
          label: "Конфигурация",
        }),
        expect.objectContaining({
          href: "/admin/framework-versions?environment=dev&returnTo=%2Fadmin%2Fincidents%3Fenvironment%3Ddev%26view%3Dcritical",
          match: "/admin/framework-versions",
          label: "Обновления и уязвимости",
        }),
        expect.objectContaining({
          href: "/admin/users?environment=dev&returnTo=%2Fadmin%2Fincidents%3Fenvironment%3Ddev%26view%3Dcritical",
          match: "/admin/users",
          label: "Пользователи",
        }),
        expect.objectContaining({
          href: "/admin/jitsi?environment=dev&returnTo=%2Fadmin%2Fincidents%3Fenvironment%3Ddev%26view%3Dcritical",
          match: "/admin/jitsi",
          label: "Доступ к видеосвязи",
        }),
      ]),
    );
  });

  it("does not expose the Jitsi access module to non-platform operators", () => {
    const currentUrl = new URL(
      "https://portal.example.test/admin?environment=prod",
    );

    expect(
      buildAdminNavGroups(currentUrl, false)
        .slice(1)
        .flatMap((group) => group.items.map((item) => item.match)),
    ).toEqual([
      "/admin/role-history",
      "/admin/config-sets",
      "/admin/framework-versions",
    ]);
  });

  it("marks active admin navigation items by exact and nested route match", () => {
    expect(isActiveAdminNavItem("/admin", "/admin")).toBe(true);
    expect(isActiveAdminNavItem("/admin/", "/admin")).toBe(true);
    expect(
      isActiveAdminNavItem("/admin/incidents/incident-1", "/admin/incidents"),
    ).toBe(true);
    expect(
      isActiveAdminNavItem("/admin/role-history", "/admin/incidents"),
    ).toBe(false);
  });
});
