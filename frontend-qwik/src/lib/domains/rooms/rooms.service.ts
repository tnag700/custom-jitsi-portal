import type {
  Room,
  PagedRoomResponse,
  CreateRoomRequest,
  UpdateRoomRequest,
  RoomErrorPayload,
} from "./types";

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

async function adaptRoomProblemDetails(response: Response): Promise<RoomErrorPayload> {
  const problem = await readProblemDetails(response);
  const errorCode =
    typeof problem.errorCode === "string" && problem.errorCode.length > 0
      ? problem.errorCode
      : fallbackErrorCode(response.status);

  return {
    title:
      typeof problem.title === "string" && problem.title.length > 0
        ? problem.title
        : "Ошибка операции с комнатой",
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

function baseHeaders(sessionCookie: string): Record<string, string> {
  return {
    Cookie: `JSESSIONID=${sessionCookie}`,
    "Content-Type": "application/json",
  };
}

function mutationHeaders(
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

  return {
    ...headers,
  };
}

export async function fetchRooms(
  sessionCookie: string,
  apiUrl: string,
  tenantId: string,
  page = 0,
  size = 20,
): Promise<PagedRoomResponse> {
  const url = `${apiUrl}/rooms?tenantId=${encodeURIComponent(tenantId)}&page=${page}&size=${size}`;
  const response = await fetch(url, {
    method: "GET",
    headers: baseHeaders(sessionCookie),
  });

  if (!response.ok) {
    throw new RoomServiceError(await adaptRoomProblemDetails(response));
  }

  return (await response.json()) as PagedRoomResponse;
}

export async function createRoom(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
  idempotencyKey: string,
  request: CreateRoomRequest,
): Promise<Room> {
  const response = await fetch(`${apiUrl}/rooms`, {
    method: "POST",
    headers: mutationHeaders(sessionCookie, csrfToken, idempotencyKey),
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw new RoomServiceError(await adaptRoomProblemDetails(response));
  }

  return (await response.json()) as Room;
}

export async function updateRoom(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
  idempotencyKey: string,
  roomId: string,
  request: UpdateRoomRequest,
): Promise<Room> {
  const response = await fetch(`${apiUrl}/rooms/${encodeURIComponent(roomId)}`, {
    method: "PUT",
    headers: mutationHeaders(sessionCookie, csrfToken, idempotencyKey),
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw new RoomServiceError(await adaptRoomProblemDetails(response));
  }

  return (await response.json()) as Room;
}

export async function closeRoom(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
  idempotencyKey: string,
  roomId: string,
): Promise<Room> {
  const response = await fetch(`${apiUrl}/rooms/${encodeURIComponent(roomId)}/close`, {
    method: "POST",
    headers: mutationHeaders(sessionCookie, csrfToken, idempotencyKey),
  });

  if (!response.ok) {
    throw new RoomServiceError(await adaptRoomProblemDetails(response));
  }

  return (await response.json()) as Room;
}

export async function deleteRoom(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
  idempotencyKey: string,
  roomId: string,
): Promise<void> {
  const response = await fetch(`${apiUrl}/rooms/${encodeURIComponent(roomId)}`, {
    method: "DELETE",
    headers: mutationHeaders(sessionCookie, csrfToken, idempotencyKey),
  });

  if (!response.ok) {
    throw new RoomServiceError(await adaptRoomProblemDetails(response));
  }
}
