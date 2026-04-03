import type { SafeUserProfile } from "../auth";
import { sanitizeAdminQueryValue } from "./admin-route-query";
import type {
  AdminDashboardErrorPayload,
  AdminDashboardSummary,
  AdminIncidentCoordination,
  AdminIncidentDetail,
  AdminIncidentList,
  AdminIncidentTicket,
} from "./types";

export interface IncidentSearchFilters {
  traceId: string;
  requestId: string;
  errorCode: string;
  from: string;
  to: string;
  meetingId: string;
}

export interface IncidentQueueFilters {
  period: string;
  environment: string;
  view: string;
  facet: string;
  roomId: string;
  meetingId: string;
  subjectId: string;
  errorCode: string;
  category: string;
  severity: string;
  traceId: string;
  requestId: string;
  from: string;
  to: string;
  limit: string;
  offset: string;
}

export interface IncidentQueueDerivedState {
  selectedEnvironment: string;
  effectiveEnvironment: string;
  activeViewLabel: string;
  activeFacetLabel: string | null;
  advancedFiltersOpen: boolean;
}

const INCIDENT_SEARCH_PARAM_KEYS = ["traceId", "requestId", "errorCode", "from", "to", "meetingId"] as const;
const INCIDENT_QUEUE_ADVANCED_FILTER_KEYS = [
  "roomId",
  "meetingId",
  "subjectId",
  "errorCode",
  "category",
  "severity",
  "traceId",
  "requestId",
  "from",
  "to",
] as const;
const INCIDENT_QUEUE_PATH = "/admin/incidents";
const SECONDARY_MODULE_PATHS = ["/admin/role-history", "/admin/config-sets"] as const;
const ROLE_HISTORY_CONTEXT_KEYS = ["subjectId", "roomId", "meetingId"] as const;
const INCIDENT_MUTATION_CLAIMS = ["role_admin", "admin"] as const;
const COORDINATION_STATUS_LABELS: Record<string, string> = {
  triage: "Triage",
  investigating: "Investigating",
  "waiting-external": "Waiting external",
  resolved: "Resolved",
  "not-enabled": "Deferred",
};

type IncidentRelatedLink = AdminIncidentDetail["relatedLinks"][number];
type IncidentNextAction = AdminIncidentDetail["nextActions"][number];

function buildIncidentQueueBaseQueryUpdates(
  filters: IncidentQueueFilters,
  view: string,
  facet: string | null,
): Record<string, string | null> {
  return {
    period: filters.period,
    environment: filters.environment,
    view,
    facet,
    roomId: null,
    meetingId: null,
    subjectId: null,
    errorCode: null,
    category: null,
    severity: null,
    traceId: null,
    requestId: null,
    from: null,
    to: null,
    offset: "0",
  };
}

function findSavedViewLabel(incidents: AdminIncidentList, token: string): string {
  return incidents.availableViews.find((view) => view.token === token)?.label ?? token;
}

function findQuickFacetLabel(incidents: AdminIncidentList, token: string | null): string | null {
  if (!token) {
    return null;
  }
  return incidents.quickFacets.find((facet) => facet.token === token)?.label ?? token;
}

