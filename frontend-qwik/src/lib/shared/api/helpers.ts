export interface ApiErrorPayload {
  title: string;
  detail: string;
  errorCode: string;
  traceId?: string;
}

interface ProblemLike {
  title?: unknown;
  detail?: unknown;
  errorCode?: unknown;
  traceId?: unknown;
  properties?: {
    errorCode?: unknown;
    traceId?: unknown;
  };
}

function asRecord(value: unknown): Record<string, unknown> | null {
  if (typeof value !== "object" || value === null) {
    return null;
  }
  return value as Record<string, unknown>;
}

async function readProblemLike(input: unknown): Promise<ProblemLike> {
  if (input instanceof Response) {
    if (input.bodyUsed) {
      return {};
    }

    try {
      return (await input.clone().json()) as ProblemLike;
    } catch {
      return {};
    }
  }

  const record = asRecord(input);
  if (!record) {
    return {};
  }

  return record as ProblemLike;
}

function readString(value: unknown): string | undefined {
  return typeof value === "string" && value.length > 0 ? value : undefined;
}

export function createBaseHeaders(sessionCookie: string): Record<string, string> {
  return {
    Cookie: `JSESSIONID=${sessionCookie}`,
  };
}

export function createMutationHeaders(
  sessionCookie: string,
  csrfRequestToken: string,
  csrfCookieToken = csrfRequestToken,
  idempotencyKey?: string,
): Record<string, string> {
  const headers: Record<string, string> = {
    ...createBaseHeaders(sessionCookie),
    "Content-Type": "application/json",
    Cookie: `JSESSIONID=${sessionCookie}; XSRF-TOKEN=${csrfCookieToken}`,
    "X-XSRF-TOKEN": csrfRequestToken,
  };

  if (idempotencyKey) {
    headers["Idempotency-Key"] = idempotencyKey;
  }

  return headers;
}

export async function adaptProblemDetails(
  input: unknown,
  status: number,
  fallbackErrorCode: (status: number) => string,
  fallbackTitle = "API request failed",
  fallbackDetail = "Unable to complete API request.",
): Promise<ApiErrorPayload> {
  const problem = await readProblemLike(input);

  const errorCode =
    readString(problem.errorCode) ??
    readString(problem.properties?.errorCode) ??
    fallbackErrorCode(status);

  return {
    title: readString(problem.title) ?? fallbackTitle,
    detail: readString(problem.detail) ?? fallbackDetail,
    errorCode,
    traceId: readString(problem.traceId) ?? readString(problem.properties?.traceId),
  };
}
