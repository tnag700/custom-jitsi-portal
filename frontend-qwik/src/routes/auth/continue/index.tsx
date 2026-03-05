import { component$ } from "@qwik.dev/core";
import { routeLoader$ } from "@qwik.dev/router";
import { AuthServiceError, fetchAuthMe } from "~/lib/domains/auth";

const DEFAULT_API_URL = "http://localhost:8080/api/v1";

export const useAuthContinue = routeLoader$(
  async ({ cookie, env, sharedMap, redirect }) => {
    const sessionCookie = cookie.get("JSESSIONID")?.value;
    if (!sessionCookie) {
      throw redirect(302, "/auth?error=AUTH_REQUIRED");
    }

    const apiUrl = env.get("API_URL") || DEFAULT_API_URL;

    let profile;
    try {
      profile = await fetchAuthMe(sessionCookie, apiUrl);
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
