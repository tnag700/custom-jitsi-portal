import { baseHeaders, mutationHeaders } from "~/lib/domains/meetings/meetings.service";
import type { ProfileErrorPayload, UserProfileResponse, UpsertProfileRequest } from "./types";

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

export async function adaptProfileProblemDetails(
  response: Response,
): Promise<ProfileErrorPayload> {
  const problem = await readProblemDetails(response);
  const errorCode =
    typeof problem.errorCode === "string" && problem.errorCode.length > 0
      ? problem.errorCode
      : fallbackProfileErrorCode(response.status);

  return {
    title:
      typeof problem.title === "string" && problem.title.length > 0
        ? problem.title
        : "Ошибка операции с профилем",
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

export async function fetchMyProfile(
  sessionCookie: string,
  apiUrl: string,
): Promise<UserProfileResponse | null> {
  const url = `${apiUrl}/profile/me`;
  const response = await fetch(url, {
    method: "GET",
    headers: baseHeaders(sessionCookie),
  });
  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new ProfileServiceError(await adaptProfileProblemDetails(response));
  }
  return (await response.json()) as UserProfileResponse;
}

export async function upsertMyProfile(
  sessionCookie: string,
  apiUrl: string,
  csrfToken: string,
  data: UpsertProfileRequest,
): Promise<UserProfileResponse> {
  const url = `${apiUrl}/profile/me`;
  const response = await fetch(url, {
    method: "PUT",
    headers: mutationHeaders(sessionCookie, csrfToken),
    body: JSON.stringify(data),
  });
  if (!response.ok) {
    throw new ProfileServiceError(await adaptProfileProblemDetails(response));
  }
  return (await response.json()) as UserProfileResponse;
}
