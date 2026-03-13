import type {
  CreateMeetingRequest,
  Meeting,
  MeetingErrorPayload,
  PagedMeetingResponse,
  UpdateMeetingRequest,
} from "./types";
import {
  createApiClient,
  adaptProblemDetails,
  meetingResponseSchema,
  pagedMeetingResponseSchema,
} from "../../shared/api";
import type {
  MutationRequestContext,
  ServerRequestContext,
} from "../../shared/routes/server-handlers";
import { asMutationRequestContext, asServerRequestContext } from "../../shared/routes/server-handlers";

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

function parseOrThrow<T>(parseFn: (d: unknown) => T, data: unknown, endpoint: string): T {
  try {
    return parseFn(data);
  } catch (e) {
    throw new MeetingServiceError({
      title: "Неожиданный формат ответа",
      detail: `${endpoint}: ${e instanceof Error ? e.message : "неверный формат ответа"}`,
      errorCode: "MEETING_RESPONSE_INVALID",
    });
  }
}

export async function adaptMeetingProblemDetails(response: Response): Promise<MeetingErrorPayload> {
  return adaptProblemDetails(
    response,
    response.status,
    fallbackErrorCode,
    "Ошибка операции со встречей",
    "Не удалось выполнить операцию.",
  );
}

export function fetchMeetings(
  context: ServerRequestContext,
  roomId: string,
  page?: number,
  size?: number,
): Promise<PagedMeetingResponse>;
export function fetchMeetings(
  sessionCookie: string,
  apiUrl: string,
  roomId: string,
  page?: number,
  size?: number,
): Promise<PagedMeetingResponse>;
export async function fetchMeetings(
  contextOrSessionCookie: ServerRequestContext | string,
  apiUrlOrRoomId: string,
  roomIdOrPage?: string | number,
  page?: number,
  size?: number,
): Promise<PagedMeetingResponse> {
  const context = asServerRequestContext(
    contextOrSessionCookie,
    typeof contextOrSessionCookie === "string" ? apiUrlOrRoomId : undefined,
  );
  const resolvedRoomId = typeof contextOrSessionCookie === "string" ? (roomIdOrPage as string) : apiUrlOrRoomId;
  const resolvedPage = typeof contextOrSessionCookie === "string" ? page ?? 0 : (roomIdOrPage as number | undefined) ?? 0;
  const resolvedSize = typeof contextOrSessionCookie === "string" ? size ?? 20 : page ?? 20;
  const client = createApiClient(context.apiUrl);
  const { data, error, response } = await client.GET("/api/v1/rooms/{roomId}/meetings", {
    headers: context.headers,
    params: {
      path: { roomId: resolvedRoomId },
      query: { page: resolvedPage, size: resolvedSize },
    },
  });

  if (!response.ok || error) {
    throw new MeetingServiceError(
      await adaptProblemDetails(
        error ?? response,
        response.status,
        fallbackErrorCode,
        "Ошибка операции со встречей",
        "Не удалось выполнить операцию.",
      ),
    );
  }

  return parseOrThrow((d) => pagedMeetingResponseSchema.parse(d), data, "GET /api/v1/rooms/{roomId}/meetings");
}

