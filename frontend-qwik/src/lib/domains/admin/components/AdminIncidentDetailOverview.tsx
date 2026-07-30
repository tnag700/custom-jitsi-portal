import { component$ } from "@qwik.dev/core";
import { ApiErrorAlert } from "~/lib/shared";
import {
  resolveIncidentReturnTo,
  type IncidentDetailDerivedState,
} from "../admin-incidents.route-helpers";
import type {
  AdminDashboardErrorPayload,
  AdminIncidentDetail,
} from "../types";
import { AdminIncidentActionsPanel } from "./AdminIncidentActionsPanel";
import { AdminIncidentCoordinationPanel } from "./AdminIncidentCoordinationPanel";
import { AdminIncidentInvestigation } from "./AdminIncidentInvestigation";
import { AdminIncidentSummary } from "./AdminIncidentSummary";

interface AdminIncidentDetailOverviewProps {
  currentUrl: string;
  incident: AdminIncidentDetail;
  detailState: IncidentDetailDerivedState;
  canManageTicket: boolean;
  ticketAction: unknown;
  coordinationAction: unknown;
  ticketError: AdminDashboardErrorPayload | null;
  coordinationError: AdminDashboardErrorPayload | null;
}

export const AdminIncidentDetailOverview = component$(
  ({
    currentUrl,
    incident,
    detailState,
    canManageTicket,
    ticketAction,
    coordinationAction,
    ticketError,
    coordinationError,
  }: AdminIncidentDetailOverviewProps) => {
    const returnTo = resolveIncidentReturnTo(
      new URL(currentUrl),
      incident.environment,
    );

    return (
      <div class="space-y-4 md:space-y-5">
        <AdminIncidentSummary incident={incident} returnTo={returnTo} />

        {ticketError ? (
          <ApiErrorAlert
            title={ticketError.title}
            message={ticketError.detail}
            errorCode={ticketError.errorCode}
            traceId={ticketError.traceId}
          />
        ) : null}

        {coordinationError ? (
          <ApiErrorAlert
            title={coordinationError.title}
            message={coordinationError.detail}
            errorCode={coordinationError.errorCode}
            traceId={coordinationError.traceId}
          />
        ) : null}

        <section class="grid gap-4 xl:grid-cols-[minmax(0,1.35fr)_minmax(20rem,0.65fr)]">
          <AdminIncidentInvestigation
            currentUrl={currentUrl}
            incident={incident}
          />
          <aside class="space-y-4">
            <AdminIncidentActionsPanel
              currentUrl={currentUrl}
              incident={incident}
              detailState={detailState}
              canManageTicket={canManageTicket}
              ticketAction={ticketAction}
            />
            <AdminIncidentCoordinationPanel
              incident={incident}
              detailState={detailState}
              canManageTicket={canManageTicket}
              coordinationAction={coordinationAction}
            />
          </aside>
        </section>
      </div>
    );
  },
);
