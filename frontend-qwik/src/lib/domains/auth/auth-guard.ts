import { AuthServiceError } from "./auth.service";

export const AUTH_PUBLIC_PATHS = ["/auth", "/auth/continue", "/invite"];
const AUTH_PATH = "/auth";
const AUTH_RECOVERY_MODE = "recover";
const DEFAULT_POST_AUTH_PATH = "/";

export function isPublicAuthPath(pathname: string): boolean {
  return AUTH_PUBLIC_PATHS.some(
    (publicPath) =>
      pathname === publicPath || pathname.startsWith(`${publicPath}/`),
  );
}

function sanitizeAuthReturnTo(returnTo: string | null | undefined): string | null {
  if (!returnTo) {
    return null;
  }
  const trimmed = returnTo.trim();
  if (!trimmed.startsWith("/") || trimmed.startsWith("//")) {
    return null;
  }
  return trimmed;
}

function buildAuthPath(
  errorCode: string | null,
  returnTo?: string | null,
  mode?: string | null,
): string {
  const query = new URLSearchParams();
  if (errorCode && errorCode.trim().length > 0) {
    query.set("error", errorCode);
  }
  if (mode && mode.trim().length > 0) {
    query.set("mode", mode);
  }
  const safeReturnTo = sanitizeAuthReturnTo(returnTo);
  if (safeReturnTo) {
    query.set("returnTo", safeReturnTo);
  }
  const search = query.toString();
  return search.length > 0 ? `${AUTH_PATH}?${search}` : AUTH_PATH;
}

export function resolveAuthRedirectPath(error: unknown, returnTo?: string | null): string {
  const errorCode = error instanceof AuthServiceError ? error.payload.errorCode : null;
  return buildAuthPath(errorCode, returnTo);
}

export function resolveAuthRecoveryRedirectPath(error: unknown, returnTo?: string | null): string {
  const errorCode = error instanceof AuthServiceError ? error.payload.errorCode : null;
  return buildAuthPath(errorCode, returnTo, AUTH_RECOVERY_MODE);
}

export function shouldAutoResumeAuth(
  errorCode: string | null | undefined,
  mode: string | null | undefined,
): boolean {
  return mode === AUTH_RECOVERY_MODE && (!errorCode || errorCode === "AUTH_REQUIRED");
}

export function buildAuthLoginHref(apiUrl: string, returnTo?: string | null): string {
  const normalizedApiUrl = apiUrl.endsWith("/") ? apiUrl.slice(0, -1) : apiUrl;
  const safeReturnTo = sanitizeAuthReturnTo(returnTo);
  if (!safeReturnTo) {
    return `${normalizedApiUrl}/auth/login`;
  }
  return `${normalizedApiUrl}/auth/login?${new URLSearchParams({ returnTo: safeReturnTo }).toString()}`;
}

export function resolvePostAuthRedirectPath(returnTo?: string | null): string {
  return sanitizeAuthReturnTo(returnTo) ?? DEFAULT_POST_AUTH_PATH;
}
