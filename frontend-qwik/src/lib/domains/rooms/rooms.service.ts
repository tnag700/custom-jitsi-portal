import type {
  Room,
  PagedRoomResponse,
  CreateRoomRequest,
  UpdateRoomRequest,
  RoomErrorPayload,
} from "./types";
import {
  createApiClient,
  adaptProblemDetails,
  pagedRoomResponseSchema,
  roomResponseSchema,
} from "../../shared/api";
import type {
  MutationRequestContext,
  ServerRequestContext,
} from "../../shared/routes/server-handlers";
import { asMutationRequestContext, asServerRequestContext } from "../../shared/routes/server-handlers";

export class RoomServiceError extends Error {
  payload: RoomErrorPayload;

  constructor(payload: RoomErrorPayload) {
    super(payload.detail);
    this.name = "RoomServiceError";
    this.payload = payload;
  }
}

function fallbackErrorCode(status: number): string {
  if (status === 404) return "ROOM_NOT_FOUND";
  if (status === 409) return "ROOM_NAME_CONFLICT";
  if (status >= 500) return "ROOM_SERVICE_UNAVAILABLE";
  return "ROOM_UNKNOWN";
}

function parseOrThrow<T>(parseFn: (d: unknown) => T, data: unknown, endpoint: string): T {
  try {
    return parseFn(data);
  } catch (e) {
    throw new RoomServiceError({
      title: "Неожиданный формат ответа",
      detail: `${endpoint}: ${e instanceof Error ? e.message : "неверный формат ответа"}`,
      errorCode: "ROOM_RESPONSE_INVALID",
    });
  }
}

export function fetchRooms(
  context: ServerRequestContext,
  tenantId: string,
  page?: number,
  size?: number,
): Promise<PagedRoomResponse>;
export function fetchRooms(
  sessionCookie: string,
  apiUrl: string,
  tenantId: string,
  page?: number,
  size?: number,
): Promise<PagedRoomResponse>;
export async function fetchRooms(
  contextOrSessionCookie: ServerRequestContext | string,
  apiUrlOrTenantId: string,
  tenantIdOrPage?: string | number,
  page?: number,
  size?: number,
): Promise<PagedRoomResponse> {
  const context = asServerRequestContext(
    contextOrSessionCookie,
    typeof contextOrSessionCookie === "string" ? apiUrlOrTenantId : undefined,
  );
  const resolvedTenantId = typeof contextOrSessionCookie === "string" ? (tenantIdOrPage as string) : apiUrlOrTenantId;
  const resolvedPage = typeof contextOrSessionCookie === "string" ? page ?? 0 : (tenantIdOrPage as number | undefined) ?? 0;
  const resolvedSize = typeof contextOrSessionCookie === "string" ? size ?? 20 : page ?? 20;
  const client = createApiClient(context.apiUrl);
  const { data, error, response } = await client.GET("/api/v1/rooms", {
    headers: context.headers,
    params: {
      query: { tenantId: resolvedTenantId, page: resolvedPage, size: resolvedSize },
    },
  });

  if (!response.ok || error) {
    throw new RoomServiceError(
      await adaptProblemDetails(
        error ?? response,
        response.status,
        fallbackErrorCode,
        "Ошибка операции с комнатой",
        "Не удалось выполнить операцию.",
      ),
    );
  }

  return parseOrThrow((d) => pagedRoomResponseSchema.parse(d), data, "GET /api/v1/rooms");
}

export function createRoom(context: MutationRequestContext, request: CreateRoomRequest): Promise<Room>;
export function createRoom(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
  idempotencyKey: string,
  request: CreateRoomRequest,
): Promise<Room>;
export async function createRoom(
  contextOrSessionCookie: MutationRequestContext | string,
  apiUrlOrRequest: string | CreateRoomRequest,
  csrfToken?: string,
  idempotencyKey?: string,
  request?: CreateRoomRequest,
): Promise<Room> {
  const context = asMutationRequestContext(
    contextOrSessionCookie,
    typeof contextOrSessionCookie === "string" ? (apiUrlOrRequest as string) : undefined,
    csrfToken,
    idempotencyKey,
  );
  const resolvedRequest = typeof contextOrSessionCookie === "string" ? request! : (apiUrlOrRequest as CreateRoomRequest);
  const client = createApiClient(context.apiUrl);
  const { data, error, response } = await client.POST("/api/v1/rooms", {
    headers: context.headers,
    body: resolvedRequest,
  });

  if (!response.ok || error) {
    throw new RoomServiceError(
      await adaptProblemDetails(
        error ?? response,
        response.status,
        fallbackErrorCode,
        "Ошибка операции с комнатой",
        "Не удалось выполнить операцию.",
      ),
    );
  }

  return parseOrThrow((d) => roomResponseSchema.parse(d), data, "POST /api/v1/rooms");
}

