import type {
  CreateInviteRequest,
  Invite,
  InviteErrorPayload,
  PagedInviteResponse,
} from "./types";
import {
  createApiClient,
  adaptProblemDetails,
  inviteResponseSchema,
  pagedInviteResponseSchema,
} from "../../shared/api";
import type {
  MutationRequestContext,
  ServerRequestContext,
} from "../../shared/routes/server-handlers";
import { asMutationRequestContext, asServerRequestContext } from "../../shared/routes/server-handlers";

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

function parseOrThrow<T>(parseFn: (d: unknown) => T, data: unknown, endpoint: string): T {
  try {
    return parseFn(data);
  } catch (e) {
    throw new InviteServiceError({
      title: "Неожиданный формат ответа",
      detail: `${endpoint}: ${e instanceof Error ? e.message : "неверный формат ответа"}`,
      errorCode: "INVITE_RESPONSE_INVALID",
    });
  }
}

export async function adaptInviteProblemDetails(response: Response): Promise<InviteErrorPayload> {
  return adaptProblemDetails(
    response,
    response.status,
    fallbackErrorCode,
    "Ошибка операции с инвайтом",
    "Не удалось выполнить операцию с инвайтом.",
  );
}

export function fetchInvites(
  context: ServerRequestContext,
  meetingId: string,
  page?: number,
  size?: number,
): Promise<PagedInviteResponse>;
export function fetchInvites(
  sessionCookie: string,
  apiUrl: string,
  meetingId: string,
  page?: number,
  size?: number,
): Promise<PagedInviteResponse>;
export async function fetchInvites(
  contextOrSessionCookie: ServerRequestContext | string,
  apiUrlOrMeetingId: string,
  meetingIdOrPage?: string | number,
  page?: number,
  size?: number,
): Promise<PagedInviteResponse> {
  const context = asServerRequestContext(
    contextOrSessionCookie,
    typeof contextOrSessionCookie === "string" ? apiUrlOrMeetingId : undefined,
  );
  const resolvedMeetingId = typeof contextOrSessionCookie === "string" ? (meetingIdOrPage as string) : apiUrlOrMeetingId;
  const resolvedPage = typeof contextOrSessionCookie === "string" ? page ?? 0 : (meetingIdOrPage as number | undefined) ?? 0;
  const resolvedSize = typeof contextOrSessionCookie === "string" ? size ?? 20 : page ?? 20;
  const client = createApiClient(context.apiUrl);
  const { data, error, response } = await client.GET("/api/v1/meetings/{meetingId}/invites", {
    headers: context.headers,
    params: {
      path: { meetingId: resolvedMeetingId },
      query: { page: resolvedPage, size: resolvedSize },
    },
  });

  if (!response.ok || error) {
    throw new InviteServiceError(
      await adaptProblemDetails(
        error ?? response,
        response.status,
        fallbackErrorCode,
        "Ошибка операции с инвайтом",
        "Не удалось выполнить операцию с инвайтом.",
      ),
    );
  }

  const parsed = parseOrThrow((d) => pagedInviteResponseSchema.parse(d), data, "GET /api/v1/meetings/{meetingId}/invites");

  return {
    content: parsed.content ?? parsed.items ?? [],
    page: parsed.page,
    pageSize: parsed.pageSize ?? parsed.size ?? resolvedSize,
    totalElements: parsed.totalElements,
    totalPages: parsed.totalPages,
  };
}

export function createInvite(context: MutationRequestContext, meetingId: string, request: CreateInviteRequest): Promise<Invite>;
export function createInvite(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
  idempotencyKey: string,
  meetingId: string,
  request: CreateInviteRequest,
): Promise<Invite>;
export async function createInvite(
  contextOrSessionCookie: MutationRequestContext | string,
  apiUrlOrMeetingId: string,
  csrfTokenOrRequest?: string | CreateInviteRequest,
  idempotencyKey?: string,
  meetingId?: string,
  request?: CreateInviteRequest,
): Promise<Invite> {
  const context = asMutationRequestContext(
    contextOrSessionCookie,
    typeof contextOrSessionCookie === "string" ? apiUrlOrMeetingId : undefined,
    typeof csrfTokenOrRequest === "string" ? csrfTokenOrRequest : undefined,
    idempotencyKey,
  );
  const resolvedMeetingId = typeof contextOrSessionCookie === "string" ? meetingId! : apiUrlOrMeetingId;
  const resolvedRequest = typeof contextOrSessionCookie === "string" ? request! : (csrfTokenOrRequest as CreateInviteRequest);
  const client = createApiClient(context.apiUrl);
  const { data, error, response } = await client.POST("/api/v1/meetings/{meetingId}/invites", {
    headers: context.headers,
    params: { path: { meetingId: resolvedMeetingId } },
    body: resolvedRequest,
  });

  if (!response.ok || error) {
    throw new InviteServiceError(
      await adaptProblemDetails(
        error ?? response,
        response.status,
        fallbackErrorCode,
        "Ошибка операции с инвайтом",
        "Не удалось выполнить операцию с инвайтом.",
      ),
    );
  }

  return parseOrThrow((d) => inviteResponseSchema.parse(d), data, "POST /api/v1/meetings/{meetingId}/invites");
}

export function revokeInvite(context: MutationRequestContext, meetingId: string, inviteId: string): Promise<void>;
export function revokeInvite(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
  meetingId: string,
  inviteId: string,
): Promise<void>;
export async function revokeInvite(
  contextOrSessionCookie: MutationRequestContext | string,
  apiUrlOrMeetingId: string,
  csrfTokenOrInviteId?: string,
  meetingId?: string,
  inviteId?: string,
): Promise<void> {
  const context = asMutationRequestContext(
    contextOrSessionCookie,
    typeof contextOrSessionCookie === "string" ? apiUrlOrMeetingId : undefined,
    typeof csrfTokenOrInviteId === "string" ? csrfTokenOrInviteId : undefined,
  );
  const resolvedMeetingId = typeof contextOrSessionCookie === "string" ? meetingId! : apiUrlOrMeetingId;
  const resolvedInviteId = typeof contextOrSessionCookie === "string" ? inviteId! : (csrfTokenOrInviteId as string);
  const client = createApiClient(context.apiUrl);
  const { error, response } = await client.DELETE("/api/v1/meetings/{meetingId}/invites/{inviteId}", {
    headers: context.headers,
    params: { path: { meetingId: resolvedMeetingId, inviteId: resolvedInviteId } },
  });

  if (!response.ok || error) {
    throw new InviteServiceError(
      await adaptProblemDetails(
        error ?? response,
        response.status,
        fallbackErrorCode,
        "Ошибка операции с инвайтом",
        "Не удалось выполнить операцию с инвайтом.",
      ),
    );
  }
}
