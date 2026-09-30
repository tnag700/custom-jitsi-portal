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

// Adapts schema failures only; callers decode JSON before invoking this helper.
export function parseOrThrow<T>(parseFn: (d: unknown) => T, data: unknown, endpoint: string): T {
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

export async function fetchMeeting(
  context: ServerRequestContext,
  meetingId: string,
): Promise<Meeting> {
  const client = createApiClient(context.apiUrl);
  const { data, error, response } = await client.GET("/api/v1/meetings/{meetingId}", {
    headers: context.headers,
    params: { path: { meetingId } },
  });
  if (!response.ok || error) {
    throw new MeetingServiceError(await adaptProblemDetails(
      error ?? response, response.status, fallbackErrorCode,
      "Ошибка загрузки встречи", "Не удалось загрузить встречу.",
    ));
  }
  return parseOrThrow((value) => meetingResponseSchema.parse(value), data, "GET /api/v1/meetings/{meetingId}");
}

export async function fetchMeetings(
  context: ServerRequestContext,
  roomId: string,
  page = 0,
  size = 20,
): Promise<PagedMeetingResponse> {
  const client = createApiClient(context.apiUrl);
  const { data, error, response } = await client.GET("/api/v1/rooms/{roomId}/meetings", {
    headers: context.headers,
    params: {
      path: { roomId },
      query: { page, size },
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

export async function createMeeting(
  context: MutationRequestContext,
  roomId: string,
  request: CreateMeetingRequest,
): Promise<Meeting> {
  const client = createApiClient(context.apiUrl);
  const { data, error, response } = await client.POST("/api/v1/rooms/{roomId}/meetings", {
    headers: context.headers,
    params: { path: { roomId } },
    body: request,
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

export async function updateMeeting(
  context: MutationRequestContext,
  meetingId: string,
  request: UpdateMeetingRequest,
): Promise<Meeting> {
  const client = createApiClient(context.apiUrl);
  const { data, error, response } = await client.PUT("/api/v1/meetings/{meetingId}", {
    headers: context.headers,
    params: { path: { meetingId } },
    body: request,
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

export async function cancelMeeting(
  context: MutationRequestContext,
  meetingId: string,
): Promise<Meeting> {
  const client = createApiClient(context.apiUrl);
  const { data, error, response } = await client.POST("/api/v1/meetings/{meetingId}/cancel", {
    headers: context.headers,
    params: { path: { meetingId } },
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
