export interface ApiErrorPayload {
  title: string;
  detail: string;
  errorCode: string;
  traceId?: string;
  httpStatus?: number;
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

function sanitizeHeaderToken(value: string): string {
  if (!value) {
    return "";
  }

  // Prevent response/request-splitting and cookie delimiter injection in composed headers.
  // eslint-disable-next-line no-control-regex -- C0 controls and DEL are intentionally removed from header values.
  return value.replace(/[\r\n\u0000-\u001F\u007F;,]/g, "").trim();
}

export function createBaseHeaders(sessionCookie: string): Record<string, string> {
  const safeSessionCookie = sanitizeHeaderToken(sessionCookie);
  return {
    Cookie: `JSESSIONID=${safeSessionCookie}`,
  };
}

export function createMutationHeaders(
  sessionCookie: string,
  csrfRequestToken: string,
  csrfCookieToken = csrfRequestToken,
  idempotencyKey?: string,
): Record<string, string> {
  const safeSessionCookie = sanitizeHeaderToken(sessionCookie);
  const safeCsrfRequestToken = sanitizeHeaderToken(csrfRequestToken);
  const safeCsrfCookieToken = sanitizeHeaderToken(csrfCookieToken);
  const headers: Record<string, string> = {
    ...createBaseHeaders(safeSessionCookie),
    "Content-Type": "application/json",
    Cookie: `JSESSIONID=${safeSessionCookie}; XSRF-TOKEN=${safeCsrfCookieToken}`,
    "X-XSRF-TOKEN": safeCsrfRequestToken,
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

  const payload: ApiErrorPayload = {
    title: readString(problem.title) ?? fallbackTitle,
    detail: readString(problem.detail) ?? fallbackDetail,
    errorCode,
    traceId: readString(problem.traceId) ?? readString(problem.properties?.traceId),
  };

  Object.defineProperty(payload, "httpStatus", {
    value: status,
    enumerable: false,
  });
  return payload;
}
