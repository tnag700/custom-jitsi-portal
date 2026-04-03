export function sanitizeAdminQueryValue(value: string | null): string {
  return value?.trim() ?? "";
}

export function normalizePositiveAdminInteger(value: string, fallback: number): number {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

export function normalizeNonNegativeAdminInteger(value: string, fallback: number): number {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback;
}

export function buildAdminQueryHref(url: URL, updates: Record<string, string | null>): string {
  const next = new URL(url.toString());
  Object.entries(updates).forEach(([key, value]) => {
    if (value === null || value.trim().length === 0) {
      next.searchParams.delete(key);
      return;
    }

    next.searchParams.set(key, value);
  });

  return `${next.pathname}${next.search}`;
}

export function buildAdminOverviewHref(environment: string): string {
  return environment.trim().length > 0 ? `/admin?environment=${encodeURIComponent(environment)}` : "/admin";
}