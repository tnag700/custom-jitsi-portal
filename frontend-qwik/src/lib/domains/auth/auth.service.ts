import type { AuthErrorPayload, SafeUserProfile } from "./types";
import {
  createApiClient,
  adaptProblemDetails as adaptApiProblemDetails,
  fetchCsrfToken as fetchSharedCsrfToken,
  safeUserProfileResponseSchema,
} from "../../shared/api";
import type {
  MutationRequestContext,
  ServerRequestContext,
} from "../../shared/routes/server-handlers";
import { asMutationRequestContext, asServerRequestContext } from "../../shared/routes/server-handlers";

const DEFAULT_AUTH_ACTIONS = "Выполните вход через SSO.";

export class AuthServiceError extends Error {
  payload: AuthErrorPayload;

  constructor(payload: AuthErrorPayload) {
    super(payload.reason);
    this.name = "AuthServiceError";
    this.payload = payload;
  }
}

function fallbackErrorCode(status: number): string {
  if (status === 401) {
    return "AUTH_REQUIRED";
  }
  if (status === 403) {
    return "ACCESS_DENIED";
  }
  if (status >= 500) {
    return "AUTH_SERVICE_UNAVAILABLE";
  }
  return "AUTH_UNKNOWN";
}

function fallbackActions(errorCode: string): string {
  if (errorCode === "AUTH_REQUIRED") {
    return "Выполните вход через SSO.";
  }
  if (errorCode === "ACCESS_DENIED") {
    return "Обратитесь к администратору.";
  }
  if (errorCode === "AUTH_SERVICE_UNAVAILABLE") {
    return "Повторите попытку позже.";
  }
  return DEFAULT_AUTH_ACTIONS;
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

function parseProfileOrThrow(data: unknown, endpoint: string) {
  try {
    return safeUserProfileResponseSchema.partial().parse(data);
  } catch (e) {
    throw new AuthServiceError({
      title: "Неожиданный формат ответа",
      reason: `${endpoint}: ${e instanceof Error ? e.message : "неверный формат ответа"}`,
      actions: "Повторите вход через SSO. Если проблема повторится, обратитесь к администратору.",
      errorCode: "AUTH_RESPONSE_INVALID",
    });
  }
}

function readRequiredProfileField(
  value: unknown,
  fieldName: string,
): string {
  if (isNonEmptyString(value)) {
    return value;
  }

  throw new AuthServiceError({
    title: "Некорректные данные профиля",
    reason: `Поле профиля '${fieldName}' отсутствует или пустое.`,
    actions: "Повторите вход через SSO. Если проблема повторится, обратитесь к администратору.",
    errorCode: "AUTH_PROFILE_INVALID",
  });
}

export async function adaptProblemDetails(
  response: Response,
): Promise<AuthErrorPayload> {
  const payload = await adaptApiProblemDetails(
    response,
    response.status,
    fallbackErrorCode,
    "Ошибка аутентификации",
    "Не удалось получить профиль пользователя.",
  );

  return {
    title: payload.title,
    reason: payload.detail,
    actions: fallbackActions(payload.errorCode),
    errorCode: payload.errorCode,
  };
}

export function fetchAuthMe(context: ServerRequestContext): Promise<SafeUserProfile>;
export function fetchAuthMe(sessionCookie: string, apiUrl: string): Promise<SafeUserProfile>;
export async function fetchAuthMe(
  contextOrSessionCookie: ServerRequestContext | string,
  apiUrl?: string,
): Promise<SafeUserProfile> {
  const context = asServerRequestContext(contextOrSessionCookie, apiUrl);
  const client = createApiClient(context.apiUrl);
  const { data, error, response } = await client.GET("/api/v1/auth/me", {
    headers: context.headers,
  });

  if (!response.ok || error) {
    const payload = await adaptApiProblemDetails(
      error ?? response,
      response.status,
      fallbackErrorCode,
      "Ошибка аутентификации",
      "Не удалось получить профиль пользователя.",
    );

    throw new AuthServiceError({
      title: payload.title,
      reason: payload.detail,
      actions: fallbackActions(payload.errorCode),
      errorCode: payload.errorCode,
    });
  }

  const profile = parseProfileOrThrow(data, "GET /api/v1/auth/me");

  return {
    id: readRequiredProfileField(profile.id, "id"),
    displayName: readRequiredProfileField(profile.displayName, "displayName"),
    email: readRequiredProfileField(profile.email, "email"),
    tenant: isNonEmptyString(profile.tenant) ? profile.tenant : "",
    claims: Array.isArray(profile.claims) ? profile.claims : [],
  };
}

export async function logoutFromAuthSession(context: MutationRequestContext): Promise<string> {
  const requestContext = asMutationRequestContext(context);

  const response = await fetch(`${requestContext.apiUrl}/auth/logout`, {
    method: "POST",
    headers: requestContext.headers,
    redirect: "manual",
  });

  if (response.status === 302 || response.status === 303) {
    const location = response.headers.get("location");
    if (location && location.length > 0) {
      return location;
    }
  }

  if (!response.ok) {
    const payload = await adaptApiProblemDetails(
      response,
      response.status,
      fallbackErrorCode,
      "Ошибка аутентификации",
      "Не удалось завершить выход из системы.",
    );

    throw new AuthServiceError({
      title: payload.title,
      reason: payload.detail,
      actions: fallbackActions(payload.errorCode),
      errorCode: payload.errorCode,
    });
  }

  throw new AuthServiceError({
    title: "Некорректный ответ logout",
    reason: "Backend не вернул redirect для logout flow.",
    actions: DEFAULT_AUTH_ACTIONS,
    errorCode: "AUTH_LOGOUT_REDIRECT_MISSING",
  });
}

export async function fetchCsrfToken(
  sessionCookie: string,
  apiUrl: string,
): Promise<string> {
  return fetchSharedCsrfToken(sessionCookie, apiUrl);
}
