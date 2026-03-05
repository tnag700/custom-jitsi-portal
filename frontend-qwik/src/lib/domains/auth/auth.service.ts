import type { AuthErrorPayload, SafeUserProfile } from "./types";

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

interface ProblemDetailsLike {
  title?: unknown;
  detail?: unknown;
  errorCode?: unknown;
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0;
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

async function readProblemDetails(response: Response): Promise<ProblemDetailsLike> {
  try {
    return (await response.json()) as ProblemDetailsLike;
  } catch {
    return {};
  }
}

export async function adaptProblemDetails(
  response: Response,
): Promise<AuthErrorPayload> {
  const problem = await readProblemDetails(response);
  const errorCode =
    typeof problem.errorCode === "string" && problem.errorCode.length > 0
      ? problem.errorCode
      : fallbackErrorCode(response.status);

  return {
    title:
      typeof problem.title === "string" && problem.title.length > 0
        ? problem.title
        : "Ошибка аутентификации",
    reason:
      typeof problem.detail === "string" && problem.detail.length > 0
        ? problem.detail
        : "Не удалось получить профиль пользователя.",
    actions: fallbackActions(errorCode),
    errorCode,
  };
}

export async function fetchAuthMe(
  sessionCookie: string,
  apiUrl: string,
): Promise<SafeUserProfile> {
  const response = await fetch(`${apiUrl}/auth/me`, {
    method: "GET",
    headers: {
      Cookie: `JSESSIONID=${sessionCookie}`,
    },
  });

  if (!response.ok) {
    throw new AuthServiceError(await adaptProblemDetails(response));
  }

  const profile = (await response.json()) as Partial<SafeUserProfile>;

  return {
    id: readRequiredProfileField(profile.id, "id"),
    displayName: readRequiredProfileField(profile.displayName, "displayName"),
    email: readRequiredProfileField(profile.email, "email"),
    tenant: isNonEmptyString(profile.tenant) ? profile.tenant : "",
    claims: Array.isArray(profile.claims)
      ? profile.claims.filter((claim): claim is string => typeof claim === "string")
      : [],
  };
}
