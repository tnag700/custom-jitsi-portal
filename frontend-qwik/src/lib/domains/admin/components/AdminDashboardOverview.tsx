import { component$ } from "@qwik.dev/core";
import {
  buildAdminDashboardActiveIncidentsHref,
  buildAdminDashboardDerivedState,
  type AdminDashboardFilters,
} from "../admin-dashboard.route-helpers";
import type {
  AdminDashboardDrillDown,
  AdminDashboardErrorPayload,
  AdminDashboardSummary,
} from "../types";
import { AdminDashboardDrillDownPanel } from "./AdminDashboardDrillDownPanel";
import { AdminDashboardEvidenceRail } from "./AdminDashboardEvidenceRail";
import { AdminDashboardHealthSections } from "./AdminDashboardHealthSections";
import { AdminDashboardPrioritySignal } from "./AdminDashboardPrioritySignal";
import { AdminDashboardToolbar } from "./AdminDashboardToolbar";

interface AdminDashboardOverviewProps {
  currentUrl: string;
  dashboard: AdminDashboardSummary;
  drillDown: AdminDashboardDrillDown | null;
  drillDownError: AdminDashboardErrorPayload | null;
  filters: AdminDashboardFilters;
}

function summarizeSelection(
  selection: ReturnType<
    typeof buildAdminDashboardDerivedState
  >["activeDrillDownSelection"],
): string {
  if (!selection) {
    return "";
  }

  return [
    selection.errorCode,
    selection.category,
    selection.roomId,
    selection.meetingId,
  ]
    .filter((value) => value.trim().length > 0)
    .join(" / ");
}

export const AdminDashboardOverview = component$(
  ({
    currentUrl,
    dashboard,
    drillDown,
    drillDownError,
    filters,
  }: AdminDashboardOverviewProps) => {
    const state = buildAdminDashboardDerivedState(dashboard, filters);
    const activeIncidentsHref = buildAdminDashboardActiveIncidentsHref(
      new URL(currentUrl),
      state.activeDrillDownSelection,
      state,
    );

    return (
      <div class="space-y-4 md:space-y-5">
        <AdminDashboardToolbar
          currentUrl={currentUrl}
          dashboard={dashboard}
          state={state}
        />
        <AdminDashboardPrioritySignal
          currentUrl={currentUrl}
          priorityBanner={dashboard.priorityBanner}
          activeIncidentsHref={activeIncidentsHref}
          state={state}
        />
        <AdminDashboardHealthSections
          currentUrl={currentUrl}
          dashboard={dashboard}
          activeIncidentsHref={activeIncidentsHref}
          state={state}
        />
        <section class="grid gap-4 xl:grid-cols-[minmax(0,1.3fr)_minmax(18rem,0.7fr)]">
          <AdminDashboardDrillDownPanel
            drillDown={drillDown}
            drillDownError={drillDownError}
            activeIncidentsHref={activeIncidentsHref}
            activeSelectionSummary={summarizeSelection(
              state.activeDrillDownSelection,
            )}
          />
          <AdminDashboardEvidenceRail
            currentUrl={currentUrl}
            dashboard={dashboard}
            state={state}
          />
        </section>
      </div>
    );
  },
);
