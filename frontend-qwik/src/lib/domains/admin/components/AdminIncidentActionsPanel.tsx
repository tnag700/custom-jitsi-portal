import { component$ } from "@qwik.dev/core";
import { Form } from "@qwik.dev/router";
import { buildIncidentNextActionHref } from "../admin-incidents.route-helpers";
import type { IncidentDetailDerivedState } from "../admin-incidents.route-helpers";
import type { AdminIncidentDetail } from "../types";

interface AdminIncidentActionsPanelProps {
  currentUrl: string;
  incident: AdminIncidentDetail;
  detailState: IncidentDetailDerivedState;
  canManageTicket: boolean;
  ticketAction: unknown;
}

export const AdminIncidentActionsPanel = component$(
  ({
    currentUrl,
    incident,
    detailState,
    canManageTicket,
    ticketAction,
  }: AdminIncidentActionsPanelProps) => {
    const url = new URL(currentUrl);
    const {
      ticketing,
      effectiveTicketReference,
      effectiveTicketUrl,
      effectiveTicketStatus,
    } = detailState;

    return (
      <section class="rounded-3xl border border-border bg-surface p-4 shadow-sm sm:p-5">
        <h3 class="text-lg font-semibold text-text">Следующие шаги</h3>
        <div class="mt-3 space-y-2">
          {incident.nextActions.map((action) => {
            const href = buildIncidentNextActionHref(
              url,
              action,
              incident.relatedLinks,
              incident.environment,
            );
            return href ? (
              <a
                key={`${action.kind}-${action.target}-${action.label}`}
                href={href}
                target={action.externalUrl ? "_blank" : undefined}
                rel={action.externalUrl ? "noreferrer" : undefined}
                class="block rounded-2xl border border-border bg-bg px-3 py-3 transition-colors hover:bg-surface-alt"
              >
                <p class="text-sm font-medium text-text">{action.label}</p>
                <p class="mt-1 text-xs text-muted">{action.detail}</p>
              </a>
            ) : (
              <article
                key={`${action.kind}-${action.target}-${action.label}`}
                class="rounded-2xl border border-dashed border-border bg-bg px-3 py-3"
              >
                <p class="text-sm font-medium text-text">{action.label}</p>
                <p class="mt-1 text-xs text-muted">{action.detail}</p>
              </article>
            );
          })}
        </div>

        <div class="mt-5 border-t border-border pt-4">
          <div class="flex items-center justify-between gap-3">
            <h4 class="font-semibold text-text">Тикет</h4>
            <span class="text-xs text-muted">
              Статус: {effectiveTicketStatus}
            </span>
          </div>
          <p class="mt-1 text-sm text-muted">
            Создайте внешний тикет, если расследование требует отдельного
            сопровождения.
          </p>
          {ticketing.available && canManageTicket ? (
            <Form
              action={ticketAction as never}
              class="mt-3 flex flex-wrap items-center gap-3"
            >
              <input
                type="hidden"
                name="incidentId"
                value={incident.incidentId}
              />
              <input
                type="hidden"
                name="environment"
                value={incident.environment}
              />
              <button
                type="submit"
                class="rounded-xl border border-text bg-text px-4 py-2 text-sm font-medium text-bg"
              >
                Создать тикет
              </button>
              {effectiveTicketUrl ? (
                <a
                  href={effectiveTicketUrl}
                  target="_blank"
                  rel="noreferrer"
                  class="text-sm font-medium underline"
                >
                  {effectiveTicketReference ?? "Открыть тикет"}
                </a>
              ) : effectiveTicketReference ? (
                <span class="text-sm font-medium text-text">
                  {effectiveTicketReference}
                </span>
              ) : null}
            </Form>
          ) : ticketing.available ? (
            <p class="mt-3 text-sm text-muted">
              Создание тикета доступно только администратору.
            </p>
          ) : (
            <p class="mt-3 text-sm text-muted">
              Внешняя тикет-система недоступна для текущего окружения.
            </p>
          )}
        </div>
      </section>
    );
  },
);
