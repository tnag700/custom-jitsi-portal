import type {
  JoinReadinessPayload,
  JoinErrorPayload,
  MeetingAccessTokenResponse,
  UpcomingMeetingCard,
} from "./types";
import {
  createApiClient,
  adaptProblemDetails,
  joinReadinessResponseSchema,
  meetingAccessTokenResponseSchema,
  upcomingMeetingCardSchema,
} from "../../shared/api";
import type {
  MutationRequestContext,
  ServerRequestContext,
} from "../../shared/routes/server-handlers";
import { asMutationRequestContext, asServerRequestContext } from "../../shared/routes/server-handlers";

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

function parseOrThrow<T>(parseFn: (d: unknown) => T, data: unknown, endpoint: string): T {
  try {
    return parseFn(data);
  } catch (e) {
    throw new JoinServiceError({
      title: "Неожиданный формат ответа",
      detail: `${endpoint}: ${e instanceof Error ? e.message : "неверный формат ответа"}`,
      errorCode: "JOIN_RESPONSE_INVALID",
    });
  }
}

export async function adaptJoinProblemDetails(response: Response): Promise<JoinErrorPayload> {
  return adaptProblemDetails(
    response,
    response.status,
    fallbackJoinErrorCode,
    "Ошибка входа во встречу",
    "Не удалось войти во встречу.",
  );
}

export function fetchUpcomingMeetings(context: ServerRequestContext): Promise<UpcomingMeetingCard[]>;
export function fetchUpcomingMeetings(sessionCookie: string, apiUrl: string): Promise<UpcomingMeetingCard[]>;
export async function fetchUpcomingMeetings(
  contextOrSessionCookie: ServerRequestContext | string,
  apiUrl?: string,
): Promise<UpcomingMeetingCard[]> {
  const context = asServerRequestContext(contextOrSessionCookie, apiUrl);
  const client = createApiClient(context.apiUrl);
  const { data, error, response } = await client.GET("/api/v1/meetings/upcoming", {
    headers: context.headers,
  });

  if (!response.ok || error) {
    throw new JoinServiceError(
      await adaptProblemDetails(
        error ?? response,
        response.status,
        fallbackJoinErrorCode,
        "Ошибка входа во встречу",
        "Не удалось войти во встречу.",
      ),
    );
  }

  return parseOrThrow((d) => upcomingMeetingCardSchema.array().parse(d), data, "GET /api/v1/meetings/upcoming");
}

export function fetchJoinReadiness(context: ServerRequestContext): Promise<JoinReadinessPayload>;
export function fetchJoinReadiness(apiUrl: string): Promise<JoinReadinessPayload>;
export async function fetchJoinReadiness(
  contextOrApiUrl: ServerRequestContext | string,
): Promise<JoinReadinessPayload> {
  const context = typeof contextOrApiUrl === "string"
    ? { apiUrl: contextOrApiUrl, headers: {} }
    : contextOrApiUrl;
  const response = await fetch(`${context.apiUrl}/health/join-readiness`, {
    method: "GET",
    headers: context.headers,
  });

  if (!response.ok) {
    throw new JoinServiceError(
      await adaptProblemDetails(
        response,
        response.status,
        fallbackJoinErrorCode,
        "Ошибка preflight перед входом",
        "Не удалось получить готовность к входу.",
      ),
    );
  }

  const data = await response.json();
  return parseOrThrow((payload) => joinReadinessResponseSchema.parse(payload), data, "GET /api/v1/health/join-readiness");
}

export function issueAccessToken(context: MutationRequestContext, meetingId: string): Promise<MeetingAccessTokenResponse>;
export function issueAccessToken(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
  meetingId: string,
): Promise<MeetingAccessTokenResponse>;
export async function issueAccessToken(
  contextOrSessionCookie: MutationRequestContext | string,
  apiUrlOrMeetingId: string,
  csrfToken?: string,
  meetingId?: string,
): Promise<MeetingAccessTokenResponse> {
  const context = asMutationRequestContext(
    contextOrSessionCookie,
    typeof contextOrSessionCookie === "string" ? apiUrlOrMeetingId : undefined,
    csrfToken,
  );
  const resolvedMeetingId = typeof contextOrSessionCookie === "string" ? meetingId ?? "" : apiUrlOrMeetingId;
  const client = createApiClient(context.apiUrl);
  const { data, error, response } = await client.POST("/api/v1/meetings/{meetingId}/access-token", {
    headers: context.headers,
    params: { path: { meetingId: resolvedMeetingId } },
  });

  if (!response.ok || error) {
    throw new JoinServiceError(
      await adaptProblemDetails(
        error ?? response,
        response.status,
        fallbackJoinErrorCode,
        "Ошибка входа во встречу",
        "Не удалось войти во встречу.",
      ),
    );
  }

  return parseOrThrow((d) => meetingAccessTokenResponseSchema.parse(d), data, "POST /api/v1/meetings/{meetingId}/access-token");
}
