const INVITE_PATH_PATTERN = /^\/invite(?:\/|$)/i;
const MAX_PATH_DECODE_PASSES = 8;

/**
 * Mirrors the router's case-insensitive, percent-decoded route classification
 * before deciding whether a bearer invite path may enter generic auth or
 * metadata flows. The original path is never returned or logged here.
 */
export function isInviteBearerPath(pathname: string): boolean {
  let candidate = pathname;

  for (let pass = 0; pass < MAX_PATH_DECODE_PASSES; pass += 1) {
    const routerNormalizedCandidate = collapseLeadingSlashes(candidate);
    if (INVITE_PATH_PATTERN.test(routerNormalizedCandidate)) {
      return true;
    }

    let decoded: string;
    try {
      decoded = decodeURIComponent(candidate);
    } catch {
      return false;
    }
    if (decoded === candidate) {
      return false;
    }
    candidate = decoded;
  }

  return INVITE_PATH_PATTERN.test(collapseLeadingSlashes(candidate));
}

function collapseLeadingSlashes(pathname: string): string {
  return pathname.startsWith("/") ? pathname.replace(/^\/+/, "/") : pathname;
}
