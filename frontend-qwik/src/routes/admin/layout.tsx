import { component$, Slot } from "@qwik.dev/core";
import { routeLoader$, useLocation } from "@qwik.dev/router";
import {
  buildAdminPrimaryNavItems,
  buildAdminSecondaryNavItems,
  hasAdminCabinetAccess,
  isActiveAdminNavItem,
} from "~/lib/domains/admin";
import { resolveAuthRecoveryRedirectPath, type SafeUserProfile } from "~/lib/domains/auth";

export const useAdminGuard = routeLoader$(({ sharedMap, redirect, url }) => {
  const user = (sharedMap.get("user") as SafeUserProfile | null) ?? null;
  if (!user) {
    throw redirect(302, resolveAuthRecoveryRedirectPath(undefined, `${url.pathname}${url.search}`));
  }

  const isAdmin = hasAdminCabinetAccess(user);
  if (!isAdmin) {
    throw redirect(302, "/");
  }

  return user;
});

export default component$(() => {
  useAdminGuard();
  const location = useLocation();
  const environment = location.url.searchParams.get("environment");

  const primaryNavItems = buildAdminPrimaryNavItems(environment);
  const secondaryNavItems = buildAdminSecondaryNavItems(location.url, environment ?? "");

  return (
    <section class="space-y-6">
      <header class="rounded-3xl border border-border bg-surface px-6 py-5 shadow-sm">
        <p class="text-xs uppercase tracking-[0.24em] text-muted">Административная консоль</p>
        <h1 class="mt-2 text-3xl font-semibold text-text">Панель управления платформой</h1>
        <p class="mt-2 max-w-3xl text-sm text-muted">
          Единая рабочая зона для состояния сервисов, готовности входа, совместимости конфигурации и
          детализации по трассировкам без переключения во внешнюю observability-систему.
        </p>
        <nav aria-label="Primary admin navigation" class="mt-4 flex flex-wrap gap-2">
          {primaryNavItems.map((item) => {
            const active = isActiveAdminNavItem(location.url.pathname, item.match);
            return (
              <a
                key={item.href}
                href={item.href}
                class={[
                  "rounded-full border px-3 py-1 text-sm transition-colors",
                  active ? "border-text bg-text text-bg" : "border-border bg-bg text-text hover:bg-surface-alt",
                ]}
              >
                {item.label}
              </a>
            );
          })}
        </nav>
        <div class="mt-5 rounded-3xl border border-dashed border-border bg-bg/70 px-4 py-4">
          <div class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <p class="text-xs uppercase tracking-[0.22em] text-muted">Вторичные модули</p>
              <p class="mt-2 text-sm text-muted">
                История ролей и конфиг-наборы остаются discoverable, но не конкурируют с triage-first маршрутом overview → incidents.
              </p>
            </div>
            <nav aria-label="Secondary admin navigation" class="flex flex-wrap gap-2">
              {secondaryNavItems.map((item) => {
                const active = isActiveAdminNavItem(location.url.pathname, item.match);
                return (
                  <a
                    key={item.href}
                    href={item.href}
                    class={[
                      "rounded-full border px-3 py-1 text-sm transition-colors",
                      active
                        ? "border-text bg-surface-alt text-text"
                        : "border-border bg-bg text-muted hover:bg-surface-alt hover:text-text",
                    ]}
                  >
                    {item.label}
                  </a>
                );
              })}
            </nav>
          </div>
        </div>
      </header>
      <Slot />
    </section>
  );
});