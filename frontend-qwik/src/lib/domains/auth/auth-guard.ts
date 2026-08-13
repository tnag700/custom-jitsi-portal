import { AuthServiceError } from "./auth.service";
import { isInviteBearerPath } from "~/lib/shared/security";

export const AUTH_PUBLIC_PATHS = ["/auth", "/auth/continue", "/invite"];
const AUTH_PATH = "/auth";
const AUTH_RECOVERY_MODE = "recover";
const DEFAULT_POST_AUTH_PATH = "/";
const RETURN_TO_VALIDATION_PASSES = 8;

export function isPublicAuthPath(pathname: string): boolean {
  if (isInviteBearerPath(pathname)) {
    return true;
  }
  return AUTH_PUBLIC_PATHS.some(
    (publicPath) =>
      publicPath !== "/invite" &&
      (pathname === publicPath || pathname.startsWith(`${publicPath}/`)),
  );
}

function sanitizeAuthReturnTo(returnTo: string | null | undefined): string | null {
  if (!returnTo) {
    return null;
  }

  let valueToValidate = returnTo;
  // Expose nested encodings introduced across backend/query/browser boundaries, with bounded work.
  for (let pass = 0; pass < RETURN_TO_VALIDATION_PASSES; pass += 1) {
    if (!isSafeRelativePath(valueToValidate)) {
      return null;
    }

    const decodedValue = decodePercentEncodedAscii(valueToValidate, pass === 0);
    if (decodedValue === null) {
      return null;
    }
    if (decodedValue === valueToValidate) {
      return returnTo;
    }
    valueToValidate = decodedValue;
  }

  return null;
}

function isSafeRelativePath(value: string): boolean {
  if (!value.startsWith("/") || value.startsWith("//")) {
    return false;
  }

  for (let index = 0; index < value.length; index += 1) {
    const codePoint = value.charCodeAt(index);
    if (value[index] === "\\" || codePoint <= 0x1f || codePoint === 0x7f) {
      return false;
    }
  }
  return true;
}

function decodePercentEncodedAscii(
  value: string,
  rejectMalformedEscape: boolean,
): string | null {
  let decoded = "";
  for (let index = 0; index < value.length; index += 1) {
    if (value[index] !== "%") {
      decoded += value[index];
      continue;
    }

    const high = index + 1 < value.length ? hexDigitValue(value.charCodeAt(index + 1)) : -1;
    const low = index + 2 < value.length ? hexDigitValue(value.charCodeAt(index + 2)) : -1;
    if (high < 0 || low < 0) {
      if (rejectMalformedEscape) {
        return null;
      }
      decoded += value[index];
      continue;
    }

    const byteValue = high * 16 + low;
    decoded += byteValue <= 0x7f ? String.fromCharCode(byteValue) : value.slice(index, index + 3);
    index += 2;
  }
  return decoded;
}

function hexDigitValue(codePoint: number): number {
  if (codePoint >= 0x30 && codePoint <= 0x39) {
    return codePoint - 0x30;
  }
  if (codePoint >= 0x41 && codePoint <= 0x46) {
    return codePoint - 0x41 + 10;
  }
  if (codePoint >= 0x61 && codePoint <= 0x66) {
    return codePoint - 0x61 + 10;
  }
  return -1;
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
