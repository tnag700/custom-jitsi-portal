import { component$ } from "@qwik.dev/core";
import { routeLoader$ } from "@qwik.dev/router";
import { AuthServiceError, fetchAuthMe } from "~/lib/domains/auth";
import { buildServerRequestContext } from "~/lib/shared/routes/server-handlers";

export const useAuthContinue = routeLoader$(
  async ({ cookie, sharedMap, redirect }) => {
    const requestContext = buildServerRequestContext({ sharedMap, cookie });
    if (!requestContext.sessionCookie) {
      throw redirect(302, "/auth?error=AUTH_REQUIRED");
    }

    let profile;
    try {
      profile = await fetchAuthMe(requestContext);
    } catch (error) {
      if (error instanceof AuthServiceError) {
        const errorCode = encodeURIComponent(error.payload.errorCode);
        throw redirect(302, `/auth?error=${errorCode}`);
      }
      throw redirect(302, "/auth?error=AUTH_SERVICE_UNAVAILABLE");
    }

    sharedMap.set("user", profile);
    throw redirect(302, "/");
  },
);

export default component$(() => {
  useAuthContinue();

  return (
    <section class="mx-auto flex w-full max-w-md flex-col items-center justify-center py-12">
      <div class="w-full rounded border border-border bg-surface p-6 text-center">
        <div
          class="mx-auto h-8 w-8 animate-spin rounded-full border-2 border-border border-t-primary"
          aria-hidden="true"
        />
        <p class="mt-4 text-sm text-muted">Выполняется вход...</p>
      </div>
    </section>
  );
});
