import { component$, type Signal } from "@qwik.dev/core";
import { Link, useLocation } from "@qwik.dev/router";
import { navItems } from "./sidebar-nav-items";

export interface SidebarProps {
  expanded: Signal<boolean>;
}

export const Sidebar = component$<SidebarProps>(({ expanded }) => {
  const loc = useLocation();

  const isActive = (href: string) => {
    const pathname = loc.url.pathname;
    if (href === "/") {
      return pathname === "/";
    }

    return pathname === href || pathname.startsWith(`${href}/`);
  };

  return (
    <aside
      class={[
        "h-full shrink-0 border-r border-border bg-surface-2 text-text transition-[width] duration-200",
        expanded.value ? "w-64" : "w-16",
      ]}
    >
      <div class="flex h-full flex-col">
        {/* Sidebar header */}
        <div class="px-3 py-4">
          <div class="flex items-center gap-2">
            <span class="text-lg font-bold text-primary">J</span>
            {expanded.value && (
              <span class="text-sm font-semibold text-text">Jitsi Portal</span>
            )}
          </div>
        </div>

        {/* Navigation */}
        <nav aria-label="Основная навигация" class="flex-1 px-2">
          <ul class="space-y-1">
            {navItems.map((item) => {
              const active = isActive(item.href);
              return (
                <li key={item.href}>
                  <Link
                    href={item.href}
                    aria-current={active ? "page" : undefined}
                    aria-label={item.label}
                    title={!expanded.value ? item.label : undefined}
                    class={[
                      "flex items-center gap-2 rounded-md px-2 py-2 text-text transition-colors hover:bg-surface focus-visible:outline focus-visible:outline-3 focus-visible:outline-primary focus-visible:outline-offset-2",
                      active && "bg-surface text-primary font-semibold",
                      !expanded.value && "justify-center",
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
                    {expanded.value && <span>{item.label}</span>}
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>

        {/* Footer with toggle */}
        <div class="px-3 py-2">
          <button
            type="button"
            class="flex w-full items-center justify-center rounded p-2 text-muted transition-colors hover:bg-surface hover:text-text focus-visible:outline focus-visible:outline-3 focus-visible:outline-primary focus-visible:outline-offset-2"
            aria-label={
              expanded.value
                ? "Свернуть боковую панель"
                : "Развернуть боковую панель"
            }
            onClick$={() => {
              expanded.value = !expanded.value;
            }}
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              class={[
                "h-5 w-5 transition-transform duration-200",
                !expanded.value && "rotate-180",
              ]}
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              stroke-width="2"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                d="M11 19l-7-7 7-7m8 14l-7-7 7-7"
              />
            </svg>
          </button>
        </div>
      </div>
    </aside>
  );
});
