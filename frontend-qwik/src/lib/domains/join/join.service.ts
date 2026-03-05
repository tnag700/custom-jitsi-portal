import { baseHeaders, mutationHeaders } from "~/lib/domains/meetings/meetings.service";
import type {
  JoinErrorPayload,
  MeetingAccessTokenResponse,
  UpcomingMeetingCard,
} from "./types";

export class JoinServiceError extends Error {
  payload: JoinErrorPayload;

  constructor(payload: JoinErrorPayload) {
    super(payload.detail);
    this.name = "JoinServiceError";
    this.payload = payload;
  }
}

function fallbackJoinErrorCode(status: number): string {
  if (status === 401) return "AUTH_REQUIRED";
  if (status === 403) return "ACCESS_DENIED";
  if (status === 404) return "MEETING_NOT_FOUND";
  if (status === 409) return "MEETING_ENDED";
  if (status >= 500) return "JOIN_SERVICE_UNAVAILABLE";
  return "JOIN_UNKNOWN";
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

export async function adaptJoinProblemDetails(response: Response): Promise<JoinErrorPayload> {
  const problem = await readProblemDetails(response);
  const errorCode =
    typeof problem.errorCode === "string" && problem.errorCode.length > 0
      ? problem.errorCode
      : fallbackJoinErrorCode(response.status);

  return {
    title:
      typeof problem.title === "string" && problem.title.length > 0
        ? problem.title
        : "Ошибка входа во встречу",
    detail:
      typeof problem.detail === "string" && problem.detail.length > 0
        ? problem.detail
        : "Не удалось войти во встречу.",
    errorCode,
    traceId:
      typeof problem.traceId === "string" && problem.traceId.length > 0
        ? problem.traceId
        : undefined,
  };
}

export async function fetchUpcomingMeetings(
  sessionCookie: string,
  apiUrl: string,
): Promise<UpcomingMeetingCard[]> {
  const response = await fetch(`${apiUrl}/meetings/upcoming`, {
    method: "GET",
    headers: baseHeaders(sessionCookie),
  });

  if (!response.ok) {
    throw new JoinServiceError(await adaptJoinProblemDetails(response));
  }

  return (await response.json()) as UpcomingMeetingCard[];
}

export async function issueAccessToken(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
  meetingId: string,
): Promise<MeetingAccessTokenResponse> {
  const response = await fetch(`${apiUrl}/meetings/${encodeURIComponent(meetingId)}/access-token`, {
    method: "POST",
    headers: mutationHeaders(sessionCookie, csrfToken),
  });

  if (!response.ok) {
    throw new JoinServiceError(await adaptJoinProblemDetails(response));
  }

  return (await response.json()) as MeetingAccessTokenResponse;
}
