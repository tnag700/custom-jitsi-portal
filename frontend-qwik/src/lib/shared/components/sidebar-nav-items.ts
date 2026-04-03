export interface NavItem {
  label: string;
  href: string;
  /** SVG path d attribute for the icon (24×24 viewBox) */
  icon: string;
}

const ADMIN_CABINET_CLAIMS = new Set([
  "role_admin",
  "admin",
  "role_system-admin",
  "system-admin",
  "role_security-admin",
  "security-admin",
  "role_support-engineer",
  "support-engineer",
]);

export const navItems: NavItem[] = [
  {
    label: "Кабинет",
    href: "/",
    icon: "M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-4 0a1 1 0 01-1-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 01-1 1",
  },
  {
    label: "Комнаты",
    href: "/rooms",
    icon: "M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4",
  },
  {
    label: "Встречи",
    href: "/meetings",
    icon: "M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z",
  },
  {
    label: "Профиль",
    href: "/profile",
    icon: "M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z",
  },
  {
    label: "Админ",
    href: "/admin",
    icon: "M12 6l7 4v4c0 5-3.5 9.5-7 11-3.5-1.5-7-6-7-11v-4l7-4zm0 3.2L8 11v2.8c0 3.8 2.4 7.2 4 8.2 1.6-1 4-4.4 4-8.2V11l-4-1.8z",
  },
];

export function filterNavItemsForClaims(items: NavItem[], claims: readonly string[]): NavItem[] {
  const normalizedClaims = new Set(claims.map((claim) => claim.trim().toLowerCase()));

  return items.filter((item) => {
    if (item.href !== "/admin") {
      return true;
    }

    for (const claim of normalizedClaims) {
      if (ADMIN_CABINET_CLAIMS.has(claim)) {
        return true;
      }
    }

    return false;
  });
}
