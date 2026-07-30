import { component$ } from "@qwik.dev/core";
import { Form } from "@qwik.dev/router";
import {
  formatIncidentDateTime,
  formatIncidentCoordinationStatus,
  type IncidentDetailDerivedState,
} from "../admin-incidents.route-helpers";
import type { AdminIncidentDetail } from "../types";

interface AdminIncidentCoordinationPanelProps {
  incident: AdminIncidentDetail;
  detailState: IncidentDetailDerivedState;
  canManageTicket: boolean;
  coordinationAction: unknown;
}

export const AdminIncidentCoordinationPanel = component$(
  ({
    incident,
    detailState,
    canManageTicket,
    coordinationAction,
  }: AdminIncidentCoordinationPanelProps) => {
    const coordination = detailState.coordination;

    return (
      <details
        open={coordination.enabled ? true : undefined}
        class="rounded-3xl border border-border bg-surface p-4 shadow-sm sm:p-5"
      >
        <summary class="cursor-pointer">
          <span class="flex items-center justify-between gap-3">
            <span class="font-semibold text-text">Координация</span>
            <span class="text-xs text-muted">
              {formatIncidentCoordinationStatus(
                coordination.workflowStatus,
              )}
            </span>
          </span>
        </summary>

        <p class="mt-3 text-sm text-muted">{coordination.explanation}</p>

        {coordination.enabled ? (
          <>
            <dl class="mt-4 grid grid-cols-2 gap-3 text-sm">
              <div>
                <dt class="text-xs text-muted">Ответственный</dt>
                <dd class="mt-1 text-text">
                  {coordination.owner ?? "Не назначен"}
                </dd>
              </div>
              <div>
                <dt class="text-xs text-muted">Связанный тикет</dt>
                <dd class="mt-1 break-all text-text">
                  {coordination.ticketReference ?? "Не привязан"}
                </dd>
              </div>
              <div>
                <dt class="text-xs text-muted">Этап</dt>
                <dd class="mt-1 text-text">
                  {formatIncidentCoordinationStatus(
                    coordination.workflowStatus,
                  )}
                </dd>
              </div>
              <div>
                <dt class="text-xs text-muted">Статус тикета</dt>
                <dd class="mt-1 text-text">
                  {coordination.ticketStatus}
                </dd>
              </div>
            </dl>

            {canManageTicket ? (
              <Form
                action={coordinationAction as never}
                class="mt-4 space-y-3 rounded-2xl border border-border bg-bg p-4"
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
                <label class="block text-sm text-muted">
                  <span class="font-medium text-text">Ответственный</span>
                  <input
                    type="text"
                    name="owner"
                    value={coordination.owner ?? ""}
                    placeholder="lead.support"
                    class="mt-2 w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-text"
                  />
                </label>
                <label class="block text-sm text-muted">
                  <span class="font-medium text-text">Этап работы</span>
                  <select
                    name="workflowStatus"
                    value={coordination.workflowStatus}
                    class="mt-2 w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-text"
                  >
                    <option value="triage">Первичная оценка</option>
                    <option value="investigating">Расследование</option>
                    <option value="waiting-external">
                      Ожидание внешней команды
                    </option>
                    <option value="resolved">Решён</option>
                  </select>
                </label>
                <label class="block text-sm text-muted">
                  <span class="font-medium text-text">Номер тикета</span>
                  <input
                    type="text"
                    name="ticketReference"
                    value={coordination.ticketReference ?? ""}
                    placeholder="INC-42"
                    class="mt-2 w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-text"
                  />
                </label>
                <label class="block text-sm text-muted">
                  <span class="font-medium text-text">Статус тикета</span>
                  <select
                    name="ticketStatus"
                    value={
                      coordination.ticketReference
                        ? coordination.ticketStatus
                        : "not-linked"
                    }
                    class="mt-2 w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-text"
                  >
                    <option value="not-linked">Не привязан</option>
                    <option value="linked">Привязан</option>
                    <option value="waiting-external">
                      Ожидание внешней команды
                    </option>
                    <option value="resolved">Решён</option>
                  </select>
                </label>
                <p class="text-xs text-muted">
                  Оставьте номер пустым, чтобы снять существующую привязку.
                </p>
                <button
                  type="submit"
                  class="rounded-xl border border-text bg-text px-4 py-2 text-sm font-medium text-bg"
                >
                  Сохранить координацию
                </button>
              </Form>
            ) : (
              <p class="mt-4 text-sm text-muted">
                Изменение координации доступно только администратору.
              </p>
            )}
          </>
        ) : (
          <p class="mt-3 text-xs text-muted">
            Раздел остаётся справочным, пока функция не включена для
            окружения.
          </p>
        )}

        {coordination.history.length > 0 ? (
          <div class="mt-4 space-y-3 border-t border-border pt-4">
            <p class="text-xs uppercase tracking-[0.18em] text-muted">
              Последние изменения
            </p>
            {coordination.history.map((entry) => (
              <article
                key={`${entry.occurredAt}-${entry.actorId}-${entry.actionType}`}
                class="rounded-2xl border border-border bg-bg p-3 text-sm text-muted"
              >
                <div class="flex items-center justify-between gap-3">
                  <p class="font-medium text-text">{entry.actionType}</p>
                  <time dateTime={entry.occurredAt} class="text-xs">
                    {formatIncidentDateTime(entry.occurredAt)}
                  </time>
                </div>
                <p class="mt-2">Исполнитель: {entry.actorId}</p>
                <p class="mt-1">Было: {entry.fromState}</p>
                <p class="mt-1">Стало: {entry.toState}</p>
                <p class="mt-1 break-all text-xs">
                  Trace ID: {entry.traceId ?? "n/a"}
                </p>
              </article>
            ))}
          </div>
        ) : null}
      </details>
    );
  },
);
