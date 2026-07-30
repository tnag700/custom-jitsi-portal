import { component$ } from "@qwik.dev/core";
import {
  buildAdminDashboardSelectionHref,
  type AdminDashboardDerivedState,
} from "../admin-dashboard.route-helpers";
import type { AdminDashboardSummary } from "../types";

interface AdminDashboardEvidenceRailProps {
  currentUrl: string;
  dashboard: Pick<
    AdminDashboardSummary,
    "latestSpikes" | "affectedScopeSummary" | "safeStateSummary"
  >;
  state: Pick<
    AdminDashboardDerivedState,
    "activeEnvironment" | "activePeriod"
  >;
}

export const AdminDashboardEvidenceRail = component$(
  ({ currentUrl, dashboard, state }: AdminDashboardEvidenceRailProps) => {
    const url = new URL(currentUrl);

    return (
      <div class="space-y-4">
        {dashboard.latestSpikes.length > 0 ? (
          <article class="rounded-3xl border border-border bg-surface p-4 shadow-sm md:p-5">
            <h3 class="text-lg font-semibold text-text">Новые всплески</h3>
            <div class="mt-3 space-y-2">
              {dashboard.latestSpikes.map((spike) => (
                <a
                  key={`${spike.errorCode}-${spike.category ?? "uncategorized"}`}
                  href={buildAdminDashboardSelectionHref(
                    url,
                    spike.handoff,
                    state,
                  )}
                  class="block rounded-2xl border border-border bg-bg px-4 py-3 transition-colors hover:bg-surface-alt"
                >
                  <div class="flex items-center justify-between gap-3">
                    <span class="break-all text-sm font-medium text-text">
                      {spike.errorCode}
                    </span>
                    <span class="rounded-full bg-surface-alt px-2 py-0.5 text-xs font-semibold text-text">
                      {spike.count}
                    </span>
                  </div>
                  <p class="mt-2 text-sm text-muted">{spike.summary}</p>
                </a>
              ))}
            </div>
          </article>
        ) : null}

        {dashboard.affectedScopeSummary.length > 0 ? (
          <article class="rounded-3xl border border-border bg-surface p-4 shadow-sm md:p-5">
            <h3 class="text-lg font-semibold text-text">
              Затронутый контур
            </h3>
            <div class="mt-3 space-y-2">
              {dashboard.affectedScopeSummary.map((scope) => (
                <a
                  key={`${scope.scopeType}-${scope.scopeValue}`}
                  href={buildAdminDashboardSelectionHref(
                    url,
                    scope.handoff,
                    state,
                  )}
                  class="block rounded-2xl border border-border bg-bg px-4 py-3 transition-colors hover:bg-surface-alt"
                >
                  <div class="flex items-start justify-between gap-3">
                    <span class="break-all text-sm font-medium text-text">
                      {scope.scopeType}: {scope.scopeValue}
                    </span>
                    <span class="rounded-full bg-surface-alt px-2 py-0.5 text-xs font-semibold text-text">
                      {scope.affectedAttempts}
                    </span>
                  </div>
                  <p class="mt-2 text-sm text-muted">{scope.summary}</p>
                </a>
              ))}
            </div>
          </article>
        ) : null}

        {dashboard.safeStateSummary.stable ? (
          <article class="rounded-3xl border border-emerald-300 bg-emerald-50 p-4 text-emerald-950 shadow-sm dark:border-emerald-800 dark:bg-emerald-950 dark:text-emerald-100 md:p-5">
            <p class="text-xs font-medium uppercase tracking-[0.2em] opacity-70">
              Контроль стабильного окна
            </p>
            <h3 class="mt-1 text-lg font-semibold">
              {dashboard.safeStateSummary.headline}
            </h3>
            <p class="mt-1 text-sm opacity-90">
              {dashboard.safeStateSummary.summary}
            </p>
            {dashboard.safeStateSummary.actions.length > 0 ? (
              <div class="mt-4 flex flex-wrap gap-2">
                {dashboard.safeStateSummary.actions.map((action) => (
                  <a
                    key={action.href}
                    href={action.href}
                    class="rounded-xl border border-current px-3 py-2 text-sm font-medium"
                  >
                    {action.label}
                  </a>
                ))}
              </div>
            ) : null}
            {dashboard.safeStateSummary.recentResolvedSpikes.length > 0 ? (
              <div class="mt-4 space-y-2 border-t border-current/20 pt-4 text-sm">
                {dashboard.safeStateSummary.recentResolvedSpikes.map(
                  (spike) => (
                    <div key={spike.label}>
                      <p class="font-medium">{spike.label}</p>
                      <p class="opacity-90">{spike.detail}</p>
                    </div>
                  ),
                )}
              </div>
            ) : null}
          </article>
        ) : null}
      </div>
    );
  },
);
