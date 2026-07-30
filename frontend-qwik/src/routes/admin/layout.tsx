import { component$, Slot } from "@qwik.dev/core";
import { routeLoader$, useLocation } from "@qwik.dev/router";
import {
  buildAdminPrimaryNavItems,
  buildAdminSecondaryNavItems,
  hasAdminCabinetAccess,
  isActiveAdminNavItem,
} from "~/lib/domains/admin";
import {
  resolveAuthRecoveryRedirectPath,
  type SafeUserProfile,
} from "~/lib/domains/auth";
import { hasPlatformAdminAccess } from "~/lib/shared/security";

export const useAdminGuard = routeLoader$(({ sharedMap, redirect, url }) => {
  const user = (sharedMap.get("user") as SafeUserProfile | null) ?? null;
  if (!user) {
    throw redirect(
      302,
      resolveAuthRecoveryRedirectPath(
        undefined,
        `${url.pathname}${url.search}`,
      ),
    );
  }

  const isAdmin = hasAdminCabinetAccess(user);
  if (!isAdmin) {
    throw redirect(302, "/");
  }

  return user;
});

export default component$(() => {
  const adminUser = useAdminGuard();
  const location = useLocation();
  const environment = location.url.searchParams.get("environment");

  const primaryNavItems = buildAdminPrimaryNavItems(environment);
  const secondaryNavItems = buildAdminSecondaryNavItems(
    location.url,
    environment ?? "",
    hasPlatformAdminAccess(adminUser.value.claims),
  );

  return (
    <section class="space-y-4 md:space-y-5">
      <header class="rounded-3xl border border-border bg-surface px-4 py-4 shadow-sm md:px-5">
        <div class="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <p class="text-xs uppercase tracking-[0.22em] text-muted">
              Административная консоль
            </p>
            <h1 class="mt-1 text-2xl font-semibold text-text">
              Управление платформой
            </h1>
            <p class="mt-1 hidden max-w-2xl text-sm text-muted sm:block">
              Состояние сервисов, инциденты и административные инструменты.
            </p>
          </div>
          <nav
            aria-label="Основные разделы администрирования"
            class="flex flex-wrap gap-2"
          >
            {primaryNavItems.map((item) => {
              const active = isActiveAdminNavItem(
                location.url.pathname,
                item.match,
              );
              return (
                <a
                  key={item.href}
                  href={item.href}
                  aria-current={active ? "page" : undefined}
                  class={[
                    "rounded-full border px-3 py-1.5 text-sm font-medium transition-colors",
                    active
                      ? "border-text bg-text text-bg"
                      : "border-border bg-bg text-text hover:bg-surface-alt",
                  ]}
                >
                  {item.label}
                </a>
              );
            })}
          </nav>
        </div>
        <div class="mt-4 flex flex-col gap-2 border-t border-border pt-3 sm:flex-row sm:items-center sm:justify-between">
          <span class="text-xs uppercase tracking-[0.18em] text-muted">
            Инструменты
          </span>
          <nav
            aria-label="Административные инструменты"
            class="flex flex-wrap gap-2"
          >
            {secondaryNavItems.map((item) => {
              const active = isActiveAdminNavItem(
                location.url.pathname,
                item.match,
              );
              return (
                <a
                  key={item.href}
                  href={item.href}
                  aria-current={active ? "page" : undefined}
                  class={[
                    "rounded-full border px-3 py-1.5 text-sm transition-colors",
                    active
                      ? "border-text bg-surface-alt font-medium text-text"
                      : "border-transparent text-muted hover:border-border hover:bg-bg hover:text-text",
                  ]}
                >
                  {item.label}
                </a>
              );
            })}
          </nav>
        </div>
      </header>
      <Slot />
    </section>
  );
});
