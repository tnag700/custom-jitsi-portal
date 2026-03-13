import {
  createBaseHeaders,
  createMutationHeaders,
  fetchCsrfTokenPair,
} from "~/lib/shared/api";

interface CookieValue {
  value?: string;
}

interface CookieAccessor {
  get(name: string): CookieValue | undefined | null;
  set?(name: string, value: string, options?: Record<string, unknown>): void;
}

interface RequestContextSource {
  sharedMap: Map<string, unknown>;
  cookie: CookieAccessor;
}

export const DEFAULT_SERVER_API_URL = "http://localhost:8080/api/v1";

interface ErrorPayload {
  title: string;
  detail: string;
  errorCode: string;
  traceId?: string;
}

type ErrorWithPayload = Error & { payload: ErrorPayload };

type FailFn = (status: number, payload: { error: ErrorPayload }) => unknown;

// The route handlers pass several domain-specific Error subclasses with incompatible constructor payloads.
// A narrow lint override here keeps the helper generic without constraining each error class to a shared constructor shape.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
type ErrorConstructor<TError extends ErrorWithPayload> = abstract new (...args: any[]) => TError;

export interface ServerRequestContext {
  apiUrl: string;
  sessionCookie: string;
  csrfToken: string;
  headers: Record<string, string>;
}

export interface MutationRequestContext extends ServerRequestContext {
  csrfCookieToken: string;
  idempotencyKey: string;
}

const SESSION_COOKIE = "JSESSIONID";
const CSRF_COOKIE = "XSRF-TOKEN";
const REQUEST_COOKIE_HEADER_KEY = "requestCookieHeader";

function readCookieFromHeader(cookieHeader: string, name: string): string {
  if (!cookieHeader) {
    return "";
  }

  for (const fragment of cookieHeader.split(";")) {
    const [rawName, ...rawValueParts] = fragment.trim().split("=");
    if (rawName !== name) {
      continue;
    }

    return rawValueParts.join("=").trim();
  }

  return "";
}

function readIncomingCookie(source: RequestContextSource, name: string): string {
  const directCookie = source.cookie.get(name)?.value ?? "";
  if (directCookie.length > 0) {
    return directCookie;
  }

  const cookieHeader = source.sharedMap.get(REQUEST_COOKIE_HEADER_KEY);
  if (typeof cookieHeader === "string") {
    return readCookieFromHeader(cookieHeader, name);
  }

  return "";
}

function resolveApiUrl(sharedMap: Map<string, unknown>): string {
  const apiUrl = sharedMap.get("apiUrl");
  return typeof apiUrl === "string" && apiUrl.trim().length > 0
    ? apiUrl
    : DEFAULT_SERVER_API_URL;
}

export function buildServerRequestContext({ sharedMap, cookie }: RequestContextSource): ServerRequestContext {
  const source = { sharedMap, cookie };
  const sessionCookie = readIncomingCookie(source, SESSION_COOKIE);
  const csrfToken = readIncomingCookie(source, CSRF_COOKIE);

  return {
    apiUrl: resolveApiUrl(sharedMap),
    sessionCookie,
    csrfToken,
    headers: createBaseHeaders(sessionCookie),
  };
}

async function resolveCsrfToken(source: RequestContextSource, apiUrl: string, sessionCookie: string): Promise<string> {
  if (!sessionCookie) {
    return "";
  }

  try {
    const pair = await fetchCsrfTokenPair(sessionCookie, apiUrl);
    const cookieToken = pair.cookieToken.length > 0
      ? pair.cookieToken
      : (readIncomingCookie(source, CSRF_COOKIE) || pair.requestToken);
    const requestToken = pair.requestToken.length > 0 ? pair.requestToken : cookieToken;

    if (cookieToken.length === 0 && requestToken.length === 0) {
      return "";
    }

    if (cookieToken.length > 0) {
      source.cookie.set?.(CSRF_COOKIE, cookieToken, {
        path: "/",
        sameSite: "lax",
      });
    }

    source.sharedMap.set("csrfCookieToken", cookieToken);

    return requestToken;
  } catch {
    const fallbackCookieToken = readIncomingCookie(source, CSRF_COOKIE);
    source.sharedMap.set("csrfCookieToken", fallbackCookieToken);
    return fallbackCookieToken;
  }
}

function resolveCsrfCookieToken(source: RequestContextSource): string {
  const sharedCookieToken = source.sharedMap.get("csrfCookieToken");
  if (typeof sharedCookieToken === "string") {
    return sharedCookieToken;
  }
  return readIncomingCookie(source, CSRF_COOKIE);
}

export async function buildMutationRequestContext(source: RequestContextSource): Promise<MutationRequestContext> {
  const apiUrl = resolveApiUrl(source.sharedMap);
  const sessionCookie = readIncomingCookie(source, SESSION_COOKIE);
  const csrfToken = await resolveCsrfToken(source, apiUrl, sessionCookie);
  const csrfCookieToken = resolveCsrfCookieToken(source);
  const idempotencyKey = crypto.randomUUID();

  return {
    apiUrl,
    sessionCookie,
    csrfToken,
    csrfCookieToken,
    headers: createMutationHeaders(sessionCookie, csrfToken, csrfCookieToken, idempotencyKey),
    idempotencyKey,
  };
}

export function asServerRequestContext(
  contextOrSessionCookie: ServerRequestContext | string,
  apiUrl?: string,
  csrfToken = "",
): ServerRequestContext {
  if (typeof contextOrSessionCookie !== "string") {
    return contextOrSessionCookie;
  }

  return {
    apiUrl: apiUrl ?? DEFAULT_SERVER_API_URL,
    sessionCookie: contextOrSessionCookie,
    csrfToken,
    headers: createBaseHeaders(contextOrSessionCookie),
  };
}

export function asMutationRequestContext(
  contextOrSessionCookie: MutationRequestContext | string,
  apiUrl?: string,
  csrfToken = "",
  idempotencyKey?: string,
  csrfCookieToken = csrfToken,
): MutationRequestContext {
  if (typeof contextOrSessionCookie !== "string") {
    return contextOrSessionCookie;
  }

  const resolvedIdempotencyKey = idempotencyKey ?? "";

  return {
    apiUrl: apiUrl ?? DEFAULT_SERVER_API_URL,
    sessionCookie: contextOrSessionCookie,
    csrfToken,
    csrfCookieToken,
    headers: createMutationHeaders(
      contextOrSessionCookie,
      csrfToken,
      csrfCookieToken,
      resolvedIdempotencyKey || undefined,
    ),
    idempotencyKey: resolvedIdempotencyKey,
  };
}

export function mapRouteActionError<TError extends ErrorWithPayload>(
  error: unknown,
  ErrorType: ErrorConstructor<TError>,
  fail: FailFn,
  fallbackErrorCode: string,
) {
  if (error instanceof ErrorType) {
    return fail(400, { error: error.payload });
  }

  console.error("[mapRouteActionError] unexpected error:", error);
  return fail(500, {
    error: {
      title: "Ошибка",
      detail: "Неизвестная ошибка",
      errorCode: fallbackErrorCode,
    },
  });
}