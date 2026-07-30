import { component$ } from "@qwik.dev/core";
import {
  buildAdminDashboardSelectionHref,
  isHealthyAdminServiceStatus,
  resolveAdminDashboardCardTone,
  resolveAdminServiceStatusTone,
  type AdminDashboardDerivedState,
} from "../admin-dashboard.route-helpers";
import type { AdminDashboardSummary } from "../types";

interface AdminDashboardHealthSectionsProps {
  currentUrl: string;
  dashboard: Pick<
    AdminDashboardSummary,
    "keyServiceStatuses" | "topDegradations"
  >;
  activeIncidentsHref: string;
  state: Pick<
    AdminDashboardDerivedState,
    "activeEnvironment" | "activePeriod"
  >;
}

export const AdminDashboardHealthSections = component$(
  ({
    currentUrl,
    dashboard,
    activeIncidentsHref,
    state,
  }: AdminDashboardHealthSectionsProps) => {
    const url = new URL(currentUrl);
    const healthyServices = dashboard.keyServiceStatuses.filter((service) =>
      isHealthyAdminServiceStatus(service.status),
    ).length;

    return (
      <section class="rounded-3xl border border-border bg-surface p-4 shadow-sm md:p-5">
        <div class="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p class="text-xs uppercase tracking-[0.2em] text-muted">
              Ключевые сервисы
            </p>
            <h3 class="mt-1 text-lg font-semibold text-text">
              {`${healthyServices} из ${dashboard.keyServiceStatuses.length} в норме`}
            </h3>
          </div>
          {dashboard.topDegradations.length > 0 ? (
            <a
              href={activeIncidentsHref}
              class="text-sm font-medium text-text underline underline-offset-4"
            >
              Открыть очередь
            </a>
          ) : (
            <span class="inline-flex w-fit rounded-full bg-emerald-100 px-3 py-1 text-xs font-medium text-emerald-800 dark:bg-emerald-950 dark:text-emerald-200">
              Активных деградаций нет
            </span>
          )}
        </div>

        <div class="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          {dashboard.keyServiceStatuses.map((service) => (
            <a
              key={service.key}
              href={buildAdminDashboardSelectionHref(
                url,
                service.handoff,
                state,
              )}
              class="group rounded-2xl border border-border bg-bg px-4 py-3 transition-colors hover:bg-surface-alt"
            >
              <div class="flex items-center justify-between gap-3">
                <p class="text-sm font-medium text-text">{service.label}</p>
                <span class="inline-flex items-center gap-2 text-xs font-semibold uppercase text-text">
                  <span
                    aria-hidden="true"
                    class={[
                      "h-2 w-2 rounded-full",
                      resolveAdminServiceStatusTone(service.status),
                    ]}
                  />
                  {service.status}
                </span>
              </div>
              <p class="mt-2 line-clamp-2 text-sm text-muted">
                {service.detail}
              </p>
            </a>
          ))}
        </div>

        {dashboard.topDegradations.length > 0 ? (
          <div class="mt-4 border-t border-border pt-4">
            <h3 class="text-base font-semibold text-text">
              Активные деградации
            </h3>
            <div class="mt-3 grid gap-3 md:grid-cols-2">
              {dashboard.topDegradations.map((degradation) => (
                <a
                  key={degradation.id}
                  href={buildAdminDashboardSelectionHref(
                    url,
                    degradation.handoff,
                    state,
                  )}
                  class={[
                    "rounded-2xl border p-4 transition-transform hover:-translate-y-0.5",
                    resolveAdminDashboardCardTone(degradation.severity),
                  ]}
                >
                  <p class="text-xs font-medium uppercase tracking-[0.18em] opacity-70">
                    {degradation.severity}
                  </p>
                  <h4 class="mt-2 font-semibold">{degradation.title}</h4>
                  <p class="mt-1 text-sm opacity-90">
                    {degradation.summary}
                  </p>
                  <p class="mt-3 text-sm font-medium underline underline-offset-4">
                    {degradation.actionLabel}
                  </p>
                </a>
              ))}
            </div>
          </div>
        ) : null}
      </section>
    );
  },
);
