import { component$ } from "@qwik.dev/core";
import {
  buildAdminDashboardSelectionHref,
  resolveAdminDashboardCardTone,
  type AdminDashboardDerivedState,
} from "../admin-dashboard.route-helpers";
import type { AdminDashboardSummary } from "../types";

interface AdminDashboardPrioritySignalProps {
  currentUrl: string;
  priorityBanner: AdminDashboardSummary["priorityBanner"];
  activeIncidentsHref: string;
  state: Pick<
    AdminDashboardDerivedState,
    "activeEnvironment" | "activePeriod"
  >;
}

export const AdminDashboardPrioritySignal = component$(
  ({
    currentUrl,
    priorityBanner,
    activeIncidentsHref,
    state,
  }: AdminDashboardPrioritySignalProps) => {
    const actionHref = priorityBanner.active
      ? buildAdminDashboardSelectionHref(
          new URL(currentUrl),
          priorityBanner.handoff,
          state,
        )
      : activeIncidentsHref;

    return (
      <section
        class={[
          "rounded-3xl border px-4 py-4 shadow-sm md:px-5",
          resolveAdminDashboardCardTone(priorityBanner.severity),
        ]}
      >
        <div class="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
          <div class="max-w-3xl">
            <div class="flex items-center gap-2">
              <span
                aria-hidden="true"
                class={[
                  "h-2.5 w-2.5 rounded-full",
                  priorityBanner.active ? "bg-rose-500" : "bg-emerald-500",
                ]}
              />
              <p class="text-xs font-medium uppercase tracking-[0.2em]">
                {priorityBanner.active
                  ? "Требует внимания"
                  : "Операционный статус"}
              </p>
            </div>
            <h3 class="mt-2 text-xl font-semibold">
              {priorityBanner.headline}
            </h3>
            <p class="mt-1 text-sm opacity-90">{priorityBanner.summary}</p>
          </div>
          <a
            href={actionHref}
            class="inline-flex shrink-0 justify-center rounded-xl border border-current px-4 py-2 text-sm font-medium transition-opacity hover:opacity-75"
          >
            {priorityBanner.actionLabel}
          </a>
        </div>
      </section>
    );
  },
);
