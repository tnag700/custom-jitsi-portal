import {
  fetchWithTimeout,
  participantAssignmentResponseSchema,
  userProfileSummaryResponseSchema,
} from "../../shared/api";
import {
  adaptMeetingProblemDetails,
  MeetingServiceError,
  parseOrThrow,
} from "./meetings.service";
import type {
  MutationRequestContext,
  ServerRequestContext,
} from "../../shared/routes/server-handlers";
import type {
  AssignParticipantRequest,
  BulkAssignParticipantsRequest,
  ParticipantAssignment,
  UpdateParticipantRoleRequest,
  UserProfileSummary,
} from "./types";

export async function fetchParticipants(
  context: ServerRequestContext,
  meetingId: string,
): Promise<ParticipantAssignment[]> {
  const response = await fetchWithTimeout(`${context.apiUrl}/meetings/${encodeURIComponent(meetingId)}/participants`, {
    method: "GET",
    headers: context.headers,
  });

  if (!response.ok) {
    throw new MeetingServiceError(await adaptMeetingProblemDetails(response));
  }

  return parseOrThrow(
    (data) => participantAssignmentResponseSchema.array().parse(data),
    await response.json(),
    "GET /api/v1/meetings/{meetingId}/participants",
  );
}

export async function assignParticipant(
  context: MutationRequestContext,
  meetingId: string,
  request: AssignParticipantRequest,
): Promise<ParticipantAssignment> {
  const response = await fetchWithTimeout(`${context.apiUrl}/meetings/${encodeURIComponent(meetingId)}/participants`, {
    method: "POST",
    headers: context.headers,
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw new MeetingServiceError(await adaptMeetingProblemDetails(response));
  }

  return parseOrThrow(
    (data) => participantAssignmentResponseSchema.parse(data),
    await response.json(),
    "POST /api/v1/meetings/{meetingId}/participants",
  );
}

export async function bulkAssignParticipants(
  context: MutationRequestContext,
  meetingId: string,
  request: BulkAssignParticipantsRequest,
): Promise<ParticipantAssignment[]> {
  const response = await fetchWithTimeout(`${context.apiUrl}/meetings/${encodeURIComponent(meetingId)}/participants/bulk`, {
    method: "POST",
    headers: context.headers,
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw new MeetingServiceError(await adaptMeetingProblemDetails(response));
  }

  return parseOrThrow(
    (data) => participantAssignmentResponseSchema.array().parse(data),
    await response.json(),
    "POST /api/v1/meetings/{meetingId}/participants/bulk",
  );
}

export async function searchUsers(
  context: ServerRequestContext,
  tenantId: string,
  query?: string,
  organization?: string,
): Promise<UserProfileSummary[]> {
  const params = new URLSearchParams({ tenant_id: tenantId });
  if (query && query.trim().length > 0) {
    params.set("q", query.trim());
  }
  if (organization && organization.trim().length > 0) {
    params.set("organization", organization.trim());
  }

  const response = await fetchWithTimeout(`${context.apiUrl}/users/search?${params.toString()}`, {
    method: "GET",
    headers: context.headers,
  });

  if (!response.ok) {
    throw new MeetingServiceError(await adaptMeetingProblemDetails(response));
  }

  return parseOrThrow(
    (data) => userProfileSummaryResponseSchema.array().parse(data),
    await response.json(),
    "GET /api/v1/users/search",
  );
}

export async function updateParticipantRole(
  context: MutationRequestContext,
  meetingId: string,
  subjectId: string,
  request: UpdateParticipantRoleRequest,
): Promise<ParticipantAssignment> {
  const response = await fetchWithTimeout(
    `${context.apiUrl}/meetings/${encodeURIComponent(meetingId)}/participants/${encodeURIComponent(subjectId)}`,
    {
      method: "PUT",
      headers: context.headers,
      body: JSON.stringify(request),
    },
  );

  if (!response.ok) {
    throw new MeetingServiceError(await adaptMeetingProblemDetails(response));
  }

  return parseOrThrow(
    (data) => participantAssignmentResponseSchema.parse(data),
    await response.json(),
    "PUT /api/v1/meetings/{meetingId}/participants/{subjectId}",
  );
}

export async function unassignParticipant(
  context: MutationRequestContext,
  meetingId: string,
  subjectId: string,
): Promise<void> {
  const response = await fetchWithTimeout(
    `${context.apiUrl}/meetings/${encodeURIComponent(meetingId)}/participants/${encodeURIComponent(subjectId)}`,
    {
      method: "DELETE",
      headers: context.headers,
    },
  );

  if (!response.ok) {
    throw new MeetingServiceError(await adaptMeetingProblemDetails(response));
  }
}
