import { component$, Slot } from "@qwik.dev/core";
import type { RequestHandler } from "@qwik.dev/router";
import type { SafeUserProfile } from "~/lib/domains/auth";
import { hasPlatformAdminAccess } from "~/lib/shared/security/access-claims";

export const onRequest: RequestHandler = ({ sharedMap, redirect }) => {
  const user = (sharedMap.get("user") as SafeUserProfile | null) ?? null;
  if (!user || !hasPlatformAdminAccess(user.claims)) {
    throw redirect(302, "/");
  }
};

export default component$(() => <Slot />);
