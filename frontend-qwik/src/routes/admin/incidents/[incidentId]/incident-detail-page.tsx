import { component$ } from "@qwik.dev/core";
import { useLocation } from "@qwik.dev/router";
import {
  AdminIncidentDetailOverview,
  buildIncidentDetailDerivedState,
  getIncidentActionError,
  getIncidentCoordinationActionResult,
  getIncidentTicketActionResult,
} from "~/lib/domains/admin";
import { ApiErrorAlert, RequestStatePanel } from "~/lib/shared";
import {
  useCreateIncidentTicket,
  useIncidentDetail,
  useUpdateIncidentCoordination,
} from "./route-handlers";

export default component$(() => {
  const loader = useIncidentDetail();
  const ticketAction = useCreateIncidentTicket();
  const coordinationAction = useUpdateIncidentCoordination();
  const location = useLocation();
  const { incident, loadError, canManageTicket } = loader.value;

  if (loadError) {
    return (
      <ApiErrorAlert
        title={loadError.title}
        message={loadError.detail}
        errorCode={loadError.errorCode}
        traceId={loadError.traceId}
      />
    );
  }

  if (!incident) {
    return (
      <RequestStatePanel
        title="Инцидент не найден"
        detail="Проверьте идентификатор инцидента или окружение."
      />
    );
  }

  const ticketResult = getIncidentTicketActionResult(ticketAction.value);
  const coordinationResult = getIncidentCoordinationActionResult(
    coordinationAction.value,
  );

  return (
    <AdminIncidentDetailOverview
      currentUrl={location.url.href}
      incident={incident}
      detailState={buildIncidentDetailDerivedState(
        incident,
        ticketResult,
        coordinationResult,
      )}
      canManageTicket={canManageTicket}
      ticketAction={ticketAction}
      coordinationAction={coordinationAction}
      ticketError={getIncidentActionError(ticketAction.value)}
      coordinationError={getIncidentActionError(coordinationAction.value)}
    />
  );
});
