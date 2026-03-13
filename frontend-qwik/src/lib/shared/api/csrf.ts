function isNonEmptyString(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

export interface CsrfTokenPair {
  requestToken: string;
  cookieToken: string;
  headerName: string;
}

function emptyCsrfTokenPair(): CsrfTokenPair {
  return {
    requestToken: "",
    cookieToken: "",
    headerName: "X-XSRF-TOKEN",
  };
}

function extractCookieToken(setCookieHeader: string): string {
  const match = /^XSRF-TOKEN=([^;]+)/i.exec(setCookieHeader.trim());
  return match?.[1] ?? "";
}

function readSetCookieHeaders(headers: Headers): string[] {
  const withGetSetCookie = headers as Headers & { getSetCookie?: () => string[] };
  if (typeof withGetSetCookie.getSetCookie === "function") {
    return withGetSetCookie.getSetCookie();
  }

  const setCookie = headers.get("set-cookie");
  return setCookie ? [setCookie] : [];
}

export async function fetchCsrfTokenPair(
  sessionCookie: string,
  apiUrl: string,
): Promise<CsrfTokenPair> {
  if (!isNonEmptyString(sessionCookie)) {
    return emptyCsrfTokenPair();
  }

  const response = await fetch(`${apiUrl}/auth/csrf`, {
    method: "GET",
    headers: {
      Cookie: `JSESSIONID=${sessionCookie}`,
    },
  });

  if (!response.ok) {
    return emptyCsrfTokenPair();
  }

  const payload = (await response.json()) as { token?: unknown; headerName?: unknown };
  const requestToken = isNonEmptyString(payload.token) ? payload.token : "";
  const headerName = isNonEmptyString(payload.headerName) ? payload.headerName : "X-XSRF-TOKEN";
  const cookieToken = readSetCookieHeaders(response.headers)
    .map(extractCookieToken)
    .find(isNonEmptyString) ?? "";

  return {
    requestToken,
    cookieToken,
    headerName,
  };
}

export async function fetchCsrfToken(
  sessionCookie: string,
  apiUrl: string,
): Promise<string> {
  const pair = await fetchCsrfTokenPair(sessionCookie, apiUrl);
  return pair.requestToken;
}