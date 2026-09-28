import createClient from "openapi-fetch";
import type { paths } from "./generated/api-types";

export function fetchWithTimeout(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const callerSignal = init?.signal ?? (input instanceof Request ? input.signal : undefined);
  const deadline = AbortSignal.timeout(10_000);
  return fetch(input, {
    ...init,
    signal: callerSignal ? AbortSignal.any([callerSignal, deadline]) : deadline,
  });
}

function toHeaderMap(headers: Headers): Record<string, string> {
  const map: Record<string, string> = {};

  for (const [rawKey, value] of headers.entries()) {
    const key = rawKey.toLowerCase();

    if (key === "content-type") {
      map["Content-Type"] = value;
      continue;
    }
    if (key === "cookie") {
      map.Cookie = value;
      continue;
    }
    if (key === "x-xsrf-token") {
      map["X-XSRF-TOKEN"] = value;
      continue;
    }
    if (key === "idempotency-key") {
      map["Idempotency-Key"] = value;
      continue;
    }

    map[rawKey] = value;
  }

  return map;
}

// openapi-fetch passes headers as a Headers object inside a Request when using its internal
// middleware pipeline. Qwik SSR Node.js environment serializes Headers differently from plain
// Record<string,string>. fetchCompat converts a Request back to a plain fetch() call so that
// Cookie / X-XSRF-TOKEN headers survive the round-trip to the backend.
async function fetchCompat(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  if (!(input instanceof Request)) {
    return fetchWithTimeout(input, init);
  }

  input = init ? new Request(input, init) : input;

  const method = input.method.toUpperCase();
  const headers = toHeaderMap(input.headers);
  const hasBody = method !== "GET" && method !== "HEAD";
  const body = hasBody ? await input.clone().text() : undefined;

  const options: RequestInit = {
    method,
    headers,
    signal: input.signal,
    credentials: input.credentials,
    mode: input.mode,
    cache: input.cache,
    redirect: input.redirect,
    referrer: input.referrer,
    referrerPolicy: input.referrerPolicy,
    integrity: input.integrity,
    keepalive: input.keepalive,
  };

  if (body && body.length > 0) {
    options.body = body;
  }

  return fetchWithTimeout(input.url, options);
}

// openapi-fetch baseUrl must NOT end with /api/v1 because path params like
// "/api/v1/rooms" would resolve to the correct URL only if the host is bare.
// If the env var contains the full prefix (e.g. http://backend:8080/api/v1),
// we strip it so openapi-fetch path templates resolve correctly.
function normalizeBaseUrl(baseUrl: string): string {
  return baseUrl.replace(/\/api\/v1\/?$/, "");
}

export function createApiClient(baseUrl: string, headers?: Record<string, string>) {
  return createClient<paths>({ baseUrl: normalizeBaseUrl(baseUrl), headers, fetch: fetchCompat });
}

/** Alias for {@link createApiClient}. Call as `apiClient(baseUrl, headers?)` to get a typed client instance. */
export const apiClient = createApiClient;

export type TypedApiClient = ReturnType<typeof createApiClient>;