export function createMeeting(context: MutationRequestContext, roomId: string, request: CreateMeetingRequest): Promise<Meeting>;
export function createMeeting(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
  idempotencyKey: string,
  roomId: string,
  request: CreateMeetingRequest,
): Promise<Meeting>;
export async function createMeeting(
  contextOrSessionCookie: MutationRequestContext | string,
  apiUrlOrRoomId: string,
  csrfTokenOrRequest?: string | CreateMeetingRequest,
  idempotencyKey?: string,
  roomId?: string,
  request?: CreateMeetingRequest,
): Promise<Meeting> {
  const context = asMutationRequestContext(
    contextOrSessionCookie,
    typeof contextOrSessionCookie === "string" ? apiUrlOrRoomId : undefined,
    typeof csrfTokenOrRequest === "string" ? csrfTokenOrRequest : undefined,
    idempotencyKey,
  );
  const resolvedRoomId = typeof contextOrSessionCookie === "string" ? roomId! : apiUrlOrRoomId;
  const resolvedRequest = typeof contextOrSessionCookie === "string" ? request! : (csrfTokenOrRequest as CreateMeetingRequest);
  const client = createApiClient(context.apiUrl);
  const { data, error, response } = await client.POST("/api/v1/rooms/{roomId}/meetings", {
    headers: context.headers,
    params: { path: { roomId: resolvedRoomId } },
    body: resolvedRequest,
  });

  if (!response.ok || error) {
    throw new MeetingServiceError(
      await adaptProblemDetails(
        error ?? response,
        response.status,
        fallbackErrorCode,
        "Ошибка операции со встречей",
        "Не удалось выполнить операцию.",
      ),
    );
  }

  return parseOrThrow((d) => meetingResponseSchema.parse(d), data, "POST /api/v1/rooms/{roomId}/meetings");
}

export function updateMeeting(context: MutationRequestContext, meetingId: string, request: UpdateMeetingRequest): Promise<Meeting>;
export function updateMeeting(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
  meetingId: string,
  request: UpdateMeetingRequest,
): Promise<Meeting>;
export async function updateMeeting(
  contextOrSessionCookie: MutationRequestContext | string,
  apiUrlOrMeetingId: string,
  csrfTokenOrRequest?: string | UpdateMeetingRequest,
  meetingId?: string,
  request?: UpdateMeetingRequest,
): Promise<Meeting> {
  const context = asMutationRequestContext(
    contextOrSessionCookie,
    typeof contextOrSessionCookie === "string" ? apiUrlOrMeetingId : undefined,
    typeof csrfTokenOrRequest === "string" ? csrfTokenOrRequest : undefined,
  );
  const resolvedMeetingId = typeof contextOrSessionCookie === "string" ? meetingId! : apiUrlOrMeetingId;
  const resolvedRequest = typeof contextOrSessionCookie === "string" ? request! : (csrfTokenOrRequest as UpdateMeetingRequest);
  const client = createApiClient(context.apiUrl);
  const { data, error, response } = await client.PUT("/api/v1/meetings/{meetingId}", {
    headers: context.headers,
    params: { path: { meetingId: resolvedMeetingId } },
    body: resolvedRequest,
  });

  if (!response.ok || error) {
    throw new MeetingServiceError(
      await adaptProblemDetails(
        error ?? response,
        response.status,
        fallbackErrorCode,
        "Ошибка операции со встречей",
        "Не удалось выполнить операцию.",
      ),
    );
  }

  return parseOrThrow((d) => meetingResponseSchema.parse(d), data, "PUT /api/v1/meetings/{meetingId}");
}

export function cancelMeeting(context: MutationRequestContext, meetingId: string): Promise<Meeting>;
export function cancelMeeting(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
  meetingId: string,
): Promise<Meeting>;
export async function cancelMeeting(
  contextOrSessionCookie: MutationRequestContext | string,
  apiUrlOrMeetingId: string,
  csrfToken?: string,
  meetingId?: string,
): Promise<Meeting> {
  const context = asMutationRequestContext(
    contextOrSessionCookie,
    typeof contextOrSessionCookie === "string" ? apiUrlOrMeetingId : undefined,
    csrfToken,
  );
  const resolvedMeetingId = typeof contextOrSessionCookie === "string" ? meetingId! : apiUrlOrMeetingId;
  const client = createApiClient(context.apiUrl);
  const { data, error, response } = await client.POST("/api/v1/meetings/{meetingId}/cancel", {
    headers: context.headers,
    params: { path: { meetingId: resolvedMeetingId } },
  });

  if (!response.ok || error) {
    throw new MeetingServiceError(
      await adaptProblemDetails(
        error ?? response,
        response.status,
        fallbackErrorCode,
        "Ошибка операции со встречей",
        "Не удалось выполнить операцию.",
      ),
    );
  }

  return parseOrThrow((d) => meetingResponseSchema.parse(d), data, "POST /api/v1/meetings/{meetingId}/cancel");
}
