const PLATFORM_ADMIN_CLAIMS = new Set(["role_admin", "admin"]);

export function hasPlatformAdminAccess(claims: readonly string[]): boolean {
  return claims.some((claim) =>
    PLATFORM_ADMIN_CLAIMS.has(claim.trim().toLowerCase()),
  );
}
