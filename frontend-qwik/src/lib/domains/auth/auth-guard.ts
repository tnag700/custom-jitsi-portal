import { AuthServiceError } from "./auth.service";

export const AUTH_PUBLIC_PATHS = ["/auth", "/auth/continue", "/invite"];

export function isPublicAuthPath(pathname: string): boolean {
  return AUTH_PUBLIC_PATHS.some(
    (publicPath) =>
      pathname === publicPath || pathname.startsWith(`${publicPath}/`),
  );
}

export function resolveAuthRedirectPath(error: unknown): string {
  if (error instanceof AuthServiceError) {
    const code = encodeURIComponent(error.payload.errorCode);
    return `/auth?error=${code}`;
  }
  return "/auth";
}
