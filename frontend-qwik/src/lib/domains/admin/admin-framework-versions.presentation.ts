import type { AdminFrameworkVersions } from "./types";

const REFRESH_CLAIMS = new Set(["role_admin", "admin"]);

export function canRefreshFrameworkVersions(claims: readonly string[]): boolean {
  return claims.some((claim) =>
    REFRESH_CLAIMS.has(claim.trim().toLowerCase()),
  );
}

export function formatFrameworkCheckTime(value: string | null): string {
  if (!value) {
    return "успешных проверок ещё не было";
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return "время проверки неизвестно";
  }
  return new Intl.DateTimeFormat("ru-RU", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: "Europe/Minsk",
  }).format(parsed);
}

export function frameworkScanStatusLabel(status: string): string {
  switch (status) {
    case "current":
      return "Проверено";
    case "partial":
      return "Проверено частично";
    case "stale":
      return "Устаревший снимок";
    case "disabled":
      return "Проверка отключена";
    default:
      return "Проверка недоступна";
  }
}

export function frameworkSecurityStatusLabel(status: string): string {
  switch (status) {
    case "critical":
      return "Требуется обновление";
    case "attention":
      return "Есть уязвимости";
    case "safe":
      return "Известных уязвимостей нет";
    default:
      return "Статус неизвестен";
  }
}

export function resolveFrameworkStatusTone(
  status: string,
): "danger" | "warning" | "success" | "neutral" {
  if (status === "critical") return "danger";
  if (status === "attention" || status === "partial" || status === "stale") {
    return "warning";
  }
  if (status === "safe" || status === "current") return "success";
  return "neutral";
}

export function hasCriticalFrameworkAlert(
  snapshot: AdminFrameworkVersions | null,
): boolean {
  return snapshot?.criticalUpdateRequired === true;
}
