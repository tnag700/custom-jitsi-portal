import { randomBytes } from "node:crypto";

const STATIC_FILE_PREFIXES = ["/assets/", "/build/"];
const STATIC_FILE_PATHS = new Set(["/favicon.svg", "/manifest.json", "/robots.txt"]);

export const STATIC_SECURITY_HEADERS = Object.freeze({
  "Permissions-Policy": "camera=(self), microphone=(self), geolocation=()",
  "Referrer-Policy": "no-referrer",
  "X-Content-Type-Options": "nosniff",
  "X-Frame-Options": "DENY",
});

export function createCspNonce(): string {
  return randomBytes(16).toString("base64");
}

export function shouldApplyDocumentSecurityHeaders(method: string, pathName: string): boolean {
  if (method !== "GET" && method !== "HEAD") {
    return false;
  }

  if (!pathName || STATIC_FILE_PATHS.has(pathName)) {
    return false;
  }

  return !STATIC_FILE_PREFIXES.some((prefix) => pathName.startsWith(prefix));
}

export function buildDocumentContentSecurityPolicy({
  nonce,
  apiUrl,
  publicApiUrl,
  extraConnectSrc = "",
}: {
  nonce: string;
  apiUrl?: string;
  publicApiUrl?: string;
  extraConnectSrc?: string;
}): string {
  const connectSrc = buildConnectSrc(apiUrl, publicApiUrl, extraConnectSrc);

  return [
    "default-src 'none'",
    "base-uri 'none'",
    "form-action 'self'",
    "frame-ancestors 'none'",
    "object-src 'none'",
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'`,
    "style-src 'self' 'unsafe-inline'",
    "style-src-attr 'unsafe-inline'",
    "img-src 'self' data: blob:",
    "font-src 'self'",
    `connect-src ${connectSrc}`,
    "manifest-src 'self'",
  ].join("; ");
}

export function resolveDocumentApiUrl({
  requestUrl,
  apiUrl,
  publicApiUrl,
  localDefaultApiUrl,
}: {
  requestUrl: URL;
  apiUrl?: string;
  publicApiUrl?: string;
  localDefaultApiUrl: string;
}): string | undefined {
  if (hasAbsoluteOrigin(apiUrl)) {
    return apiUrl;
  }

  if (hasAbsoluteOrigin(publicApiUrl)) {
    return publicApiUrl;
  }

  if (isLocalHost(requestUrl.hostname)) {
    return localDefaultApiUrl;
  }

  return apiUrl ?? publicApiUrl;
}

function buildConnectSrc(apiUrl?: string, publicApiUrl?: string, extraConnectSrc?: string): string {
  const connectSrc = ["'self'"];

  appendOrigin(connectSrc, publicApiUrl);
  appendOrigin(connectSrc, apiUrl);

  for (const source of parseExtraSources(extraConnectSrc)) {
    appendUnique(connectSrc, source);
  }

  appendUnique(connectSrc, "https:");
  appendUnique(connectSrc, "wss:");

  return connectSrc.join(" ");
}

function appendOrigin(target: string[], rawUrl?: string): void {
  if (!rawUrl) {
    return;
  }

  try {
    appendUnique(target, new URL(rawUrl).origin);
  } catch {
    // Ignore malformed operator configuration and keep a safe fallback policy.
  }
}

function parseExtraSources(rawValue?: string): string[] {
  if (!rawValue) {
    return [];
  }

  return rawValue
    .split(/[\s,]+/)
    .map((value) => value.trim())
    .filter(Boolean);
}

function appendUnique(target: string[], value: string): void {
  if (!target.includes(value)) {
    target.push(value);
  }
}

function hasAbsoluteOrigin(rawUrl?: string): boolean {
  if (!rawUrl) {
    return false;
  }

  try {
    new URL(rawUrl);
    return true;
  } catch {
    return false;
  }
}

function isLocalHost(hostname: string): boolean {
  return hostname === "localhost" || hostname === "127.0.0.1" || hostname === "::1";
}