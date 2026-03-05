import {
  adaptMeetingProblemDetails,
  baseHeaders,
  mutationHeaders,
  MeetingServiceError,
} from "./meetings.service";
import type {
  AssignParticipantRequest,
  ParticipantAssignment,
  UpdateParticipantRoleRequest,
} from "./types";

export async function fetchParticipants(
  sessionCookie: string,
  apiUrl: string,
  meetingId: string,
): Promise<ParticipantAssignment[]> {
  const response = await fetch(`${apiUrl}/meetings/${encodeURIComponent(meetingId)}/participants`, {
    method: "GET",
    headers: baseHeaders(sessionCookie),
  });

  if (!response.ok) {
    throw new MeetingServiceError(await adaptMeetingProblemDetails(response));
  }

  return (await response.json()) as ParticipantAssignment[];
}

export async function assignParticipant(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
  idempotencyKey: string,
  meetingId: string,
  request: AssignParticipantRequest,
): Promise<ParticipantAssignment> {
  const response = await fetch(`${apiUrl}/meetings/${encodeURIComponent(meetingId)}/participants`, {
    method: "POST",
    headers: mutationHeaders(sessionCookie, csrfToken, idempotencyKey),
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw new MeetingServiceError(await adaptMeetingProblemDetails(response));
  }

  return (await response.json()) as ParticipantAssignment;
}

export async function updateParticipantRole(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
  meetingId: string,
  subjectId: string,
  request: UpdateParticipantRoleRequest,
): Promise<ParticipantAssignment> {
  const response = await fetch(
    `${apiUrl}/meetings/${encodeURIComponent(meetingId)}/participants/${encodeURIComponent(subjectId)}`,
    {
      method: "PUT",
      headers: mutationHeaders(sessionCookie, csrfToken),
      body: JSON.stringify(request),
    },
  );

  if (!response.ok) {
    throw new MeetingServiceError(await adaptMeetingProblemDetails(response));
  }

  return (await response.json()) as ParticipantAssignment;
}

export async function unassignParticipant(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
  meetingId: string,
  subjectId: string,
): Promise<void> {
  const response = await fetch(
    `${apiUrl}/meetings/${encodeURIComponent(meetingId)}/participants/${encodeURIComponent(subjectId)}`,
    {
      method: "DELETE",
      headers: mutationHeaders(sessionCookie, csrfToken),
    },
  );

  if (!response.ok) {
    throw new MeetingServiceError(await adaptMeetingProblemDetails(response));
  }
}
