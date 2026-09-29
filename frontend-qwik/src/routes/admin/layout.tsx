import { component$, Slot } from "@qwik.dev/core";
import { routeLoader$, useLocation } from "@qwik.dev/router";
import {
  buildAdminNavGroups,
  fetchAdminFrameworkVersions,
  hasAdminCabinetAccess,
  hasCriticalFrameworkAlert,
  hasFrameworkReleaseAlert,
  isActiveAdminNavItem,
  withAdminEnvironment,
} from "~/lib/domains/admin";
import {
  resolveAuthRecoveryRedirectPath,
  type SafeUserProfile,
} from "~/lib/domains/auth";
import { hasPlatformAdminAccess } from "~/lib/shared/security";
import { buildServerRequestContext } from "~/lib/shared/routes/server-handlers";

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

export const useFrameworkVersionAlert = routeLoader$(
  async ({ sharedMap, cookie }) => {
    const user = (sharedMap.get("user") as SafeUserProfile | null) ?? null;
    if (!user || !hasAdminCabinetAccess(user)) {
      return null;
    }
    try {
      return await fetchAdminFrameworkVersions(
        buildServerRequestContext({ sharedMap, cookie }),
      );
    } catch {
      return null;
    }
  },
);

export default component$(() => {
  const adminUser = useAdminGuard();
  const versionAlert = useFrameworkVersionAlert();
  const location = useLocation();
  const environment = location.url.searchParams.get("environment");

  const navGroups = buildAdminNavGroups(
    location.url,
    hasPlatformAdminAccess(adminUser.value.claims),
  );
  const currentSection = navGroups
    .flatMap((group) => group.items)
    .find((item) => isActiveAdminNavItem(location.url.pathname, item.match));

  return (
    <section class="space-y-4 md:space-y-5">
      <header class="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
        <h1 class="font-semibold text-text">Администрирование</h1>
        {currentSection ? (
          <span class="text-muted">/ {currentSection.label}</span>
        ) : null}
      </header>
      <div class="grid items-start gap-5 xl:grid-cols-[14rem_minmax(0,1fr)]">
        <nav
          aria-label="Разделы администрирования"
          class="grid gap-4 rounded-2xl border border-border bg-surface p-3 md:grid-cols-3 xl:sticky xl:top-0 xl:block xl:space-y-5"
        >
          <a
            href="#admin-content"
            class="sr-only focus:not-sr-only focus:rounded focus:p-2 focus:text-primary"
          >
            Перейти к содержимому раздела
          </a>
          {navGroups.map((group) => (
            <section key={group.label}>
              <h2 class="mb-2 px-2 text-xs font-semibold text-muted">
                {group.label}
              </h2>
              <ul class="grid grid-cols-2 gap-1 md:grid-cols-1">
                {group.items.map((item) => {
                  const active = isActiveAdminNavItem(
                    location.url.pathname,
                    item.match,
                  );
                  return (
                    <li key={item.match}>
                      <a
                        href={item.href}
                        aria-current={active ? "page" : undefined}
                        class={[
                          "block h-full rounded-xl border px-3 py-2.5 transition-colors focus-visible:outline focus-visible:outline-3 focus-visible:outline-primary focus-visible:outline-offset-2",
                          active
                            ? "border-primary/30 bg-primary/10 text-primary"
                            : "border-transparent text-text hover:border-border hover:bg-surface-alt",
                        ]}
                      >
                        <span class="block text-sm font-semibold">
                          {item.label}
                        </span>
                        <span class="mt-1 hidden text-xs leading-4 text-muted sm:block">
                          {item.description}
                        </span>
                      </a>
                    </li>
                  );
                })}
              </ul>
            </section>
          ))}
        </nav>
        <div
          id="admin-content"
          tabIndex={-1}
          class="min-w-0 space-y-4 md:space-y-5"
        >
          {hasCriticalFrameworkAlert(versionAlert.value) ? (
            <aside
              role="alert"
              class="flex flex-col gap-3 rounded-3xl border border-danger/30 bg-danger/10 px-4 py-4 text-danger shadow-sm sm:flex-row sm:items-center sm:justify-between"
            >
              <div>
                <p class="font-semibold">Обнаружены критические уязвимости</p>
                <p class="mt-1 text-sm">
                  Требуется обновить затронутые фреймворки. Критических записей:{" "}
                  {versionAlert.value?.criticalVulnerabilityCount ?? 0}.
                </p>
              </div>
              <a
                href={withAdminEnvironment(
                  "/admin/framework-versions",
                  environment,
                )}
                class="w-fit rounded-full bg-danger px-4 py-2 text-sm font-medium text-white"
              >
                Открыть отчёт
              </a>
            </aside>
          ) : null}
          {hasFrameworkReleaseAlert(versionAlert.value) ? (
            <aside
              role="alert"
              class="flex flex-col gap-3 rounded-3xl border border-warning/30 bg-warning/10 px-4 py-4 text-warning shadow-sm sm:flex-row sm:items-center sm:justify-between"
            >
              <div>
                <p class="font-semibold">Доступны новые версии компонентов</p>
                <p class="mt-1 text-sm">
                  По последней проверке обновлений:{" "}
                  {versionAlert.value?.updateAvailableCount ?? 0}. Проверьте
                  совместимость перед установкой.
                </p>
              </div>
              <a
                href={withAdminEnvironment(
                  "/admin/framework-versions",
                  environment,
                )}
                class="w-fit rounded-full bg-warning px-4 py-2 text-sm font-medium text-white"
              >
                Открыть версии
              </a>
            </aside>
          ) : null}
          <Slot />
        </div>
      </div>
    </section>
  );
});
