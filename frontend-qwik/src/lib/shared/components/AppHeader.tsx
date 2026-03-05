import { component$, type QRL } from "@qwik.dev/core";
import { Link } from "@qwik.dev/router";
import { ThemeToggle } from "./ThemeToggle";

export interface AppHeaderProps {
  isAuthenticated: boolean;
  showSidebarToggle: boolean;
  isSidebarExpanded: boolean;
  userDisplayName?: string | null;
  authLogoutHref?: string;
  onToggleSidebar$: QRL<() => void>;
}

export const AppHeader = component$<AppHeaderProps>(
  ({
    isAuthenticated,
    showSidebarToggle,
    isSidebarExpanded,
    userDisplayName,
    authLogoutHref = "#",
    onToggleSidebar$,
  }) => {
    return (
      <header class="flex h-14 shrink-0 items-center justify-between border-b border-border bg-surface px-4">
        <div class="flex items-center gap-3">
          {showSidebarToggle && (
            <button
              type="button"
              class="rounded p-1.5 text-muted transition-colors hover:bg-surface-2 hover:text-text focus-visible:outline focus-visible:outline-3 focus-visible:outline-primary focus-visible:outline-offset-2"
              aria-label="Переключить боковую панель"
              onClick$={onToggleSidebar$}
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                class="h-5 w-5"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                stroke-width="2"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  d="M4 6h16M4 12h16M4 18h16"
                />
              </svg>
            </button>
          )}
          {(!isAuthenticated || !isSidebarExpanded) && (
            <span class="text-sm font-semibold text-text">Jitsi Portal</span>
          )}
        </div>

        <div class="flex items-center gap-3">
          <ThemeToggle />
          {isAuthenticated ? (
            <>
              {userDisplayName && (
                <span class="hidden text-sm text-muted md:inline">
                  {userDisplayName}
                </span>
              )}
              <a
                href={authLogoutHref}
                class="inline-flex items-center rounded-md border border-border px-3 py-1.5 text-sm font-medium text-text transition-colors hover:bg-surface-2 focus-visible:outline focus-visible:outline-3 focus-visible:outline-primary focus-visible:outline-offset-2"
              >
                Выйти
              </a>
            </>
          ) : (
            <Link
              href="/auth"
              class="inline-flex items-center rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground transition-colors hover:opacity-90 focus-visible:outline focus-visible:outline-3 focus-visible:outline-primary focus-visible:outline-offset-2"
            >
              Войти
            </Link>
          )}
        </div>
      </header>
    );
  },
);
