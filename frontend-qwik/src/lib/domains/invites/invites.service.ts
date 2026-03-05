import { baseHeaders, mutationHeaders } from "~/lib/domains/meetings/meetings.service";
import type {
  CreateInviteRequest,
  Invite,
  InviteErrorPayload,
  PagedInviteResponse,
} from "./types";

export class InviteServiceError extends Error {
  payload: InviteErrorPayload;

  constructor(payload: InviteErrorPayload) {
    super(payload.detail);
    this.name = "InviteServiceError";
    this.payload = payload;
  }
}

function fallbackErrorCode(status: number): string {
  if (status === 400) return "VALIDATION_ERROR";
  if (status === 404) return "INVITE_NOT_FOUND";
  if (status === 409) return "INVITE_ALREADY_REVOKED";
  if (status === 410) return "INVITE_EXPIRED";
  if (status >= 500) return "INVITE_SERVICE_UNAVAILABLE";
  return "INVITE_UNKNOWN";
}

interface ProblemDetailsLike {
  title?: unknown;
  detail?: unknown;
  errorCode?: unknown;
  traceId?: unknown;
}

async function readProblemDetails(response: Response): Promise<ProblemDetailsLike> {
  try {
    return (await response.json()) as ProblemDetailsLike;
  } catch {
    return {};
  }
}

export async function adaptInviteProblemDetails(response: Response): Promise<InviteErrorPayload> {
  const problem = await readProblemDetails(response);
  const errorCode =
    typeof problem.errorCode === "string" && problem.errorCode.length > 0
      ? problem.errorCode
      : fallbackErrorCode(response.status);

  return {
    title:
      typeof problem.title === "string" && problem.title.length > 0
        ? problem.title
        : "Ошибка операции с инвайтом",
    detail:
      typeof problem.detail === "string" && problem.detail.length > 0
        ? problem.detail
        : "Не удалось выполнить операцию с инвайтом.",
    errorCode,
    traceId:
      typeof problem.traceId === "string" && problem.traceId.length > 0
        ? problem.traceId
        : undefined,
  };
}

export async function fetchInvites(
  sessionCookie: string,
  apiUrl: string,
  meetingId: string,
  page = 0,
  size = 20,
): Promise<PagedInviteResponse> {
  const url = `${apiUrl}/meetings/${encodeURIComponent(meetingId)}/invites?page=${page}&size=${size}`;
  const response = await fetch(url, {
    method: "GET",
    headers: baseHeaders(sessionCookie),
  });

  if (!response.ok) {
    throw new InviteServiceError(await adaptInviteProblemDetails(response));
  }

  return (await response.json()) as PagedInviteResponse;
}

export async function createInvite(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
  idempotencyKey: string,
  meetingId: string,
  request: CreateInviteRequest,
): Promise<Invite> {
  const response = await fetch(`${apiUrl}/meetings/${encodeURIComponent(meetingId)}/invites`, {
    method: "POST",
    headers: mutationHeaders(sessionCookie, csrfToken, idempotencyKey),
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw new InviteServiceError(await adaptInviteProblemDetails(response));
  }

  return (await response.json()) as Invite;
}

export async function revokeInvite(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
  meetingId: string,
  inviteId: string,
): Promise<void> {
  const response = await fetch(
    `${apiUrl}/meetings/${encodeURIComponent(meetingId)}/invites/${encodeURIComponent(inviteId)}`,
    {
      method: "DELETE",
      headers: mutationHeaders(sessionCookie, csrfToken),
    },
  );

  if (!response.ok) {
    throw new InviteServiceError(await adaptInviteProblemDetails(response));
  }
}
