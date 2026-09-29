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
  description: string;
}

export interface AdminNavGroup {
  label: string;
  items: AdminLayoutNavItem[];
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
  pathname = pathname.replace(/\/$/, "");
  if (match === "/admin") {
    return pathname === match;
  }
  return pathname === match || pathname.startsWith(`${match}/`);
}

export function buildAdminNavGroups(
  currentUrl: URL,
  includePlatformAdminTools: boolean,
): AdminNavGroup[] {
  const environment = currentUrl.searchParams.get("environment");
  const groups = [
    {
      label: "Обзор и диагностика",
      items: [
        { match: "/admin", label: "Состояние платформы", description: "Сервисы и сигналы сбоев" },
        { match: "/admin/incidents", label: "Инциденты", description: "Ошибки входа и их разбор" },
      ],
    },
    {
      label: "Пользователи и доступ",
      items: [
        { match: "/admin/users", label: "Пользователи", description: "Профили и данные сотрудников" },
        { match: "/admin/role-history", label: "История ролей", description: "Кому и когда меняли права" },
      ],
    },
    {
      label: "Настройки системы",
      items: [
        { match: "/admin/jitsi", label: "Доступ к видеосвязи", description: "Готовность и правила входа Jitsi" },
        { match: "/admin/config-sets", label: "Конфигурация", description: "Наборы настроек и их применение" },
        { match: "/admin/framework-versions", label: "Обновления и уязвимости", description: "Версии компонентов и отчёт CVE" },
      ],
    },
  ];

  return groups.map((group) => ({
    ...group,
    items: group.items
      .filter((item) => includePlatformAdminTools || !["/admin/users", "/admin/jitsi"].includes(item.match))
      .map((item) => ({
        ...item,
        href: item.match === "/admin" || item.match === "/admin/incidents"
          ? withAdminEnvironment(item.match, environment)
          : buildAdminSecondaryHref(currentUrl, item.match, environment ?? ""),
      })),
  }));
}
