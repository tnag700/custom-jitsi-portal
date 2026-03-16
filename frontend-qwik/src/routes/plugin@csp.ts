import { isDev } from "@qwik.dev/core";
import type { RequestHandler } from "@qwik.dev/router";
import {
  buildDocumentContentSecurityPolicy,
  createCspNonce,
  resolveDocumentApiUrl,
  shouldApplyDocumentSecurityHeaders,
  STATIC_SECURITY_HEADERS,
} from "~/lib/shared/security/csp";

const DEFAULT_SERVER_API_URL = "http://localhost:8080/api/v1";
const DEFAULT_PUBLIC_API_URL = "http://localhost:8080/api/v1";

function readConfiguredUrl(value: string | null | undefined): string | undefined {
  return typeof value === "string" && value.trim().length > 0 ? value : undefined;
}

export const onRequest: RequestHandler = ({ env, headers, request, sharedMap, url }) => {
  if (isDev || !shouldApplyDocumentSecurityHeaders(request.method, url.pathname)) {
    return;
  }

  const nonce = createCspNonce();
  const apiUrl = readConfiguredUrl(env.get("API_URL"));
  const publicApiUrl =
    readConfiguredUrl(env.get("PUBLIC_API_URL"))
    ?? apiUrl
    ?? DEFAULT_PUBLIC_API_URL;
  const documentApiUrl = resolveDocumentApiUrl({
    requestUrl: url,
    apiUrl,
    publicApiUrl,
    localDefaultApiUrl: DEFAULT_SERVER_API_URL,
  });

  sharedMap.set("@nonce", nonce);

  for (const [headerName, headerValue] of Object.entries(STATIC_SECURITY_HEADERS)) {
    headers.set(headerName, headerValue);
  }

  headers.set(
    "Content-Security-Policy",
    buildDocumentContentSecurityPolicy({
      nonce,
      apiUrl: documentApiUrl,
      publicApiUrl,
      extraConnectSrc: readConfiguredUrl(env.get("FRONTEND_CSP_CONNECT_SRC")) ?? "",
    }),
  );
};