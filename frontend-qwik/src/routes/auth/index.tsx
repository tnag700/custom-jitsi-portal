import { component$ } from "@qwik.dev/core";
import { routeLoader$ } from "@qwik.dev/router";
import {
  AuthErrorPanel,
  buildAuthLoginHref,
  mapAuthErrorCodeToPayload,
  resolveAuthRedirectPath,
  resolvePostAuthRedirectPath,
  shouldAutoResumeAuth,
} from "~/lib/domains/auth";

const DEFAULT_PUBLIC_API_URL = "http://localhost:8080/api/v1";

export const useAuthPage = routeLoader$(({ sharedMap, url, env, redirect }) => {
  const returnTo = url.searchParams.get("returnTo");
  if (sharedMap.get("user")) {
    throw redirect(302, resolvePostAuthRedirectPath(returnTo));
  }

  const apiUrl =
    env.get("PUBLIC_API_URL") || env.get("API_URL") || DEFAULT_PUBLIC_API_URL;
  const errorCode = url.searchParams.get("error");
  const mode = url.searchParams.get("mode");
  const loginHref = buildAuthLoginHref(apiUrl, returnTo);

  if (shouldAutoResumeAuth(errorCode, mode)) {
    throw redirect(302, loginHref);
  }

  return {
    loginHref,
    retryHref: resolveAuthRedirectPath(undefined, returnTo),
    error: errorCode ? mapAuthErrorCodeToPayload(errorCode) : null,
  };
});

export default component$(() => {
  const data = useAuthPage();

  return (
    <section class="mx-auto flex w-full max-w-xl flex-col gap-6 py-10">
      {data.value.error && (
        <AuthErrorPanel
          error={data.value.error}
          retryHref={data.value.retryHref}
        />
      )}

      <div class="rounded border border-border bg-surface p-6 text-center">
        <h1 class="text-xl font-semibold text-text">Вход в портал</h1>
        <p class="mt-2 text-sm text-muted">
          Используйте корпоративный SSO для безопасного входа.
        </p>

        <div class="mt-6">
          <a
            href={data.value.loginHref}
            class="inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-opacity hover:opacity-90 focus-visible:outline focus-visible:outline-3 focus-visible:outline-primary focus-visible:outline-offset-2"
          >
            Войти через SSO
          </a>
        </div>
      </div>
    </section>
  );
});
