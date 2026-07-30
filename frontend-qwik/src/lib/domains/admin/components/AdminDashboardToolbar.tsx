import { component$ } from "@qwik.dev/core";
import { buildAdminQueryHref } from "../admin-route-query";
import type { AdminDashboardDerivedState } from "../admin-dashboard.route-helpers";
import type { AdminDashboardSummary } from "../types";

const PERIOD_OPTIONS = ["15m", "1h", "24h"] as const;
const ENVIRONMENT_OPTIONS = ["dev", "test", "prod"] as const;

interface AdminDashboardToolbarProps {
  currentUrl: string;
  dashboard: Pick<AdminDashboardSummary, "traceId" | "sampleWindowLimited">;
  state: Pick<
    AdminDashboardDerivedState,
    "activeEnvironment" | "activePeriod"
  >;
}

function filterClass(active: boolean): string[] {
  return [
    "rounded-full border px-3 py-1.5 text-sm font-medium transition-colors",
    active
      ? "border-text bg-text text-bg"
      : "border-border bg-bg text-text hover:bg-surface-alt",
  ];
}

export const AdminDashboardToolbar = component$(
  ({ currentUrl, dashboard, state }: AdminDashboardToolbarProps) => {
    const url = new URL(currentUrl);

    return (
      <section class="rounded-3xl border border-border bg-surface px-4 py-4 shadow-sm md:px-5">
        <div class="grid gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(20rem,0.7fr)] xl:items-end">
          <div>
            <p class="text-xs uppercase tracking-[0.24em] text-muted">
              Оперативная сводка
            </p>
            <div class="mt-2 flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
              <div>
                <h2 class="text-2xl font-semibold text-text">
                  Состояние платформы
                </h2>
                <p class="mt-1 max-w-2xl text-sm text-muted">
                  Сигналы, сервисы и очередь инцидентов в одном рабочем
                  контексте.
                </p>
              </div>
              <div class="flex flex-wrap gap-x-5 gap-y-3">
                <nav aria-label="Период сводки">
                  <p class="mb-1.5 text-xs text-muted">Период</p>
                  <div class="flex flex-wrap gap-2">
                    {PERIOD_OPTIONS.map((period) => {
                      const active = state.activePeriod === period;
                      return (
                        <a
                          key={period}
                          href={buildAdminQueryHref(url, { period })}
                          aria-current={active ? "page" : undefined}
                          class={filterClass(active)}
                        >
                          {period}
                        </a>
                      );
                    })}
                  </div>
                </nav>
                <nav aria-label="Окружение сводки">
                  <p class="mb-1.5 text-xs text-muted">Окружение</p>
                  <div class="flex flex-wrap gap-2">
                    {ENVIRONMENT_OPTIONS.map((environment) => {
                      const active = state.activeEnvironment === environment;
                      return (
                        <a
                          key={environment}
                          href={buildAdminQueryHref(url, { environment })}
                          aria-current={active ? "page" : undefined}
                          class={[
                            ...filterClass(active),
                            "uppercase",
                          ]}
                        >
                          {environment}
                        </a>
                      );
                    })}
                  </div>
                </nav>
              </div>
            </div>
          </div>

          <dl class="grid grid-cols-2 gap-x-4 gap-y-2 rounded-2xl border border-border bg-bg/70 px-4 py-3 text-sm">
            <div>
              <dt class="text-xs text-muted">Окружение</dt>
              <dd class="mt-1 font-semibold uppercase text-text">
                {state.activeEnvironment}
              </dd>
            </div>
            <div>
              <dt class="text-xs text-muted">Окно</dt>
              <dd class="mt-1 font-semibold text-text">
                {state.activePeriod}
              </dd>
            </div>
            <div class="col-span-2 border-t border-border pt-2">
              <dt class="text-xs text-muted">Trace ID</dt>
              <dd class="mt-1 break-all font-mono text-xs text-text">
                {dashboard.traceId}
              </dd>
            </div>
            {dashboard.sampleWindowLimited ? (
              <div class="col-span-2 border-t border-border pt-2">
                <dt class="sr-only">Ограничение выборки</dt>
                <dd class="text-xs text-muted">
                  Использовано безопасное окно чтения.
                </dd>
              </div>
            ) : null}
          </dl>
        </div>
      </section>
    );
  },
);
