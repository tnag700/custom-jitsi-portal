import type { SafeUserProfile } from "../auth";
import { buildAdminSecondaryHref } from "./admin-incidents.route-helpers";

const ADMIN_CABINET_CLAIMS = [
  "role_admin",
  "admin",
  "role_system-admin",
  "system-admin",
  "role_security-admin",
  "security-admin",
  "role_support-engineer",
  "support-engineer",
] as const;

export interface AdminLayoutNavItem {
  href: string;
  match: string;
  label: string;
}

export function hasAdminCabinetAccess(user: SafeUserProfile): boolean {
  return user.claims.some((claim) => ADMIN_CABINET_CLAIMS.includes(claim.trim().toLowerCase() as (typeof ADMIN_CABINET_CLAIMS)[number]));
}

export function withAdminEnvironment(href: string, environment: string | null): string {
  if (!environment || environment.trim().length === 0) {
    return href;
  }
  return `${href}?environment=${encodeURIComponent(environment)}`;
}

export function isActiveAdminNavItem(pathname: string, match: string): boolean {
  if (match === "/admin") {
    return pathname === match;
  }
  return pathname === match || pathname.startsWith(`${match}/`);
}

export function buildAdminPrimaryNavItems(environment: string | null): AdminLayoutNavItem[] {
  return [
    { href: withAdminEnvironment("/admin", environment), match: "/admin", label: "Сводка" },
    { href: withAdminEnvironment("/admin/incidents", environment), match: "/admin/incidents", label: "Инциденты" },
  ];
}

export function buildAdminSecondaryNavItems(
  currentUrl: URL,
  fallbackEnvironment: string,
  includePlatformAdminTools: boolean,
): AdminLayoutNavItem[] {
  const items: AdminLayoutNavItem[] = [
    {
      href: buildAdminSecondaryHref(currentUrl, "/admin/role-history", fallbackEnvironment),
      match: "/admin/role-history",
      label: "История ролей",
    },
    {
      href: buildAdminSecondaryHref(currentUrl, "/admin/config-sets", fallbackEnvironment),
      match: "/admin/config-sets",
      label: "Конфиг-наборы",
    },
  ];

  if (includePlatformAdminTools) {
    items.push({
      href: buildAdminSecondaryHref(currentUrl, "/admin/jitsi", fallbackEnvironment),
      match: "/admin/jitsi",
      label: "Доступ Jitsi",
    });
  }

  return items;
}
