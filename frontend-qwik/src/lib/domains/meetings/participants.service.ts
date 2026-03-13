import {
  adaptMeetingProblemDetails,
  MeetingServiceError,
} from "./meetings.service";
import type {
  MutationRequestContext,
  ServerRequestContext,
} from "../../shared/routes/server-handlers";
import { asMutationRequestContext, asServerRequestContext } from "../../shared/routes/server-handlers";
import type {
  AssignParticipantRequest,
  BulkAssignParticipantsRequest,
  ParticipantAssignment,
  UpdateParticipantRoleRequest,
  UserProfileSummary,
} from "./types";

export function fetchParticipants(
  context: ServerRequestContext,
  meetingId: string,
): Promise<ParticipantAssignment[]>;
export function fetchParticipants(
  sessionCookie: string,
  apiUrl: string,
  meetingId: string,
): Promise<ParticipantAssignment[]>;
export async function fetchParticipants(
  contextOrSessionCookie: ServerRequestContext | string,
  apiUrlOrMeetingId: string,
  meetingId?: string,
): Promise<ParticipantAssignment[]> {
  const context = asServerRequestContext(
    contextOrSessionCookie,
    typeof contextOrSessionCookie === "string" ? apiUrlOrMeetingId : undefined,
  );
  const resolvedMeetingId = typeof contextOrSessionCookie === "string" ? meetingId! : apiUrlOrMeetingId;

  const response = await fetch(`${context.apiUrl}/meetings/${encodeURIComponent(resolvedMeetingId)}/participants`, {
    method: "GET",
    headers: context.headers,
  });

  if (!response.ok) {
    throw new MeetingServiceError(await adaptMeetingProblemDetails(response));
  }

  return (await response.json()) as ParticipantAssignment[];
}

export function assignParticipant(
  context: MutationRequestContext,
  meetingId: string,
  request: AssignParticipantRequest,
): Promise<ParticipantAssignment>;
export function assignParticipant(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
  idempotencyKey: string,
  meetingId: string,
  request: AssignParticipantRequest,
): Promise<ParticipantAssignment>;
export async function assignParticipant(
  contextOrSessionCookie: MutationRequestContext | string,
  apiUrlOrMeetingId: string,
  csrfTokenOrRequest?: string | AssignParticipantRequest,
  idempotencyKey?: string,
  meetingId?: string,
  request?: AssignParticipantRequest,
): Promise<ParticipantAssignment> {
  const context = asMutationRequestContext(
    contextOrSessionCookie,
    typeof contextOrSessionCookie === "string" ? apiUrlOrMeetingId : undefined,
    typeof csrfTokenOrRequest === "string" ? csrfTokenOrRequest : undefined,
    idempotencyKey,
  );
  const resolvedMeetingId = typeof contextOrSessionCookie === "string" ? meetingId! : apiUrlOrMeetingId;
  const resolvedRequest = typeof contextOrSessionCookie === "string" ? request! : (csrfTokenOrRequest as AssignParticipantRequest);

  const response = await fetch(`${context.apiUrl}/meetings/${encodeURIComponent(resolvedMeetingId)}/participants`, {
    method: "POST",
    headers: context.headers,
    body: JSON.stringify(resolvedRequest),
  });

  if (!response.ok) {
    throw new MeetingServiceError(await adaptMeetingProblemDetails(response));
  }

  return (await response.json()) as ParticipantAssignment;
}

export function bulkAssignParticipants(
  context: MutationRequestContext,
  meetingId: string,
  request: BulkAssignParticipantsRequest,
): Promise<ParticipantAssignment[]>;
export function bulkAssignParticipants(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
  idempotencyKey: string,
  meetingId: string,
  request: BulkAssignParticipantsRequest,
): Promise<ParticipantAssignment[]>;
export async function bulkAssignParticipants(
  contextOrSessionCookie: MutationRequestContext | string,
  apiUrlOrMeetingId: string,
  csrfTokenOrRequest?: string | BulkAssignParticipantsRequest,
  idempotencyKey?: string,
  meetingId?: string,
  request?: BulkAssignParticipantsRequest,
): Promise<ParticipantAssignment[]> {
  const context = asMutationRequestContext(
    contextOrSessionCookie,
    typeof contextOrSessionCookie === "string" ? apiUrlOrMeetingId : undefined,
    typeof csrfTokenOrRequest === "string" ? csrfTokenOrRequest : undefined,
    idempotencyKey,
  );
  const resolvedMeetingId = typeof contextOrSessionCookie === "string" ? meetingId! : apiUrlOrMeetingId;
  const resolvedRequest =
    typeof contextOrSessionCookie === "string"
      ? request!
      : (csrfTokenOrRequest as BulkAssignParticipantsRequest);

  const response = await fetch(`${context.apiUrl}/meetings/${encodeURIComponent(resolvedMeetingId)}/participants/bulk`, {
    method: "POST",
    headers: context.headers,
    body: JSON.stringify(resolvedRequest),
  });

  if (!response.ok) {
    throw new MeetingServiceError(await adaptMeetingProblemDetails(response));
  }

  return (await response.json()) as ParticipantAssignment[];
}

