import type {
  CreateMeetingRequest,
  Meeting,
  MeetingErrorPayload,
  PagedMeetingResponse,
  UpdateMeetingRequest,
} from "./types";

export class MeetingServiceError extends Error {
  payload: MeetingErrorPayload;

  constructor(payload: MeetingErrorPayload) {
    super(payload.detail);
    this.name = "MeetingServiceError";
    this.payload = payload;
  }
}

function fallbackErrorCode(status: number): string {
  if (status === 400) return "INVALID_SCHEDULE";
  if (status === 404) return "MEETING_NOT_FOUND";
  if (status === 409) return "MEETING_FINALIZED";
  if (status >= 500) return "MEETING_SERVICE_UNAVAILABLE";
  return "MEETING_UNKNOWN";
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

export async function adaptMeetingProblemDetails(response: Response): Promise<MeetingErrorPayload> {
  const problem = await readProblemDetails(response);
  const errorCode =
    typeof problem.errorCode === "string" && problem.errorCode.length > 0
      ? problem.errorCode
      : fallbackErrorCode(response.status);

  return {
    title:
      typeof problem.title === "string" && problem.title.length > 0
        ? problem.title
        : "Ошибка операции со встречей",
    detail:
      typeof problem.detail === "string" && problem.detail.length > 0
        ? problem.detail
        : "Не удалось выполнить операцию.",
    errorCode,
    traceId:
      typeof problem.traceId === "string" && problem.traceId.length > 0
        ? problem.traceId
        : undefined,
  };
}

export function baseHeaders(sessionCookie: string): Record<string, string> {
  return {
    Cookie: `JSESSIONID=${sessionCookie}`,
    "Content-Type": "application/json",
  };
}

export function mutationHeaders(
  sessionCookie: string,
  csrfToken: string,
  idempotencyKey?: string,
): Record<string, string> {
  const headers: Record<string, string> = {
    ...baseHeaders(sessionCookie),
    "X-XSRF-TOKEN": csrfToken,
  };

  if (idempotencyKey) {
    headers["Idempotency-Key"] = idempotencyKey;
  }

  return { ...headers };
}

export async function fetchMeetings(
  sessionCookie: string,
  apiUrl: string,
  roomId: string,
  page = 0,
  size = 20,
): Promise<PagedMeetingResponse> {
  const url = `${apiUrl}/rooms/${encodeURIComponent(roomId)}/meetings?page=${page}&size=${size}`;
  const response = await fetch(url, {
    method: "GET",
    headers: baseHeaders(sessionCookie),
  });

  if (!response.ok) {
    throw new MeetingServiceError(await adaptMeetingProblemDetails(response));
  }

  return (await response.json()) as PagedMeetingResponse;
}

export async function createMeeting(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
  idempotencyKey: string,
  roomId: string,
  request: CreateMeetingRequest,
): Promise<Meeting> {
  const response = await fetch(`${apiUrl}/rooms/${encodeURIComponent(roomId)}/meetings`, {
    method: "POST",
    headers: mutationHeaders(sessionCookie, csrfToken, idempotencyKey),
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw new MeetingServiceError(await adaptMeetingProblemDetails(response));
  }

  return (await response.json()) as Meeting;
}

export async function updateMeeting(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
  meetingId: string,
  request: UpdateMeetingRequest,
): Promise<Meeting> {
  const response = await fetch(`${apiUrl}/meetings/${encodeURIComponent(meetingId)}`, {
    method: "PUT",
    headers: mutationHeaders(sessionCookie, csrfToken),
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw new MeetingServiceError(await adaptMeetingProblemDetails(response));
  }

  return (await response.json()) as Meeting;
}

export async function cancelMeeting(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
  meetingId: string,
): Promise<Meeting> {
  const response = await fetch(`${apiUrl}/meetings/${encodeURIComponent(meetingId)}/cancel`, {
    method: "POST",
    headers: mutationHeaders(sessionCookie, csrfToken),
  });

  if (!response.ok) {
    throw new MeetingServiceError(await adaptMeetingProblemDetails(response));
  }

  return (await response.json()) as Meeting;
}
