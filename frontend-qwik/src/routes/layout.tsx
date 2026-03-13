import {
  component$,
  useSignal,
  useStore,
  useContextProvider,
  $,
  Slot,
} from "@qwik.dev/core";
import { routeAction$, routeLoader$, type RequestHandler } from "@qwik.dev/router";
import { ThemeContext, type Theme } from "~/lib/shared/stores/theme-context";
import { AppHeader, Sidebar } from "~/lib/shared/components";
import {
  AuthContext,
  fetchAuthMe,
  isPublicAuthPath,
  logoutFromAuthSession,
  resolveAuthRedirectPath,
  type AuthStore,
  type SafeUserProfile,
} from "~/lib/domains/auth";
import {
  buildMutationRequestContext,
  buildServerRequestContext,
} from "~/lib/shared/routes/server-handlers";

const THEME_COOKIE = "theme";
const THEME_COOKIE_MAX_AGE = 365 * 24 * 60 * 60;
const DEFAULT_SERVER_API_URL = "http://localhost:8080/api/v1";
const DEFAULT_PUBLIC_API_URL = "http://localhost:8080/api/v1";

/** Read theme cookie on the server and pass it via sharedMap → routeLoader$. */
export const onRequest: RequestHandler = async ({
  cookie,
  sharedMap,
  env,
  url,
  request,
  redirect,
}) => {
  const raw = cookie.get(THEME_COOKIE)?.value;
  const theme: Theme =
    raw === "dark" || raw === "light" ? raw : "light";
  sharedMap.set("theme", theme);
  cookie.set(THEME_COOKIE, theme, {
    path: "/",
    maxAge: THEME_COOKIE_MAX_AGE,
    sameSite: "lax",
  });

  const apiUrl = env.get("API_URL") || DEFAULT_SERVER_API_URL;
  const publicApiUrl = env.get("PUBLIC_API_URL") || env.get("API_URL") || DEFAULT_PUBLIC_API_URL;
  sharedMap.set("apiUrl", apiUrl);
  sharedMap.set("publicApiUrl", publicApiUrl);
  sharedMap.set("requestCookieHeader", request.headers.get("cookie") ?? "");

  if (isPublicAuthPath(url.pathname)) {
    sharedMap.set("user", null);
    return;
  }

  const requestContext = buildServerRequestContext({ sharedMap, cookie });
  if (!requestContext.sessionCookie) {
    throw redirect(302, "/auth");
  }

  await buildMutationRequestContext({ sharedMap, cookie });

  try {
    const profile = await fetchAuthMe(requestContext);
    sharedMap.set("user", profile);
  } catch (error) {
    throw redirect(302, resolveAuthRedirectPath(error));
  }
};

export const useTheme = routeLoader$(({ sharedMap }) => {
  return (sharedMap.get("theme") as Theme) || "light";
});

export const useAuth = routeLoader$(({ sharedMap }) => {
  return (sharedMap.get("user") as SafeUserProfile | null) ?? null;
});

export const useLogout = routeAction$(async (_, { sharedMap, cookie, redirect }) => {
  const requestContext = await buildMutationRequestContext({ sharedMap, cookie });

  try {
    const location = await logoutFromAuthSession(requestContext);
    throw redirect(302, location);
  } catch (error) {
    if (
      typeof error === "object" &&
      error !== null &&
      "type" in error &&
      (error as { type?: unknown }).type === "redirect"
    ) {
      throw error;
    }

    throw redirect(302, resolveAuthRedirectPath(error));
  }
});

export default component$(() => {
  const themeData = useTheme();
  const authData = useAuth();
  const logoutAction = useLogout();
  const theme = useSignal<Theme>(themeData.value);
  const expanded = useSignal(true);
  const authStore = useStore<AuthStore>({
    isAuthenticated: !!authData.value,
    profile: authData.value,
    error: null,
  });

  const toggle$ = $(() => {
    theme.value = theme.value === "dark" ? "light" : "dark";
    document.documentElement.classList.toggle("dark", theme.value === "dark");
    document.cookie = `${THEME_COOKIE}=${theme.value};path=/;max-age=${THEME_COOKIE_MAX_AGE};SameSite=Lax`;
  });

  useContextProvider(ThemeContext, { theme, toggle$ });
  useContextProvider(AuthContext, authStore);

  const toggleSidebar$ = $(() => {
    expanded.value = !expanded.value;
  });

  return (
    <div class="flex h-screen overflow-hidden bg-bg text-text">
      {authStore.isAuthenticated ? (
        <>
          <Sidebar expanded={expanded} />
          <div class="flex flex-1 flex-col overflow-hidden">
            <AppHeader
              isAuthenticated={authStore.isAuthenticated}
              showSidebarToggle={true}
              isSidebarExpanded={expanded.value}
              userDisplayName={authStore.profile?.displayName ?? null}
              logoutAction={logoutAction}
              onToggleSidebar$={toggleSidebar$}
            />
            <main class="flex-1 overflow-y-auto p-6">
              <div class="mx-auto max-w-6xl">
                <Slot />
              </div>
            </main>
          </div>
        </>
      ) : (
        <>
          <div class="flex flex-1 flex-col overflow-hidden">
            <AppHeader
              isAuthenticated={false}
              showSidebarToggle={false}
              isSidebarExpanded={expanded.value}
              userDisplayName={null}
              logoutAction={logoutAction}
              onToggleSidebar$={toggleSidebar$}
            />
            <main class="flex-1 overflow-y-auto p-6">
              <div class="mx-auto max-w-6xl">
                <Slot />
              </div>
            </main>
          </div>
        </>
      )}
    </div>
  );
});
