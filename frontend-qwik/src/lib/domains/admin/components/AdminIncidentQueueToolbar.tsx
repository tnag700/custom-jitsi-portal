import { component$ } from "@qwik.dev/core";
import { buildAdminQueryHref } from "../admin-route-query";
import {
  buildIncidentQueueFacetQueryUpdates,
  buildIncidentQueueViewQueryUpdates,
  type IncidentQueueDerivedState,
  type IncidentQueueFilters,
} from "../admin-incidents.route-helpers";
import type { AdminIncidentList } from "../types";

interface AdminIncidentQueueToolbarProps {
  currentUrl: string;
  incidents: AdminIncidentList;
  filters: IncidentQueueFilters;
  state: IncidentQueueDerivedState;
}

export const AdminIncidentQueueToolbar = component$(
  ({
    currentUrl,
    incidents,
    filters,
    state,
  }: AdminIncidentQueueToolbarProps) => {
    const url = new URL(currentUrl);
    const activeViewSummary = incidents.availableViews.find(
      (view) => view.token === incidents.selectedView,
    )?.summary;

    return (
      <section class="rounded-3xl border border-border bg-surface p-4 shadow-sm sm:p-5">
        <div class="grid gap-4 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-start">
          <div>
            <p class="text-xs uppercase tracking-[0.22em] text-muted">
              Оперативная очередь
            </p>
            <h2 class="mt-1.5 text-xl font-semibold text-text">
              Инциденты входа
            </h2>
            <p class="mt-1.5 max-w-2xl text-sm text-muted">
              Выберите рабочий режим или сузьте очередь быстрым фасетом.
            </p>
          </div>

          <dl class="grid grid-cols-3 overflow-hidden rounded-2xl border border-border bg-bg/60 text-sm">
            <div class="px-3 py-2.5">
              <dt class="text-xs text-muted">Очередь</dt>
              <dd class="mt-1 font-semibold text-text">
                {String(incidents.totalElements)} в очереди
              </dd>
            </div>
            <div class="border-l border-border px-3 py-2.5">
              <dt class="text-xs text-muted">Контур</dt>
              <dd class="mt-1 font-medium text-text">
                {state.selectedEnvironment || "all"}
              </dd>
            </div>
            <div class="border-l border-border px-3 py-2.5">
              <dt class="text-xs text-muted">Окно</dt>
              <dd class="mt-1 font-medium text-text">{incidents.period}</dd>
            </div>
          </dl>
        </div>

        <div class="mt-4 border-t border-border pt-4">
          <div class="grid gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
            <div>
              <div class="flex flex-wrap items-center justify-between gap-2">
                <p class="text-xs font-medium uppercase tracking-[0.18em] text-muted">
                  Представление
                </p>
                <span class="text-xs text-muted">{incidents.sort.label}</span>
              </div>
              <nav
                aria-label="Сохранённые представления очереди"
                class="mt-2 flex flex-wrap gap-2"
              >
                {incidents.availableViews.map((view) => (
                  <a
                    key={view.token}
                    href={buildAdminQueryHref(
                      url,
                      buildIncidentQueueViewQueryUpdates(filters, view.token),
                    )}
                    aria-current={
                      view.token === incidents.selectedView
                        ? "page"
                        : undefined
                    }
                    class={
                      view.token === incidents.selectedView
                        ? "rounded-full bg-text px-3 py-1.5 text-sm font-medium text-bg"
                        : "rounded-full border border-border px-3 py-1.5 text-sm text-text transition-colors hover:bg-surface-alt"
                    }
                  >
                    {view.label}
                  </a>
                ))}
              </nav>
              {activeViewSummary ? (
                <p class="mt-2 text-sm text-muted">{activeViewSummary}</p>
              ) : null}
            </div>

            <div>
              <p class="text-xs font-medium uppercase tracking-[0.18em] text-muted">
                Быстрые фасеты
              </p>
              <nav
                aria-label="Быстрые фасеты очереди"
                class="mt-2 flex flex-wrap gap-2"
              >
                {incidents.quickFacets.map((facet) => (
                  <a
                    key={facet.token}
                    href={buildAdminQueryHref(
                      url,
                      buildIncidentQueueFacetQueryUpdates(
                        filters,
                        incidents.selectedView,
                        incidents.selectedQuickFacet,
                        facet.token,
                      ),
                    )}
                    aria-current={facet.active ? "page" : undefined}
                    class={
                      facet.active
                        ? "rounded-full border border-text bg-bg px-3 py-1.5 text-sm font-medium text-text"
                        : "rounded-full border border-border px-3 py-1.5 text-sm text-text transition-colors hover:bg-surface-alt"
                    }
                  >
                    {facet.label}{" "}
                    <span class="text-muted">{facet.count}</span>
                  </a>
                ))}
              </nav>
            </div>
          </div>
        </div>
      </section>
    );
  },
);