export interface IncidentDetailDerivedState {
  coordination: AdminIncidentCoordination;
  ticketing: AdminIncidentDetail["ticketing"];
  effectiveTicketReference: string | null;
  effectiveTicketUrl: string | null;
  effectiveTicketStatus: string;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function isAdminDashboardErrorPayload(value: unknown): value is AdminDashboardErrorPayload {
  return isRecord(value)
    && typeof value.title === "string"
    && typeof value.detail === "string"
    && typeof value.errorCode === "string"
    && (value.traceId === undefined || typeof value.traceId === "string");
}

export function buildIncidentQueueFilters(query: URLSearchParams): IncidentQueueFilters {
  return {
    period: sanitizeAdminQueryValue(query.get("period")) || "15m",
    environment: sanitizeAdminQueryValue(query.get("environment")),
    view: sanitizeAdminQueryValue(query.get("view")),
    facet: sanitizeAdminQueryValue(query.get("facet")),
    roomId: sanitizeAdminQueryValue(query.get("roomId")),
    meetingId: sanitizeAdminQueryValue(query.get("meetingId")),
    subjectId: sanitizeAdminQueryValue(query.get("subjectId")),
    errorCode: sanitizeAdminQueryValue(query.get("errorCode")),
    category: sanitizeAdminQueryValue(query.get("category")),
    severity: sanitizeAdminQueryValue(query.get("severity")),
    traceId: sanitizeAdminQueryValue(query.get("traceId")),
    requestId: sanitizeAdminQueryValue(query.get("requestId")),
    from: sanitizeAdminQueryValue(query.get("from")),
    to: sanitizeAdminQueryValue(query.get("to")),
    limit: sanitizeAdminQueryValue(query.get("limit")) || "50",
    offset: sanitizeAdminQueryValue(query.get("offset")) || "0",
  };
}

export function resolveIncidentRelativeTimeLabel(value: string): string {
  return value.trim().length > 0 ? value : "Сводка активности недоступна";
}

export function buildIncidentQueueDerivedState(
  incidents: AdminIncidentList,
  filters: IncidentQueueFilters,
): IncidentQueueDerivedState {
  const selectedEnvironment = filters.environment || incidents.environment;
  const effectiveEnvironment = selectedEnvironment && selectedEnvironment !== "all" ? selectedEnvironment : "";

  return {
    selectedEnvironment,
    effectiveEnvironment,
    activeViewLabel: findSavedViewLabel(incidents, incidents.selectedView),
    activeFacetLabel: findQuickFacetLabel(incidents, incidents.selectedQuickFacet),
    advancedFiltersOpen: INCIDENT_QUEUE_ADVANCED_FILTER_KEYS.some((key) => filters[key].trim().length > 0),
  };
}

export function buildIncidentQueueViewQueryUpdates(
  filters: IncidentQueueFilters,
  view: string,
): Record<string, string | null> {
  return buildIncidentQueueBaseQueryUpdates(filters, view, null);
}

export function buildIncidentQueueFacetQueryUpdates(
  filters: IncidentQueueFilters,
  selectedView: string,
  selectedQuickFacet: string | null,
  facet: string,
): Record<string, string | null> {
  return buildIncidentQueueBaseQueryUpdates(
    filters,
    selectedView,
    facet === selectedQuickFacet ? null : facet,
  );
}

export function buildIncidentQueueResetFiltersQueryUpdates(
  filters: IncidentQueueFilters,
  selectedView: string,
  selectedQuickFacet: string | null,
): Record<string, string | null> {
  return buildIncidentQueueBaseQueryUpdates(filters, selectedView, selectedQuickFacet);
}

export function canManageIncidentTicket(user: SafeUserProfile | null): boolean {
  if (!user) {
    return false;
  }

  return user.claims.some((claim) => INCIDENT_MUTATION_CLAIMS.includes(claim.trim().toLowerCase() as (typeof INCIDENT_MUTATION_CLAIMS)[number]));
}

export function formatIncidentCoordinationStatus(workflowStatus: string): string {
  return COORDINATION_STATUS_LABELS[workflowStatus] ?? workflowStatus;
}

export function buildIncidentMutationAccessDenied(detail: string): AdminDashboardErrorPayload {
  return {
    title: "Недостаточно прав",
    detail,
    errorCode: "ACCESS_DENIED",
  };
}

export function buildIncidentMutationUnexpectedError(
  title: string,
  detail: string,
  errorCode: string,
): AdminDashboardErrorPayload {
  return {
    title,
    detail,
    errorCode,
  };
}

export function getIncidentTicketActionResult(actionValue: unknown): AdminIncidentTicket | null {
  if (!isRecord(actionValue) || actionValue.success !== true || !("ticket" in actionValue)) {
    return null;
  }

  const ticket = actionValue.ticket;
  return isRecord(ticket) ? (ticket as AdminIncidentTicket) : null;
}

export function getIncidentCoordinationActionResult(actionValue: unknown): AdminIncidentCoordination | null {
  if (!isRecord(actionValue) || actionValue.success !== true || !("coordination" in actionValue)) {
    return null;
  }

  const coordination = actionValue.coordination;
  return isRecord(coordination) ? (coordination as AdminIncidentCoordination) : null;
}

export function getIncidentActionError(actionValue: unknown): AdminDashboardErrorPayload | null {
  if (!isRecord(actionValue) || !("error" in actionValue)) {
    return null;
  }

  const error = actionValue.error;
  return isAdminDashboardErrorPayload(error) ? error : null;
}

export function buildIncidentDetailDerivedState(
  incident: AdminIncidentDetail,
  ticketResult: AdminIncidentTicket | null,
  coordinationResult: AdminIncidentCoordination | null,
): IncidentDetailDerivedState {
  const coordination = coordinationResult
    ? coordinationResult
    : incident.coordination.enabled && ticketResult
      ? {
          ...incident.coordination,
          ticketReference: ticketResult.ticketKey,
          ticketStatus: ticketResult.created ? "linked" : incident.coordination.ticketStatus,
          ticketUrl: ticketResult.ticketUrl,
        }
      : incident.coordination;

  const ticketing = ticketResult
    ? {
        available: ticketResult.available,
        ticketKey: ticketResult.ticketKey,
        ticketUrl: ticketResult.ticketUrl,
        status: ticketResult.created ? "created" : incident.ticketing.status,
      }
    : incident.ticketing;

  return {
    coordination,
    ticketing,
    effectiveTicketReference: coordination.ticketReference ?? ticketing.ticketKey,
    effectiveTicketUrl: coordination.ticketUrl ?? ticketing.ticketUrl,
    effectiveTicketStatus: coordinationResult || coordination.ticketReference
      ? coordination.ticketStatus
      : ticketing.status,
  };
}

export function hasIncidentSearchQuery(filters: IncidentSearchFilters): boolean {
  return INCIDENT_SEARCH_PARAM_KEYS.some((key) => filters[key].trim().length > 0);
}

function isSafeReturnTo(value: string | null | undefined): value is string {
  if (!value) {
    return false;
  }
  const trimmed = value.trim();
  return trimmed.startsWith("/") && !trimmed.startsWith("//");
}

function setEnvironmentIfMissing(next: URL, fallbackEnvironment: string): void {
  if (!next.searchParams.has("environment") && fallbackEnvironment.trim().length > 0) {
    next.searchParams.set("environment", fallbackEnvironment);
  }
}

export function buildIncidentQueueReturnHref(currentUrl: URL, fallbackEnvironment = ""): string {
  const next = new URL(INCIDENT_QUEUE_PATH, currentUrl);
  currentUrl.searchParams.forEach((value, key) => {
    if (key === "returnTo") {
      return;
    }
    if (INCIDENT_SEARCH_PARAM_KEYS.includes(key as (typeof INCIDENT_SEARCH_PARAM_KEYS)[number])) {
      return;
    }
    next.searchParams.append(key, value);
  });
  INCIDENT_SEARCH_PARAM_KEYS.forEach((key) => next.searchParams.delete(key));
  setEnvironmentIfMissing(next, fallbackEnvironment);
  return `${next.pathname}${next.search}`;
}

export function resolveIncidentReturnTo(currentUrl: URL, fallbackEnvironment: string): string {
  const returnTo = currentUrl.searchParams.get("returnTo");
  return isSafeReturnTo(returnTo) ? returnTo : buildIncidentQueueReturnHref(currentUrl, fallbackEnvironment);
}

export function buildIncidentDetailHref(currentUrl: URL, incidentId: string, environment: string, returnTo?: string): string {
  const next = new URL(`/admin/incidents/${incidentId}`, currentUrl);
  if (environment.trim().length > 0) {
    next.searchParams.set("environment", environment);
  }
  next.searchParams.set("returnTo", isSafeReturnTo(returnTo) ? returnTo : buildIncidentQueueReturnHref(currentUrl, environment));
  return `${next.pathname}${next.search}`;
}

function setIfPresent(next: URL, key: string, value: string | null): void {
  if (value && value.trim().length > 0) {
    next.searchParams.set(key, value);
  }
}

function buildCurrentRouteHref(currentUrl: URL): string {
  return `${currentUrl.pathname}${currentUrl.search}`;
}

function resolveSecondaryModuleReturnTo(currentUrl: URL, fallbackEnvironment: string): string {
  return SECONDARY_MODULE_PATHS.includes(currentUrl.pathname as (typeof SECONDARY_MODULE_PATHS)[number])
    ? resolveIncidentReturnTo(currentUrl, fallbackEnvironment)
    : buildCurrentRouteHref(currentUrl);
}

export function buildAdminSecondaryHref(currentUrl: URL, targetPath: string, fallbackEnvironment: string): string {
  const next = new URL(targetPath, currentUrl);
  setEnvironmentIfMissing(next, fallbackEnvironment);

  if (targetPath === "/admin/role-history") {
    ROLE_HISTORY_CONTEXT_KEYS.forEach((key) => setIfPresent(next, key, currentUrl.searchParams.get(key)));
  }

  next.searchParams.set("returnTo", resolveSecondaryModuleReturnTo(currentUrl, fallbackEnvironment));
  return `${next.pathname}${next.search}`;
}

export function buildIncidentRelatedHref(currentUrl: URL, link: IncidentRelatedLink, fallbackEnvironment: string): string | null {
  if (link.externalUrl) {
    return link.externalUrl;
  }

  if (link.kind === "role-history") {
    const next = new URL("/admin/role-history", currentUrl);
    setIfPresent(next, "environment", link.environment ?? fallbackEnvironment);
    setIfPresent(next, "subjectId", link.subjectId);
    setIfPresent(next, "roomId", link.roomId);
    setIfPresent(next, "meetingId", link.meetingId);
    next.searchParams.set("returnTo", `${currentUrl.pathname}${currentUrl.search}`);
    return `${next.pathname}${next.search}`;
  }

  if (link.kind === "incident-scope") {
    const next = new URL("/admin/incidents", currentUrl);
    setIfPresent(next, "environment", link.environment ?? fallbackEnvironment);
    setIfPresent(next, "roomId", link.roomId);
    setIfPresent(next, "meetingId", link.meetingId);
    setIfPresent(next, "subjectId", link.subjectId);
    next.searchParams.set("returnTo", `${currentUrl.pathname}${currentUrl.search}`);
    return `${next.pathname}${next.search}`;
  }

  return null;
}

export function buildIncidentNextActionHref(
  currentUrl: URL,
  action: IncidentNextAction,
  relatedLinks: AdminIncidentDetail["relatedLinks"],
  fallbackEnvironment: string,
): string | null {
  if (action.externalUrl) {
    return action.externalUrl;
  }

  if (action.target === "queue-return") {
    return resolveIncidentReturnTo(currentUrl, fallbackEnvironment);
  }

  if (action.target === "role-history") {
    const link = relatedLinks.find((item) => item.kind === "role-history");
    return link ? buildIncidentRelatedHref(currentUrl, link, fallbackEnvironment) : null;
  }

  return null;
}

export function buildIncidentEmptyStateHref(
  currentUrl: URL,
  target: string,
  relatedLinks: AdminIncidentDetail["relatedLinks"],
  fallbackEnvironment: string,
): string | null {
  if (target === "queue-return") {
    return resolveIncidentReturnTo(currentUrl, fallbackEnvironment);
  }

  if (target === "role-history") {
    const link = relatedLinks.find((item) => item.kind === "role-history");
    return link ? buildIncidentRelatedHref(currentUrl, link, fallbackEnvironment) : null;
  }

  return null;
}

export function buildDashboardIncidentHref(
  currentUrl: URL,
  handoff: AdminDashboardSummary["priorityBanner"]["handoff"],
  fallbackEnvironment: string,
  fallbackPeriod: string,
): string {
  const next = new URL(handoff.incidentId ? `/admin/incidents/${handoff.incidentId}` : "/admin/incidents", currentUrl);
  next.searchParams.set("environment", handoff.environment || fallbackEnvironment);
  next.searchParams.set("period", handoff.period || fallbackPeriod);
  if (!handoff.incidentId && handoff.severity.trim().toLowerCase() === "critical") {
    next.searchParams.set("view", "critical");
  }
  if (handoff.severity) {
    next.searchParams.set("severity", handoff.severity);
  }
  if (handoff.errorCode) {
    next.searchParams.set("errorCode", handoff.errorCode);
  }
  if (handoff.category) {
    next.searchParams.set("category", handoff.category);
  }
  if (handoff.roomId) {
    next.searchParams.set("roomId", handoff.roomId);
  }
  if (handoff.meetingId) {
    next.searchParams.set("meetingId", handoff.meetingId);
  }
  if (handoff.incidentId) {
    next.searchParams.set("returnTo", `${currentUrl.pathname}${currentUrl.search}`);
  }
  return `${next.pathname}${next.search}`;
}