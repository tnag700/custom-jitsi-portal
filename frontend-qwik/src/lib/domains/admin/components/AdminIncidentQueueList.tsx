import { component$ } from "@qwik.dev/core";
import {
  buildIncidentDetailHref,
  resolveIncidentRelativeTimeLabel,
  type IncidentQueueDerivedState,
} from "../admin-incidents.route-helpers";
import type { AdminIncidentList } from "../types";
import { RequestStatePanel } from "~/lib/shared";

interface AdminIncidentQueueListProps {
  currentUrl: string;
  incidents: AdminIncidentList;
  state: IncidentQueueDerivedState;
}

function resolveSeverityClass(severity: string): string {
  switch (severity.trim().toLowerCase()) {
    case "critical":
      return "border-red-300 bg-red-50 text-red-800 dark:border-red-800 dark:bg-red-950/40 dark:text-red-200";
    case "warning":
    case "warn":
      return "border-amber-300 bg-amber-50 text-amber-800 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200";
    default:
      return "border-border bg-bg text-muted";
  }
}

export const AdminIncidentQueueList = component$(
  ({ currentUrl, incidents, state }: AdminIncidentQueueListProps) => (
    <section class="rounded-3xl border border-border bg-surface p-4 shadow-sm sm:p-5">
      <div class="flex items-center justify-between gap-3">
        <div>
          <p class="text-xs uppercase tracking-[0.18em] text-muted">
            Рабочая область
          </p>
          <h3 class="mt-1 text-lg font-semibold text-text">Инциденты</h3>
        </div>
        <p class="text-sm text-muted">
          {String(incidents.totalElements)} найдено
        </p>
      </div>

      {incidents.items.length > 0 ? (
        <div class="mt-4 space-y-2">
          {incidents.items.map((incident) => (
            <a
              key={incident.incidentId}
              href={buildIncidentDetailHref(
                new URL(currentUrl),
                incident.incidentId,
                state.effectiveEnvironment,
              )}
              class="block rounded-2xl border border-border bg-bg px-4 py-3.5 transition-colors hover:bg-surface-alt"
            >
              <div class="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div class="min-w-0">
                  <div class="flex flex-wrap items-center gap-2">
                    <span
                      class={[
                        "rounded-full border px-2 py-1 text-xs font-medium uppercase tracking-[0.14em]",
                        resolveSeverityClass(incident.severity),
                      ]}
                    >
                      {incident.severity}
                    </span>
                    <span class="text-sm font-semibold text-text">
                      {incident.errorCode}
                    </span>
                    <span class="text-sm text-muted">{incident.category}</span>
                  </div>
                  <p class="mt-2 text-sm text-text">
                    {incident.affectedEntitySummary}
                  </p>
                  <p class="mt-1 text-xs text-muted">
                    {resolveIncidentRelativeTimeLabel(incident.freshnessHint)}
                  </p>
                </div>
                <div class="shrink-0 text-xs text-muted lg:text-right">
                  <p>{incident.occurredAt}</p>
                  <p class="mt-1 text-sm font-medium text-text">
                    Открыть карточку
                  </p>
                </div>
              </div>
            </a>
          ))}
        </div>
      ) : (
        <div class="mt-4">
          <RequestStatePanel
            title="Очередь пуста для выбранного режима"
            detail={`Представление ${state.activeViewLabel}${state.activeFacetLabel ? `, фасет ${state.activeFacetLabel}` : ""} не содержит инцидентов в текущем окне.`}
          />
        </div>
      )}
    </section>
  ),
);