export function updateRoom(context: MutationRequestContext, roomId: string, request: UpdateRoomRequest): Promise<Room>;
export function updateRoom(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
  idempotencyKey: string,
  roomId: string,
  request: UpdateRoomRequest,
): Promise<Room>;
export async function updateRoom(
  contextOrSessionCookie: MutationRequestContext | string,
  apiUrlOrRoomId: string,
  csrfTokenOrRequest?: string | UpdateRoomRequest,
  idempotencyKey?: string,
  roomId?: string,
  request?: UpdateRoomRequest,
): Promise<Room> {
  const context = asMutationRequestContext(
    contextOrSessionCookie,
    typeof contextOrSessionCookie === "string" ? apiUrlOrRoomId : undefined,
    typeof csrfTokenOrRequest === "string" ? csrfTokenOrRequest : undefined,
    idempotencyKey,
  );
  const resolvedRoomId = typeof contextOrSessionCookie === "string" ? roomId! : apiUrlOrRoomId;
  const resolvedRequest = typeof contextOrSessionCookie === "string" ? request! : (csrfTokenOrRequest as UpdateRoomRequest);
  const client = createApiClient(context.apiUrl);
  const { data, error, response } = await client.PUT("/api/v1/rooms/{roomId}", {
    headers: context.headers,
    params: { path: { roomId: resolvedRoomId } },
    body: resolvedRequest,
  });

  if (!response.ok || error) {
    throw new RoomServiceError(
      await adaptProblemDetails(
        error ?? response,
        response.status,
        fallbackErrorCode,
        "Ошибка операции с комнатой",
        "Не удалось выполнить операцию.",
      ),
    );
  }

  return parseOrThrow((d) => roomResponseSchema.parse(d), data, "PUT /api/v1/rooms/{roomId}");
}

export function closeRoom(context: MutationRequestContext, roomId: string): Promise<Room>;
export function closeRoom(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
  idempotencyKey: string,
  roomId: string,
): Promise<Room>;
export async function closeRoom(
  contextOrSessionCookie: MutationRequestContext | string,
  apiUrlOrRoomId: string,
  csrfToken?: string,
  idempotencyKey?: string,
  roomId?: string,
): Promise<Room> {
  const context = asMutationRequestContext(
    contextOrSessionCookie,
    typeof contextOrSessionCookie === "string" ? apiUrlOrRoomId : undefined,
    csrfToken,
    idempotencyKey,
  );
  const resolvedRoomId = typeof contextOrSessionCookie === "string" ? roomId! : apiUrlOrRoomId;
  const client = createApiClient(context.apiUrl);
  const { data, error, response } = await client.POST("/api/v1/rooms/{roomId}/close", {
    headers: context.headers,
    params: { path: { roomId: resolvedRoomId } },
  });

  if (!response.ok || error) {
    throw new RoomServiceError(
      await adaptProblemDetails(
        error ?? response,
        response.status,
        fallbackErrorCode,
        "Ошибка операции с комнатой",
        "Не удалось выполнить операцию.",
      ),
    );
  }

  return parseOrThrow((d) => roomResponseSchema.parse(d), data, "POST /api/v1/rooms/{roomId}/close");
}

export function deleteRoom(context: MutationRequestContext, roomId: string): Promise<void>;
export function deleteRoom(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
  idempotencyKey: string,
  roomId: string,
): Promise<void>;
export async function deleteRoom(
  contextOrSessionCookie: MutationRequestContext | string,
  apiUrlOrRoomId: string,
  csrfToken?: string,
  idempotencyKey?: string,
  roomId?: string,
): Promise<void> {
  const context = asMutationRequestContext(
    contextOrSessionCookie,
    typeof contextOrSessionCookie === "string" ? apiUrlOrRoomId : undefined,
    csrfToken,
    idempotencyKey,
  );
  const resolvedRoomId = typeof contextOrSessionCookie === "string" ? roomId! : apiUrlOrRoomId;
  const client = createApiClient(context.apiUrl);
  const { error, response } = await client.DELETE("/api/v1/rooms/{roomId}", {
    headers: context.headers,
    params: { path: { roomId: resolvedRoomId } },
  });

  if (!response.ok || error) {
    throw new RoomServiceError(
      await adaptProblemDetails(
        error ?? response,
        response.status,
        fallbackErrorCode,
        "Ошибка операции с комнатой",
        "Не удалось выполнить операцию.",
      ),
    );
  }
}