export function searchUsers(
  context: ServerRequestContext,
  tenantId: string,
  query?: string,
  organization?: string,
): Promise<UserProfileSummary[]>;
export function searchUsers(
  sessionCookie: string,
  apiUrl: string,
  tenantId: string,
  query?: string,
  organization?: string,
): Promise<UserProfileSummary[]>;
export async function searchUsers(
  contextOrSessionCookie: ServerRequestContext | string,
  apiUrlOrTenantId: string,
  tenantIdOrQuery?: string,
  query?: string,
  organization?: string,
): Promise<UserProfileSummary[]> {
  const context = asServerRequestContext(
    contextOrSessionCookie,
    typeof contextOrSessionCookie === "string" ? apiUrlOrTenantId : undefined,
  );

  const tenantId = typeof contextOrSessionCookie === "string" ? tenantIdOrQuery! : apiUrlOrTenantId;
  const resolvedQuery = typeof contextOrSessionCookie === "string" ? query : tenantIdOrQuery;
  const resolvedOrganization = typeof contextOrSessionCookie === "string" ? organization : query;

  const params = new URLSearchParams({ tenant_id: tenantId });
  if (resolvedQuery && resolvedQuery.trim().length > 0) {
    params.set("q", resolvedQuery.trim());
  }
  if (resolvedOrganization && resolvedOrganization.trim().length > 0) {
    params.set("organization", resolvedOrganization.trim());
  }

  const response = await fetch(`${context.apiUrl}/users/search?${params.toString()}`, {
    method: "GET",
    headers: context.headers,
  });

  if (!response.ok) {
    throw new MeetingServiceError(await adaptMeetingProblemDetails(response));
  }

  return (await response.json()) as UserProfileSummary[];
}

export function updateParticipantRole(
  context: MutationRequestContext,
  meetingId: string,
  subjectId: string,
  request: UpdateParticipantRoleRequest,
): Promise<ParticipantAssignment>;
export function updateParticipantRole(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
  meetingId: string,
  subjectId: string,
  request: UpdateParticipantRoleRequest,
): Promise<ParticipantAssignment>;
export async function updateParticipantRole(
  contextOrSessionCookie: MutationRequestContext | string,
  apiUrlOrMeetingId: string,
  csrfTokenOrSubjectId?: string,
  meetingIdOrRequest?: string | UpdateParticipantRoleRequest,
  subjectId?: string,
  request?: UpdateParticipantRoleRequest,
): Promise<ParticipantAssignment> {
  const context = asMutationRequestContext(
    contextOrSessionCookie,
    typeof contextOrSessionCookie === "string" ? apiUrlOrMeetingId : undefined,
    typeof csrfTokenOrSubjectId === "string" ? csrfTokenOrSubjectId : undefined,
  );
  const resolvedMeetingId = typeof contextOrSessionCookie === "string" ? (meetingIdOrRequest as string) : apiUrlOrMeetingId;
  const resolvedSubjectId = typeof contextOrSessionCookie === "string" ? subjectId! : (csrfTokenOrSubjectId as string);
  const resolvedRequest = typeof contextOrSessionCookie === "string" ? request! : (meetingIdOrRequest as UpdateParticipantRoleRequest);

  const response = await fetch(
    `${context.apiUrl}/meetings/${encodeURIComponent(resolvedMeetingId)}/participants/${encodeURIComponent(resolvedSubjectId)}`,
    {
      method: "PUT",
      headers: context.headers,
      body: JSON.stringify(resolvedRequest),
    },
  );

  if (!response.ok) {
    throw new MeetingServiceError(await adaptMeetingProblemDetails(response));
  }

  return (await response.json()) as ParticipantAssignment;
}

export function unassignParticipant(
  context: MutationRequestContext,
  meetingId: string,
  subjectId: string,
): Promise<void>;
export function unassignParticipant(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
  meetingId: string,
  subjectId: string,
): Promise<void>;
export async function unassignParticipant(
  contextOrSessionCookie: MutationRequestContext | string,
  apiUrlOrMeetingId: string,
  csrfTokenOrSubjectId?: string,
  meetingId?: string,
  subjectId?: string,
): Promise<void> {
  const context = asMutationRequestContext(
    contextOrSessionCookie,
    typeof contextOrSessionCookie === "string" ? apiUrlOrMeetingId : undefined,
    typeof csrfTokenOrSubjectId === "string" ? csrfTokenOrSubjectId : undefined,
  );
  const resolvedMeetingId = typeof contextOrSessionCookie === "string" ? meetingId! : apiUrlOrMeetingId;
  const resolvedSubjectId = typeof contextOrSessionCookie === "string" ? subjectId! : (csrfTokenOrSubjectId as string);

  const response = await fetch(
    `${context.apiUrl}/meetings/${encodeURIComponent(resolvedMeetingId)}/participants/${encodeURIComponent(resolvedSubjectId)}`,
    {
      method: "DELETE",
      headers: context.headers,
    },
  );

  if (!response.ok) {
    throw new MeetingServiceError(await adaptMeetingProblemDetails(response));
  }
}
