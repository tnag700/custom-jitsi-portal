import type { AdminRoleHistoryFilters } from "./admin-role-history.route-helpers";

const ROLE_LABELS: Record<string, string> = {
  host: "Организатор",
  moderator: "Модератор",
  participant: "Участник",
};

const ENVIRONMENT_LABELS: Record<string, string> = {
  dev: "Разработка",
  development: "Разработка",
  test: "Тест",
  prod: "Рабочая среда",
  production: "Рабочая среда",
};

export function formatAdminEnvironment(value: string): string {
  return ENVIRONMENT_LABELS[value.toLowerCase()] ?? value;
}

export function formatAdminRoleHistoryDateTime(value: string): string {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }

  return `${new Intl.DateTimeFormat("ru-RU", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: "UTC",
  }).format(parsed)} UTC`;
}

export function formatAdminMeetingRole(
  role: string | null | undefined,
): string {
  if (!role) {
    return "не назначена";
  }
  return ROLE_LABELS[role.toLowerCase()] ?? role;
}

export function describeAdminRoleTransition(
  oldRole: string | null | undefined,
  newRole: string | null | undefined,
): string {
  return `${formatAdminMeetingRole(oldRole)} → ${formatAdminMeetingRole(newRole)}`;
}

export function hasAdminRoleHistoryAdvancedFilters(
  filters: AdminRoleHistoryFilters,
): boolean {
  return Boolean(
    filters.actionType ||
      filters.role ||
      filters.actorId ||
      filters.from ||
      filters.to ||
      filters.pageSize !== "20",
  );
}
