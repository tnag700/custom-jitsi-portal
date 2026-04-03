import type { AdminDashboardSummary } from "./types";
import { buildAdminSecondaryHref, buildDashboardIncidentHref } from "./admin-incidents.route-helpers";
import { sanitizeAdminQueryValue } from "./admin-route-query";

export interface AdminDashboardFilters {
  period: string;
  environment: string;
  roomId: string;
  meetingId: string;
  errorCode: string;
  category: string;
}

export interface AdminDashboardDrillDownSelection {
  environment: string;
  period: string;
  severity: string;
  errorCode: string;
  category: string;
  roomId: string;
  meetingId: string;
  incidentId: string;
}

export interface AdminDashboardDerivedState {
  activeEnvironment: string;
  activePeriod: string;
  activeDrillDownSelection: AdminDashboardDrillDownSelection | null;
  secondaryModuleLinks: Array<{
    label: string;
    href: string;
  }>;
}

export function buildAdminDashboardFilters(query: URLSearchParams): AdminDashboardFilters {
  return {
    period: sanitizeAdminQueryValue(query.get("period")) || "15m",
    environment: sanitizeAdminQueryValue(query.get("environment")),
    roomId: sanitizeAdminQueryValue(query.get("roomId")),
    meetingId: sanitizeAdminQueryValue(query.get("meetingId")),
    errorCode: sanitizeAdminQueryValue(query.get("errorCode")),
    category: sanitizeAdminQueryValue(query.get("category")),
  };
}

type DashboardHandoff = AdminDashboardSummary["priorityBanner"]["handoff"];

function normalizeSelectionFromHandoff(
  handoff: DashboardHandoff,
  activeEnvironment: string,
  activePeriod: string,
): AdminDashboardDrillDownSelection {
  return {
    environment: handoff.environment || activeEnvironment,
    period: handoff.period || activePeriod,
    severity: handoff.severity || "",
    errorCode: handoff.errorCode ?? "",
    category: handoff.category ?? "",
    roomId: handoff.roomId ?? "",
    meetingId: handoff.meetingId ?? "",
    incidentId: handoff.incidentId ?? "",
  };
}

function hasMeaningfulSelectionValue(selection: AdminDashboardDrillDownSelection): boolean {
  return [
    selection.errorCode,
    selection.category,
    selection.roomId,
    selection.meetingId,
  ].some((value) => value.trim().length > 0);
}

function buildSelectionFromFilters(
  filters: AdminDashboardFilters,
  activeEnvironment: string,
  activePeriod: string,
): AdminDashboardDrillDownSelection | null {
  const selection: AdminDashboardDrillDownSelection = {
    environment: activeEnvironment,
    period: activePeriod,
    severity: "",
    errorCode: filters.errorCode,
    category: filters.category,
    roomId: filters.roomId,
    meetingId: filters.meetingId,
    incidentId: "",
  };

  return hasMeaningfulSelectionValue(selection) ? selection : null;
}

function deriveDefaultSelection(
  dashboard: AdminDashboardSummary,
  activeEnvironment: string,
  activePeriod: string,
): AdminDashboardDrillDownSelection | null {
  const candidates: DashboardHandoff[] = [
    dashboard.priorityBanner.handoff,
    ...dashboard.topDegradations.map((item) => item.handoff),
    ...dashboard.latestSpikes.map((item) => item.handoff),
    ...dashboard.affectedScopeSummary.map((item) => item.handoff),
    ...dashboard.keyServiceStatuses.map((item) => item.handoff),
  ];

  for (const candidate of candidates) {
    const selection = normalizeSelectionFromHandoff(candidate, activeEnvironment, activePeriod);
    if (hasMeaningfulSelectionValue(selection)) {
      return selection;
    }
  }

  return null;
}

export function buildAdminDashboardDerivedState(
  currentUrl: URL,
  dashboard: AdminDashboardSummary,
  filters: AdminDashboardFilters,
): AdminDashboardDerivedState {
  const activeEnvironment = filters.environment || dashboard.environment;
  const activePeriod = filters.period || dashboard.period;
  const activeDrillDownSelection = buildSelectionFromFilters(filters, activeEnvironment, activePeriod)
    ?? deriveDefaultSelection(dashboard, activeEnvironment, activePeriod);

  return {
    activeEnvironment,
    activePeriod,
    activeDrillDownSelection,
    secondaryModuleLinks: [
      {
        label: "История ролей",
        href: buildAdminSecondaryHref(currentUrl, "/admin/role-history", activeEnvironment),
      },
      {
        label: "Конфиг-наборы",
        href: buildAdminSecondaryHref(currentUrl, "/admin/config-sets", activeEnvironment),
      },
    ],
  };
}

export function buildAdminDashboardSelectionHref(
  currentUrl: URL,
  handoff: DashboardHandoff,
  state: Pick<AdminDashboardDerivedState, "activeEnvironment" | "activePeriod">,
): string {
  const next = new URL(currentUrl);
  next.searchParams.set("environment", handoff.environment || state.activeEnvironment);
  next.searchParams.set("period", handoff.period || state.activePeriod);

  const updates = {
    errorCode: handoff.errorCode ?? "",
    category: handoff.category ?? "",
    roomId: handoff.roomId ?? "",
    meetingId: handoff.meetingId ?? "",
  };

  for (const [key, value] of Object.entries(updates)) {
    if (value.trim().length > 0) {
      next.searchParams.set(key, value);
      continue;
    }
    next.searchParams.delete(key);
  }

  return `${next.pathname}${next.search}`;
}

export function buildAdminDashboardActiveIncidentsHref(
  currentUrl: URL,
  selection: AdminDashboardDrillDownSelection | null,
  state: Pick<AdminDashboardDerivedState, "activeEnvironment" | "activePeriod">,
): string {
  if (!selection) {
    const next = new URL("/admin/incidents", currentUrl);
    next.searchParams.set("environment", state.activeEnvironment);
    next.searchParams.set("period", state.activePeriod);
    return `${next.pathname}${next.search}`;
  }

  return buildDashboardIncidentHref(
    currentUrl,
    {
      environment: selection.environment,
      period: selection.period,
      severity: selection.severity,
      errorCode: selection.errorCode || null,
      category: selection.category || null,
      roomId: selection.roomId || null,
      meetingId: selection.meetingId || null,
      incidentId: selection.incidentId || null,
    },
    state.activeEnvironment,
    state.activePeriod,
  );
}

export function buildAdminDashboardIncidentHandoffHref(
  currentUrl: URL,
  handoff: AdminDashboardSummary["priorityBanner"]["handoff"],
  state: Pick<AdminDashboardDerivedState, "activeEnvironment" | "activePeriod">,
): string {
  return buildDashboardIncidentHref(currentUrl, handoff, state.activeEnvironment, state.activePeriod);
}

export function resolveAdminDashboardCardTone(severity: string): string {
  switch (severity) {
    case "critical":
      return "border-rose-300 bg-rose-50 text-rose-950 dark:border-rose-800 dark:bg-rose-950 dark:text-rose-100";
    case "warning":
      return "border-amber-300 bg-amber-50 text-amber-950 dark:border-amber-700 dark:bg-amber-950 dark:text-amber-100";
    default:
      return "border-border bg-surface text-text";
  }
}