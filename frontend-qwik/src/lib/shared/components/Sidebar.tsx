import { component$, type QRL, type Signal } from "@qwik.dev/core";
import { Link, useLocation } from "@qwik.dev/router";
import {
  isNavItemActive,
  navItems,
  type NavItem,
} from "./sidebar-nav-items";

export interface SidebarProps {
  expanded: Signal<boolean>;
  items?: readonly NavItem[];
  variant?: "desktop" | "mobile";
  onDismiss$?: QRL<() => void>;
}

export const Sidebar = component$<SidebarProps>(
  ({ expanded, items, variant = "desktop", onDismiss$ }) => {
    const loc = useLocation();
    const visibleNavItems = items ?? navItems;
    const mobile = variant === "mobile";
    const showLabels = mobile || expanded.value;

    return (
      <aside
        id={mobile ? "mobile-navigation" : "desktop-navigation"}
        aria-label={mobile ? "Мобильное меню" : "Боковая панель"}
        class={[
          "h-full border-r border-border bg-surface-2 text-text",
          mobile
            ? "fixed inset-y-0 left-0 z-50 w-72 max-w-[85vw] shadow-2xl lg:hidden"
            : "hidden shrink-0 transition-[width] duration-200 lg:block",
          !mobile && (expanded.value ? "lg:w-64" : "lg:w-16"),
        ]}
      >
        <div class="flex h-full flex-col">
          <div class="px-3 py-4">
            <div class="flex items-center gap-2">
              <span class="text-lg font-bold text-primary">J</span>
              {showLabels && (
                <span class="text-sm font-semibold text-text">Jitsi Portal</span>
              )}
            </div>
          </div>

          <nav aria-label="Основная навигация" class="flex-1 px-2">
            <ul class="space-y-1">
              {visibleNavItems.map((item) => {
                const active = isNavItemActive(loc.url.pathname, item.href);
                return (
                  <li key={item.href}>
                    <Link
                      href={item.href}
                      prefetch="js"
                      aria-current={active ? "page" : undefined}
                      aria-label={item.label}
                      title={!showLabels ? item.label : undefined}
                      onClick$={mobile ? onDismiss$ : undefined}
                      class={[
                        "flex items-center gap-2 rounded-md px-2 py-2 text-text transition-colors hover:bg-surface focus-visible:outline focus-visible:outline-3 focus-visible:outline-primary focus-visible:outline-offset-2",
                        active && "bg-surface font-semibold text-primary",
                        !showLabels && "justify-center",
                      ]}
                    >
                      <svg
                        xmlns="http://www.w3.org/2000/svg"
                        class="h-5 w-5 shrink-0"
                        fill="none"
                        viewBox="0 0 24 24"
                        stroke="currentColor"
                        stroke-width="2"
                      >
                        <path
                          stroke-linecap="round"
                          stroke-linejoin="round"
                          d={item.icon}
                        />
                      </svg>
                      {showLabels && <span>{item.label}</span>}
                    </Link>
                  </li>
                );
              })}
            </ul>
          </nav>

          <div class="px-3 py-2">
            <button
              type="button"
              class="flex w-full items-center justify-center rounded p-2 text-muted transition-colors hover:bg-surface hover:text-text focus-visible:outline focus-visible:outline-3 focus-visible:outline-primary focus-visible:outline-offset-2"
              aria-label={
                mobile
                  ? "Закрыть меню"
                  : expanded.value
                    ? "Свернуть боковую панель"
                    : "Развернуть боковую панель"
              }
              onClick$={
                mobile
                  ? onDismiss$
                  : () => {
                      expanded.value = !expanded.value;
                    }
              }
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                class={[
                  "h-5 w-5 transition-transform duration-200",
                  !mobile && !expanded.value && "rotate-180",
                ]}
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                stroke-width="2"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  d={mobile ? "M6 18L18 6M6 6l12 12" : "M11 19l-7-7 7-7m8 14l-7-7 7-7"}
                />
              </svg>
            </button>
          </div>
        </div>
      </aside>
    );
  },
);
