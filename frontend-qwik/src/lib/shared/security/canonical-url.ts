import { isInviteBearerPath } from "./sensitive-route";

export function resolveCanonicalHref(url: URL): string | null {
  if (isInviteBearerPath(url.pathname)) {
    return null;
  }

  const canonicalUrl = new URL(url);
  canonicalUrl.hash = "";
  return canonicalUrl.href;
}
