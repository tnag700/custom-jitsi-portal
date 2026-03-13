import type { ProfileErrorPayload, UserProfileResponse, UpsertProfileRequest } from "./types";
import {
  createApiClient,
  adaptProblemDetails,
  userProfileResponseSchema,
} from "../../shared/api";
import type {
  MutationRequestContext,
  ServerRequestContext,
} from "../../shared/routes/server-handlers";
import { asMutationRequestContext, asServerRequestContext } from "../../shared/routes/server-handlers";

export class ProfileServiceError extends Error {
  payload: ProfileErrorPayload;

  constructor(payload: ProfileErrorPayload) {
    super(payload.detail);
    this.name = "ProfileServiceError";
    this.payload = payload;
  }
}

function fallbackProfileErrorCode(status: number): string {
  if (status === 401) return "AUTH_REQUIRED";
  if (status === 400) return "PROFILE_VALIDATION_FAILED";
  if (status === 404) return "PROFILE_NOT_FOUND";
  if (status >= 500) return "PROFILE_SERVICE_UNAVAILABLE";
  return "PROFILE_UNKNOWN";
}

function parseOrThrow<T>(parseFn: (d: unknown) => T, data: unknown, endpoint: string): T {
  try {
    return parseFn(data);
  } catch (e) {
    throw new ProfileServiceError({
      title: "Неожиданный формат ответа",
      detail: `${endpoint}: ${e instanceof Error ? e.message : "неверный формат ответа"}`,
      errorCode: "PROFILE_RESPONSE_INVALID",
    });
  }
}

export async function adaptProfileProblemDetails(
  response: Response,
): Promise<ProfileErrorPayload> {
  return adaptProblemDetails(
    response,
    response.status,
    fallbackProfileErrorCode,
    "Ошибка операции с профилем",
    "Не удалось выполнить операцию.",
  );
}

export function fetchMyProfile(context: ServerRequestContext): Promise<UserProfileResponse | null>;
export function fetchMyProfile(sessionCookie: string, apiUrl: string): Promise<UserProfileResponse | null>;
export async function fetchMyProfile(
  contextOrSessionCookie: ServerRequestContext | string,
  apiUrl?: string,
): Promise<UserProfileResponse | null> {
  const context = asServerRequestContext(contextOrSessionCookie, apiUrl);
  const client = createApiClient(context.apiUrl);
  const { data, error, response } = await client.GET("/api/v1/profile/me", {
    headers: context.headers,
  });

  if (response.status === 404) {
    return null;
  }
  if (!response.ok || error) {
    throw new ProfileServiceError(
      await adaptProblemDetails(
        error ?? response,
        response.status,
        fallbackProfileErrorCode,
        "Ошибка операции с профилем",
        "Не удалось выполнить операцию.",
      ),
    );
  }
  return parseOrThrow((d) => userProfileResponseSchema.parse(d), data, "GET /api/v1/profile/me");
}

export function upsertMyProfile(context: MutationRequestContext, data: UpsertProfileRequest): Promise<UserProfileResponse>;
export function upsertMyProfile(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
  data: UpsertProfileRequest,
): Promise<UserProfileResponse>;
export async function upsertMyProfile(
  contextOrSessionCookie: MutationRequestContext | string,
  apiUrlOrData: string | UpsertProfileRequest,
  csrfToken?: string,
  data?: UpsertProfileRequest,
): Promise<UserProfileResponse> {
  const context = asMutationRequestContext(
    contextOrSessionCookie,
    typeof contextOrSessionCookie === "string" ? (apiUrlOrData as string) : undefined,
    csrfToken,
  );
  const resolvedData = typeof contextOrSessionCookie === "string" ? data! : (apiUrlOrData as UpsertProfileRequest);
  const client = createApiClient(context.apiUrl);
  const { data: responseData, error, response } = await client.PUT("/api/v1/profile/me", {
    headers: context.headers,
    body: resolvedData,
  });

  if (!response.ok || error) {
    throw new ProfileServiceError(
      await adaptProblemDetails(
        error ?? response,
        response.status,
        fallbackProfileErrorCode,
        "Ошибка операции с профилем",
        "Не удалось выполнить операцию.",
      ),
    );
  }

  return parseOrThrow((d) => userProfileResponseSchema.parse(d), responseData, "PUT /api/v1/profile/me");
}
