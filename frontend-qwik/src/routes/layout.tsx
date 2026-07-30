import {
  component$,
  useSignal,
  useStore,
  useTask$,
  useContextProvider,
  useOnDocument,
  $,
  Slot,
} from "@qwik.dev/core";
import {
  routeAction$,
  routeLoader$,
  type RequestHandler,
} from "@qwik.dev/router";
import { ThemeContext, type Theme } from "~/lib/shared/stores/theme-context";
import { AppHeader, Sidebar } from "~/lib/shared/components";
import {
  AuthContext,
  AuthServiceError,
  fetchAuthMe,
  isPublicAuthPath,
  logoutFromAuthSession,
  resolveAuthRecoveryRedirectPath,
  resolveAuthRedirectPath,
  synchronizeAuthStore,
  type AuthStore,
  type SafeUserProfile,
} from "~/lib/domains/auth";
import {
  filterNavItemsForClaims,
  navItems,
} from "~/lib/shared/components/sidebar-nav-items";
import {
  buildMutationRequestContext,
  buildServerRequestContext,
} from "~/lib/shared/routes/server-handlers";

const THEME_COOKIE = "theme";
const THEME_COOKIE_MAX_AGE = 365 * 24 * 60 * 60;
const DEFAULT_SERVER_API_URL = "http://localhost:8080/api/v1";
const DEFAULT_PUBLIC_API_URL = "http://localhost:8080/api/v1";
const AUTH_LOGOUT_ALLOWED_ORIGINS_ENV = "AUTH_LOGOUT_ALLOWED_ORIGINS";
const AUTH_LOGOUT_ALLOW_INSECURE_PRIVATE_ORIGIN_ENV =
  "AUTH_LOGOUT_ALLOW_INSECURE_PRIVATE_ORIGIN";

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
  const theme: Theme = raw === "dark" || raw === "light" ? raw : "light";
  sharedMap.set("theme", theme);
  cookie.set(THEME_COOKIE, theme, {
    path: "/",
    maxAge: THEME_COOKIE_MAX_AGE,
    sameSite: "lax",
  });

  const apiUrl = env.get("API_URL") || DEFAULT_SERVER_API_URL;
  const publicApiUrl =
    env.get("PUBLIC_API_URL") || env.get("API_URL") || DEFAULT_PUBLIC_API_URL;
  sharedMap.set("apiUrl", apiUrl);
  sharedMap.set("publicApiUrl", publicApiUrl);
  sharedMap.set("requestCookieHeader", request.headers.get("cookie") ?? "");

  if (isPublicAuthPath(url.pathname)) {
    sharedMap.set("user", null);
    return;
  }

  const returnTo = `${url.pathname}${url.search}`;

  const requestContext = buildServerRequestContext({ sharedMap, cookie });
  if (!requestContext.sessionCookie) {
    throw redirect(302, resolveAuthRecoveryRedirectPath(undefined, returnTo));
  }

  try {
    const profile = await fetchAuthMe(requestContext);
    sharedMap.set("user", profile);
  } catch (error) {
    if (
      error instanceof AuthServiceError &&
      error.payload.errorCode === "AUTH_REQUIRED"
    ) {
      throw redirect(302, resolveAuthRecoveryRedirectPath(error, returnTo));
    }
    throw redirect(302, resolveAuthRedirectPath(error, returnTo));
  }
};

export const useTheme = routeLoader$(({ sharedMap }) => {
  return (sharedMap.get("theme") as Theme) || "light";
});

export const useAuth = routeLoader$(({ sharedMap }) => {
  return (sharedMap.get("user") as SafeUserProfile | null) ?? null;
});

function parseAllowedLogoutOrigins(
  raw: string | undefined,
  currentOrigin: string,
): Set<string> {
  const allowedOrigins = new Set<string>([currentOrigin]);
  if (!raw) {
    return allowedOrigins;
  }
  for (const candidate of raw.split(",")) {
    const trimmed = candidate.trim();
    if (!trimmed) {
      continue;
    }
    try {
      const parsed = new URL(trimmed);
      allowedOrigins.add(parsed.origin);
    } catch {
      // Ignore malformed entries and keep strict fallback behavior.
    }
  }
  return allowedOrigins;
}

function isAllowedLogoutRedirect(
  location: string,
  currentUrl: URL,
  allowedOriginsRaw: string | undefined,
  allowInsecurePrivateOrigin: boolean,
): boolean {
  let parsed: URL;
  try {
    parsed = new URL(location, currentUrl.origin);
  } catch {
    return false;
  }

  const allowedOrigins = parseAllowedLogoutOrigins(
    allowedOriginsRaw,
    currentUrl.origin,
  );
  if (!allowedOrigins.has(parsed.origin)) {
    return false;
  }
  if (parsed.origin !== currentUrl.origin && parsed.protocol !== "https:") {
    if (
      !allowInsecurePrivateOrigin ||
      parsed.protocol !== "http:" ||
      !isPrivateHostname(parsed.hostname)
    ) {
      return false;
    }
  }
  return parsed.protocol === "http:" || parsed.protocol === "https:";
}

