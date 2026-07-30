import { component$ } from "@qwik.dev/core";
import { buildAdminOverviewHref } from "../admin-route-query";
import { formatIncidentTimeWindow } from "../admin-incidents.route-helpers";
import type { AdminIncidentDetail } from "../types";

interface AdminIncidentSummaryProps {
  incident: AdminIncidentDetail;
  returnTo: string;
}

export const AdminIncidentSummary = component$(
  ({ incident, returnTo }: AdminIncidentSummaryProps) => {
    const summary = incident.summaryBar;

    return (
      <section class="rounded-3xl border border-border bg-surface p-4 shadow-sm sm:p-5">
        <div class="flex flex-wrap items-center justify-between gap-3 text-sm">
          <a href={returnTo} class="font-medium text-text underline">
            К очереди инцидентов
          </a>
          <a
            href={buildAdminOverviewHref(incident.environment)}
            class="text-muted underline"
          >
            Открыть сводку
          </a>
        </div>

        <div class="mt-4 grid gap-4 xl:grid-cols-[minmax(0,1.35fr)_minmax(16rem,0.65fr)]">
          <div>
            <p class="text-xs uppercase tracking-[0.18em] text-muted">
              Расследование инцидента
            </p>
            <h2 class="mt-1 text-2xl font-semibold text-text">
              {summary.title}
            </h2>
            <p class="mt-2 max-w-3xl text-sm text-muted">
              {incident.summary}
            </p>
            <div class="mt-3 flex flex-wrap gap-2 text-xs">
              <span class="rounded-full border border-border px-3 py-1 text-text">
                Важность: {incident.severity}
              </span>
              <span class="rounded-full border border-border px-3 py-1 text-text">
                {summary.refusalReason}
              </span>
              <span class="rounded-full border border-border px-3 py-1 text-text">
                Статус: {summary.operationalStatus}
              </span>
              <span class="rounded-full border border-border px-3 py-1 text-text">
                Окружение: {summary.environment}
              </span>
            </div>
          </div>

          <dl class="space-y-3 rounded-2xl border border-border bg-bg p-4 text-sm">
            <div>
              <dt class="text-xs text-muted">Затронуто</dt>
              <dd class="mt-1 font-medium text-text">
                {summary.affectedScope}
              </dd>
            </div>
            <div>
              <dt class="text-xs text-muted">Период</dt>
              <dd class="mt-1 font-medium text-text">
                {formatIncidentTimeWindow(
                  incident.startedAt,
                  incident.endedAt,
                )}
              </dd>
            </div>
          </dl>
        </div>
      </section>
    );
  },
);
