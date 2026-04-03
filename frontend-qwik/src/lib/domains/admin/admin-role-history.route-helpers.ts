import { sanitizeAdminQueryValue } from "./admin-route-query";

export interface AdminRoleHistoryFilters {
  environment: string;
  q: string;
  from: string;
  to: string;
  actionType: string;
  role: string;
  actorId: string;
  subjectId: string;
  roomId: string;
  meetingId: string;
  page: string;
  pageSize: string;
  returnTo: string;
}

export function buildAdminRoleHistoryFilters(query: URLSearchParams): AdminRoleHistoryFilters {
  return {
    environment: sanitizeAdminQueryValue(query.get("environment")),
    q: sanitizeAdminQueryValue(query.get("q")),
    from: sanitizeAdminQueryValue(query.get("from")),
    to: sanitizeAdminQueryValue(query.get("to")),
    actionType: sanitizeAdminQueryValue(query.get("actionType")),
    role: sanitizeAdminQueryValue(query.get("role")),
    actorId: sanitizeAdminQueryValue(query.get("actorId")),
    subjectId: sanitizeAdminQueryValue(query.get("subjectId")),
    roomId: sanitizeAdminQueryValue(query.get("roomId")),
    meetingId: sanitizeAdminQueryValue(query.get("meetingId")),
    page: sanitizeAdminQueryValue(query.get("page")) || "0",
    pageSize: sanitizeAdminQueryValue(query.get("pageSize")) || "20",
    returnTo: sanitizeAdminQueryValue(query.get("returnTo")),
  };
}

export function hasAdminRoleHistoryPrimaryFilter(filters: AdminRoleHistoryFilters): boolean {
  return Boolean(filters.q || filters.subjectId || filters.roomId || filters.meetingId);
}

export function buildAdminRoleHistoryResetQueryUpdates(): Record<string, string | null> {
  return {
    q: null,
    subjectId: null,
    actorId: null,
    roomId: null,
    meetingId: null,
    environment: null,
    actionType: null,
    role: null,
    from: null,
    to: null,
    page: "0",
  };
}

export function buildAdminRoleHistoryPageQueryUpdates(page: number): Record<string, string | null> {
  return {
    page: String(page),
  };
}