function isPrivateHostname(hostname: string): boolean {
  const normalized = hostname.trim().toLowerCase();
  if (
    normalized === "localhost" ||
    normalized === "::1" ||
    normalized === "[::1]"
  ) {
    return true;
  }

  const octets = normalized.split(".").map(Number);
  if (
    octets.length !== 4 ||
    octets.some((octet) => !Number.isInteger(octet) || octet < 0 || octet > 255)
  ) {
    return false;
  }

  return (
    octets[0] === 10 ||
    (octets[0] === 172 && octets[1] >= 16 && octets[1] <= 31) ||
    (octets[0] === 192 && octets[1] === 168) ||
    octets[0] === 127
  );
}

export const useLogout = routeAction$(
  async (_, { sharedMap, cookie, redirect, url, env }) => {
    const requestContext = await buildMutationRequestContext({
      sharedMap,
      cookie,
    });
    const returnTo = `${url.pathname}${url.search}`;

    try {
      const location = await logoutFromAuthSession(requestContext);
      if (
        !isAllowedLogoutRedirect(
          location,
          url,
          env.get(AUTH_LOGOUT_ALLOWED_ORIGINS_ENV),
          env
            .get(AUTH_LOGOUT_ALLOW_INSECURE_PRIVATE_ORIGIN_ENV)
            ?.trim()
            .toLowerCase() === "true",
        )
      ) {
        throw new AuthServiceError({
          title: "Некорректный redirect logout",
          reason: "Logout redirect origin is not allowed.",
          actions: "Повторите вход через SSO или обратитесь к администратору.",
          errorCode: "AUTH_LOGOUT_REDIRECT_INVALID",
        });
      }
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

      throw redirect(302, resolveAuthRedirectPath(error, returnTo));
    }
  },
);

export default component$(() => {
  const themeData = useTheme();
  const authData = useAuth();
  const logoutAction = useLogout();
  const theme = useSignal<Theme>(themeData.value);
  const desktopSidebarExpanded = useSignal(true);
  const mobileSidebarOpen = useSignal(false);
  const authStore = useStore<AuthStore>({
    isAuthenticated: !!authData.value,
    profile: authData.value,
    error: null,
  });

  useTask$(({ track }) => {
    const profile = track(() => authData.value);
    synchronizeAuthStore(authStore, profile);
  });

  const toggle$ = $(() => {
    theme.value = theme.value === "dark" ? "light" : "dark";
    document.documentElement.classList.toggle("dark", theme.value === "dark");
    document.cookie = `${THEME_COOKIE}=${theme.value};path=/;max-age=${THEME_COOKIE_MAX_AGE};SameSite=Lax`;
  });

  useContextProvider(ThemeContext, { theme, toggle$ });
  useContextProvider(AuthContext, authStore);
  const visibleNavItems = filterNavItemsForClaims(
    navItems,
    authStore.profile?.claims ?? [],
  );

  const toggleMobileSidebar$ = $(() => {
    mobileSidebarOpen.value = !mobileSidebarOpen.value;
  });

  const closeMobileSidebar$ = $(() => {
    mobileSidebarOpen.value = false;
  });

  useOnDocument(
    "keydown",
    $((event) => {
      if (event instanceof KeyboardEvent && event.key === "Escape") {
        mobileSidebarOpen.value = false;
      }
    }),
  );

  return (
    <div class="flex h-screen overflow-hidden bg-bg text-text">
      {authStore.isAuthenticated ? (
        <>
          <Sidebar
            expanded={desktopSidebarExpanded}
            items={visibleNavItems}
            variant="desktop"
          />
          {mobileSidebarOpen.value && (
            <>
              <button
                type="button"
                class="fixed inset-0 z-40 bg-black/45 lg:hidden"
                aria-label="Закрыть мобильное меню"
                onClick$={closeMobileSidebar$}
              />
              <Sidebar
                expanded={desktopSidebarExpanded}
                items={visibleNavItems}
                variant="mobile"
                onDismiss$={closeMobileSidebar$}
              />
            </>
          )}
          <div class="flex min-w-0 flex-1 flex-col overflow-hidden">
            <AppHeader
              isAuthenticated={authStore.isAuthenticated}
              showSidebarToggle={true}
              isSidebarExpanded={desktopSidebarExpanded.value}
              isMobileSidebarOpen={mobileSidebarOpen.value}
              userDisplayName={authStore.profile?.displayName ?? null}
              logoutAction={logoutAction}
              onToggleSidebar$={toggleMobileSidebar$}
            />
            <main class="flex-1 overflow-y-auto p-4 sm:p-6">
              <div class="mx-auto max-w-6xl">
                <Slot />
              </div>
            </main>
          </div>
        </>
      ) : (
        <>
          <div class="flex min-w-0 flex-1 flex-col overflow-hidden">
            <AppHeader
              isAuthenticated={false}
              showSidebarToggle={false}
              isSidebarExpanded={desktopSidebarExpanded.value}
              userDisplayName={null}
              logoutAction={logoutAction}
              onToggleSidebar$={toggleMobileSidebar$}
            />
            <main class="flex-1 overflow-y-auto p-4 sm:p-6">
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
