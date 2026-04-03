import { component$ } from "@qwik.dev/core";
import { Form, routeAction$, routeLoader$, useLocation, z, zod$, type DocumentHead } from "@qwik.dev/router";
import { resolveAuthRecoveryRedirectPath, type SafeUserProfile } from "~/lib/domains/auth";
import {
  AdminDashboardServiceError,
  buildIncidentDetailDerivedState,
  buildIncidentEmptyStateHref,
  buildIncidentMutationAccessDenied,
  buildIncidentMutationUnexpectedError,
  buildIncidentNextActionHref,
  buildIncidentRelatedHref,
  canManageIncidentTicket,
  createAdminIncidentTicket,
  fetchAdminIncidentDetail,
  formatIncidentCoordinationStatus,
  getIncidentActionError,
  getIncidentCoordinationActionResult,
  getIncidentTicketActionResult,
  resolveIncidentReturnTo,
  updateAdminIncidentCoordination,
  type AdminDashboardErrorPayload,
  type AdminIncidentDetail,
} from "~/lib/domains/admin";
import { ApiErrorAlert, RequestStatePanel } from "~/lib/shared";
import { buildMutationRequestContext, buildServerRequestContext } from "~/lib/shared/routes/server-handlers";

interface IncidentDetailLoaderData {
  incident: AdminIncidentDetail | null;
  loadError: AdminDashboardErrorPayload | null;
  canManageTicket: boolean;
}

function buildOverviewHref(environment: string): string {
  return environment.trim().length > 0 ? `/admin?environment=${encodeURIComponent(environment)}` : "/admin";
}

export const useIncidentDetail = routeLoader$(async ({ sharedMap, cookie, params, query, redirect, url }) => {
  const user = (sharedMap.get("user") as SafeUserProfile | null) ?? null;
  const requestContext = buildServerRequestContext({ sharedMap, cookie });
  const returnTo = `${url.pathname}${url.search}`;
  try {
    const incident = await fetchAdminIncidentDetail(
      requestContext,
      params.incidentId,
      query.get("environment")?.trim() || undefined,
    );
    return { incident, loadError: null, canManageTicket: canManageIncidentTicket(user) } satisfies IncidentDetailLoaderData;
  } catch (error) {
    if (error instanceof AdminDashboardServiceError) {
      if (error.payload.errorCode === "AUTH_REQUIRED") {
        throw redirect(302, resolveAuthRecoveryRedirectPath(error, returnTo));
      }
      if (error.payload.errorCode === "ACCESS_DENIED") {
        throw redirect(302, "/");
      }
      return { incident: null, loadError: error.payload, canManageTicket: canManageIncidentTicket(user) } satisfies IncidentDetailLoaderData;
    }
    throw error;
  }
});

export const useCreateIncidentTicket = routeAction$(
  async (data, { sharedMap, cookie, fail, redirect, url }) => {
    const user = (sharedMap.get("user") as SafeUserProfile | null) ?? null;
    const returnTo = `${url.pathname}${url.search}`;
    if (!canManageIncidentTicket(user)) {
      return fail(403, {
        error: buildIncidentMutationAccessDenied("Создание external ticket доступно только admin."),
      });
    }
    try {
      const requestContext = await buildMutationRequestContext({ sharedMap, cookie });
      const ticket = await createAdminIncidentTicket(requestContext, data.incidentId, data.environment);
      return { success: true as const, ticket };
    } catch (error) {
      if (error instanceof AdminDashboardServiceError) {
        if (error.payload.errorCode === "AUTH_REQUIRED") {
          throw redirect(302, resolveAuthRecoveryRedirectPath(error, returnTo));
        }
        return fail(400, { error: error.payload });
      }
      return fail(500, {
        error: buildIncidentMutationUnexpectedError(
          "Ошибка ticket action",
          "Не удалось создать внешний тикет.",
          "ADMIN_INCIDENT_TICKET_FAILED",
        ),
      });
    }
  },
  zod$(z.object({ incidentId: z.string().min(1), environment: z.string().optional() })),
);

export const useUpdateIncidentCoordination = routeAction$(
  async (data, { sharedMap, cookie, fail, redirect, url }) => {
    const user = (sharedMap.get("user") as SafeUserProfile | null) ?? null;
    const returnTo = `${url.pathname}${url.search}`;
    if (!canManageIncidentTicket(user)) {
      return fail(403, {
        error: buildIncidentMutationAccessDenied("Изменение coordination seam доступно только admin."),
      });
    }
    try {
      const requestContext = await buildMutationRequestContext({ sharedMap, cookie });
      const coordination = await updateAdminIncidentCoordination(requestContext, data.incidentId, {
        environment: data.environment,
        owner: data.owner,
        workflowStatus: data.workflowStatus,
        ticketReference: data.ticketReference,
        ticketStatus: data.ticketStatus,
      });
      return { success: true as const, coordination };
    } catch (error) {
      if (error instanceof AdminDashboardServiceError) {
        if (error.payload.errorCode === "AUTH_REQUIRED") {
          throw redirect(302, resolveAuthRecoveryRedirectPath(error, returnTo));
        }
        return fail(400, { error: error.payload });
      }
      return fail(500, {
        error: buildIncidentMutationUnexpectedError(
          "Ошибка coordination seam",
          "Не удалось обновить owner, workflow status или ticket link.",
          "ADMIN_INCIDENT_COORDINATION_FAILED",
        ),
      });
    }
  },
  zod$(z.object({
    incidentId: z.string().min(1),
    environment: z.string().optional(),
    owner: z.string().optional(),
    workflowStatus: z.enum(["triage", "investigating", "waiting-external", "resolved"]),
    ticketReference: z.string().optional(),
    ticketStatus: z.enum(["not-linked", "linked", "waiting-external", "resolved"]).optional(),
  })),
);

export default component$(() => {
  const loader = useIncidentDetail();
  const ticketAction$ = useCreateIncidentTicket();
  const coordinationAction$ = useUpdateIncidentCoordination();
  const action$Ticket = ticketAction$;
  const action$Coordination = coordinationAction$;
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
    return <RequestStatePanel title="Инцидент не найден" detail="Проверьте incidentId или уточните окружение." />;
  }

  const ticketResult = getIncidentTicketActionResult(ticketAction$.value);
  const coordinationResult = getIncidentCoordinationActionResult(coordinationAction$.value);
  const ticketError = getIncidentActionError(ticketAction$.value);
  const coordinationError = getIncidentActionError(coordinationAction$.value);
  const {
    coordination,
    ticketing,
    effectiveTicketReference,
    effectiveTicketUrl,
    effectiveTicketStatus,
  } = buildIncidentDetailDerivedState(incident, ticketResult, coordinationResult);
  const returnTo = resolveIncidentReturnTo(location.url, incident.environment);
  const overviewHref = buildOverviewHref(incident.environment);
  const summaryBar = incident.summaryBar;
  const timeline = incident.timeline;
  const evidence = incident.evidence;
  const relatedLinks = incident.relatedLinks;
  const nextActions = incident.nextActions;

  return (
    <div class="space-y-6">
      <section class="rounded-3xl border border-border bg-surface px-6 py-5 shadow-sm">
        <nav aria-label="Triage context" class="flex flex-wrap items-center gap-2 text-sm text-muted">
          <a href={overviewHref} class="underline">Сводка</a>
          <span>/</span>
          <a href={returnTo} class="underline">Инциденты</a>
          <span>/</span>
          <span class="text-text">Деталь инцидента</span>
        </nav>
        <a href={returnTo} class="mt-3 inline-block text-sm text-muted underline">Вернуться к списку</a>
        <p class="mt-2 text-xs uppercase tracking-[0.22em] text-muted">Investigation workspace</p>
        <div class="mt-4 grid gap-4 xl:grid-cols-[1.35fr_0.65fr]">
          <div>
            <p class="text-xs uppercase tracking-[0.18em] text-muted">Краткая сводка</p>
            <h2 class="mt-2 text-2xl font-semibold text-text">{summaryBar.title}</h2>
            <p class="mt-2 text-sm text-muted">{incident.summary}</p>
            <div class="mt-4 flex flex-wrap gap-2 text-sm">
              <span class="rounded-full border border-border px-3 py-1 text-text">severity: {incident.severity}</span>
              <span class="rounded-full border border-border px-3 py-1 text-text">{summaryBar.refusalReason}</span>
              <span class="rounded-full border border-border px-3 py-1 text-text">status: {summaryBar.operationalStatus}</span>
              <span class="rounded-full border border-border px-3 py-1 text-text">env: {summaryBar.environment}</span>
            </div>
          </div>
          <div class="rounded-2xl border border-border bg-bg p-4">
            <p class="text-xs uppercase tracking-[0.18em] text-muted">First scan</p>
            <dl class="mt-3 space-y-2 text-sm text-muted">
              <div>
                <dt class="font-medium text-text">Affected scope</dt>
                <dd>{summaryBar.affectedScope}</dd>
              </div>
              <div>
                <dt class="font-medium text-text">Time window</dt>
                <dd>{summaryBar.timeWindow}</dd>
              </div>
            </dl>
          </div>
        </div>
      </section>

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

      <section class="grid gap-6 xl:grid-cols-[1.2fr_0.8fr]">
        <div class="space-y-6">
          <section class="rounded-3xl border border-border bg-surface p-5 shadow-sm">
            <h3 class="text-lg font-semibold text-text">Таймлайн сигналов</h3>
            {timeline.length > 0 ? (
              <div class="mt-4 space-y-3">
                {timeline.map((entry) => (
                  <article key={`${entry.occurredAt}-${entry.traceId ?? entry.correlationId ?? entry.meetingId ?? entry.roomId ?? entry.title}`} class="rounded-2xl border border-border bg-bg p-4">
                    <div class="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                      <div>
                        <p class="text-sm font-medium text-text">{entry.title}</p>
                        <p class="mt-1 text-sm text-muted">{entry.summary}</p>
                      </div>
                      <p class="text-xs text-muted">{entry.occurredAt}</p>
                    </div>
                    <div class="mt-3 grid gap-1 text-xs text-muted sm:grid-cols-2 xl:grid-cols-4">
                      <p>Subject: {entry.subjectDisplay ?? "n/a"}</p>
                      <p>Role: {entry.role ?? "n/a"}</p>
                      <p>Trace ID: {entry.traceId ?? "n/a"}</p>
                      <p>Correlation ID: {entry.correlationId ?? "n/a"}</p>
                    </div>
                  </article>
                ))}
              </div>
            ) : (
              <RequestStatePanel title="Нет timeline событий" detail="Для инцидента не найден связанный таймлайн сигналов." />
            )}
          </section>

          <section class="rounded-3xl border border-border bg-surface p-5 shadow-sm">
            <h3 class="text-lg font-semibold text-text">Diagnostics evidence</h3>
            <div class="mt-4 space-y-3">
              {evidence.map((block) => {
                const emptyStateHref = block.emptyState
                  ? buildIncidentEmptyStateHref(location.url, block.emptyState.nextActionTarget, relatedLinks, incident.environment)
                  : null;

                return (
                  <article key={`${block.kind}-${block.status}-${block.traceId ?? block.correlationId ?? block.title}`} class="rounded-2xl border border-border bg-bg p-4">
                    <div class="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                      <div>
                        <p class="text-sm font-medium text-text">{block.title}</p>
                        <p class="text-xs uppercase tracking-[0.18em] text-muted">{block.status}</p>
                      </div>
                      {block.traceUrl ? (
                        <a href={block.traceUrl} target="_blank" rel="noreferrer" class="text-sm font-medium underline">
                          Открыть trace
                        </a>
                      ) : null}
                    </div>
                    {block.summary ? <p class="mt-3 text-sm text-text">{block.summary}</p> : null}
                    <p class="mt-2 text-sm text-muted">{block.detail}</p>
                    <div class="mt-3 grid gap-1 text-xs text-muted sm:grid-cols-2">
                      <p>Trace ID: {block.traceId ?? "n/a"}</p>
                      <p>Correlation ID: {block.correlationId ?? "n/a"}</p>
                    </div>
                    {block.emptyState ? (
                      <div class="mt-4 rounded-2xl border border-dashed border-border px-4 py-3 text-sm text-muted">
                        <p class="font-medium text-text">{block.emptyState.title}</p>
                        <p class="mt-1">{block.emptyState.detail}</p>
                        {emptyStateHref ? (
                          <a href={emptyStateHref} class="mt-3 inline-block text-xs font-medium uppercase tracking-[0.16em] underline">
                            {block.emptyState.nextActionLabel}
                          </a>
                        ) : (
                          <p class="mt-2 text-xs uppercase tracking-[0.16em]">{block.emptyState.nextActionLabel}</p>
                        )}
                      </div>
                    ) : null}
                  </article>
                );
              })}
            </div>
          </section>

          <section class="rounded-3xl border border-border bg-surface p-5 shadow-sm">
            <h3 class="text-lg font-semibold text-text">Связанный контекст</h3>
            {relatedLinks.length > 0 ? (
              <div class="mt-4 grid gap-3 md:grid-cols-2">
                {relatedLinks.map((link) => {
                  const href = buildIncidentRelatedHref(location.url, link, incident.environment);
                  return (
                    <article key={`${link.kind}-${link.traceId ?? link.subjectId ?? link.roomId ?? link.meetingId ?? link.label}`} class="rounded-2xl border border-border bg-bg p-4">
                      <p class="text-sm font-medium text-text">{link.label}</p>
                      <p class="mt-2 text-xs text-muted">kind: {link.kind}</p>
                      <p class="mt-1 text-xs text-muted">subjectId: {link.subjectId ?? "n/a"}</p>
                      <p class="text-xs text-muted">roomId: {link.roomId ?? "n/a"} · meetingId: {link.meetingId ?? "n/a"}</p>
                      {href ? (
                        <a href={href} target={link.externalUrl ? "_blank" : undefined} rel={link.externalUrl ? "noreferrer" : undefined} class="mt-3 inline-block text-sm font-medium underline">
                          Открыть
                        </a>
                      ) : (
                        <p class="mt-3 text-sm text-muted">Bounded переход недоступен, используйте next actions ниже.</p>
                      )}
                    </article>
                  );
                })}
              </div>
            ) : (
              <div class="mt-4 rounded-2xl border border-dashed border-border bg-bg p-4">
                <p class="text-sm font-medium text-text">Связанный контекст недоступен</p>
                <p class="mt-2 text-sm text-muted">Используйте очередь и bounded evidence blocks как следующий шаг расследования.</p>
                <div class="mt-4 flex flex-wrap gap-3">
                  {nextActions
                    .filter((action) => action.target === "queue-return" || action.target === "role-history")
                    .map((action) => {
                      const href = buildIncidentNextActionHref(location.url, action, relatedLinks, incident.environment);
                      return href ? (
                        <a key={`${action.kind}-${action.target}-${action.label}`} href={href} class="text-sm font-medium underline">
                          {action.label}
                        </a>
                      ) : null;
                    })}
                </div>
              </div>
            )}
          </section>

          <details class="rounded-3xl border border-border bg-surface p-5 shadow-sm">
            <summary class="cursor-pointer text-lg font-semibold text-text">Технические детали</summary>
            {incident.affectedAttempts.length > 0 ? (
              <div class="mt-4 space-y-3">
                {incident.affectedAttempts.map((attempt) => (
                  <article key={`${attempt.correlationId ?? attempt.traceId ?? attempt.occurredAt}-${attempt.occurredAt}`} class="rounded-2xl border border-border bg-bg p-4 text-xs text-muted">
                    <p>occurredAt: {attempt.occurredAt}</p>
                    <p>subject: {attempt.subjectDisplay ?? "n/a"}</p>
                    <p>role: {attempt.role ?? "n/a"}</p>
                    <p>traceId: {attempt.traceId ?? "n/a"}</p>
                    <p>correlationId: {attempt.correlationId ?? "n/a"}</p>
                    <p>roomId: {attempt.roomId ?? "n/a"}</p>
                    <p>meetingId: {attempt.meetingId ?? "n/a"}</p>
                  </article>
                ))}
              </div>
            ) : (
              <RequestStatePanel title="Нет технических деталей" detail="Bounded attempts для этого инцидента отсутствуют." />
            )}
          </details>
        </div>

        <aside class="space-y-6">
          <section class="rounded-3xl border border-border bg-surface p-5 shadow-sm">
            <div class="flex items-center justify-between gap-3">
              <h3 class="text-lg font-semibold text-text">Coordination seam</h3>
              <span class="text-sm text-muted">workflow: {formatIncidentCoordinationStatus(coordination.workflowStatus)}</span>
            </div>
            <p class="mt-2 text-sm text-muted">{coordination.explanation}</p>
            <dl class="mt-4 grid gap-3 text-sm text-muted sm:grid-cols-2">
              <div>
                <dt class="font-medium text-text">owner</dt>
                <dd>{coordination.owner ?? "Не назначен"}</dd>
              </div>
              <div>
                <dt class="font-medium text-text">ticketReference</dt>
                <dd>{coordination.ticketReference ?? "Не привязан"}</dd>
              </div>
              <div>
                <dt class="font-medium text-text">workflowStatus</dt>
                <dd>{formatIncidentCoordinationStatus(coordination.workflowStatus)}</dd>
              </div>
              <div>
                <dt class="font-medium text-text">ticketStatus</dt>
                <dd>{coordination.ticketStatus}</dd>
              </div>
            </dl>
            {coordination.enabled ? (
              canManageTicket ? (
                <Form action={action$Coordination} class="mt-4 space-y-3 rounded-2xl border border-border bg-bg p-4">
                  <input type="hidden" name="incidentId" value={incident.incidentId} />
                  <input type="hidden" name="environment" value={incident.environment} />
                  <label class="block text-sm text-muted">
                    <span class="font-medium text-text">Owner</span>
                    <input
                      type="text"
                      name="owner"
                      value={coordination.owner ?? ""}
                      placeholder="lead.support"
                      class="mt-2 w-full rounded-2xl border border-border bg-surface px-3 py-2 text-sm text-text"
                    />
                  </label>
                  <label class="block text-sm text-muted">
                    <span class="font-medium text-text">Workflow status</span>
                    <select name="workflowStatus" value={coordination.workflowStatus} class="mt-2 w-full rounded-2xl border border-border bg-surface px-3 py-2 text-sm text-text">
                      <option value="triage">Triage</option>
                      <option value="investigating">Investigating</option>
                      <option value="waiting-external">Waiting external</option>
                      <option value="resolved">Resolved</option>
                    </select>
                  </label>
                  <label class="block text-sm text-muted">
                    <span class="font-medium text-text">Existing ticket reference</span>
                    <input
                      type="text"
                      name="ticketReference"
                      value={coordination.ticketReference ?? ""}
                      placeholder="INC-42"
                      class="mt-2 w-full rounded-2xl border border-border bg-surface px-3 py-2 text-sm text-text"
                    />
                  </label>
                  <label class="block text-sm text-muted">
                    <span class="font-medium text-text">Ticket status</span>
                    <select
                      name="ticketStatus"
                      value={coordination.ticketReference ? coordination.ticketStatus : "not-linked"}
                      class="mt-2 w-full rounded-2xl border border-border bg-surface px-3 py-2 text-sm text-text"
                    >
                      <option value="not-linked">Not linked</option>
                      <option value="linked">Linked</option>
                      <option value="waiting-external">Waiting external</option>
                      <option value="resolved">Resolved</option>
                    </select>
                  </label>
                  <p class="text-xs text-muted">
                    Оставьте reference пустым, чтобы снять привязку существующего тикета без создания нового.
                  </p>
                  <button type="submit" class="rounded-2xl border border-text bg-text px-4 py-2 text-sm font-medium text-bg">
                    Сохранить coordination
                  </button>
                </Form>
              ) : (
                <p class="mt-4 text-sm text-muted">Изменение coordination metadata доступно только для роли admin.</p>
              )
            ) : (
              <p class="mt-4 text-sm text-muted">Coordination rollout остаётся optional: detail screen сохраняет read-first режим даже без включения seam.</p>
            )}
            {coordination.history.length > 0 ? (
              <div class="mt-4 space-y-3 border-t border-border pt-4">
                <p class="text-xs uppercase tracking-[0.18em] text-muted">Recent coordination history</p>
                {coordination.history.map((entry) => (
                  <article key={`${entry.occurredAt}-${entry.actorId}-${entry.actionType}`} class="rounded-2xl border border-border bg-bg p-3 text-sm text-muted">
                    <div class="flex items-center justify-between gap-3">
                      <p class="font-medium text-text">{entry.actionType}</p>
                      <p class="text-xs text-muted">{entry.occurredAt}</p>
                    </div>
                    <p class="mt-2">actor: {entry.actorId}</p>
                    <p class="mt-1">from: {entry.fromState}</p>
                    <p class="mt-1">to: {entry.toState}</p>
                    <p class="mt-1 text-xs">trace: {entry.traceId ?? "n/a"}</p>
                  </article>
                ))}
              </div>
            ) : null}
          </section>

          <section class="rounded-3xl border border-border bg-surface p-5 shadow-sm">
            <h3 class="text-lg font-semibold text-text">Следующие шаги</h3>
            <div class="mt-4 space-y-3">
              {nextActions.map((action) => {
                const href = buildIncidentNextActionHref(location.url, action, relatedLinks, incident.environment);
                return href ? (
                  <a key={`${action.kind}-${action.target}-${action.label}`} href={href} target={action.externalUrl ? "_blank" : undefined} rel={action.externalUrl ? "noreferrer" : undefined} class="block rounded-2xl border border-border bg-bg px-4 py-4 transition-colors hover:bg-surface-alt">
                    <p class="text-sm font-medium text-text">{action.label}</p>
                    <p class="mt-2 text-sm text-muted">{action.detail}</p>
                  </a>
                ) : (
                  <article key={`${action.kind}-${action.target}-${action.label}`} class="rounded-2xl border border-dashed border-border bg-bg px-4 py-4">
                    <p class="text-sm font-medium text-text">{action.label}</p>
                    <p class="mt-2 text-sm text-muted">{action.detail}</p>
                  </article>
                );
              })}
            </div>
          </section>

          <section class="rounded-3xl border border-border bg-surface p-5 shadow-sm">
            <div class="flex items-center justify-between gap-3">
              <h3 class="text-lg font-semibold text-text">External Ticketing</h3>
              <span class="text-sm text-muted">status: {effectiveTicketStatus}</span>
            </div>
            <p class="mt-2 text-sm text-muted">Ticket action остаётся adjunct action и не становится primary mode detail page.</p>
            {ticketing.available && canManageTicket ? (
              <Form action={action$Ticket} class="mt-4 flex flex-wrap items-center gap-3">
                <input type="hidden" name="incidentId" value={incident.incidentId} />
                <input type="hidden" name="environment" value={incident.environment} />
                <button type="submit" class="rounded-2xl border border-text bg-text px-4 py-2 text-sm font-medium text-bg">
                  Создать тикет
                </button>
                {effectiveTicketUrl ? (
                  <a href={effectiveTicketUrl} target="_blank" rel="noreferrer" class="text-sm font-medium underline">
                    {effectiveTicketReference ?? "Открыть тикет"}
                  </a>
                ) : effectiveTicketReference ? (
                  <span class="text-sm font-medium text-text">{effectiveTicketReference}</span>
                ) : null}
              </Form>
            ) : ticketing.available ? (
              <p class="mt-4 text-sm text-muted">Создание external ticket доступно только для роли admin.</p>
            ) : (
              <p class="mt-4 text-sm text-muted">External ticketing недоступен для текущего окружения.</p>
            )}
          </section>
        </aside>
      </section>
    </div>
  );
});

export const head: DocumentHead = {
  title: "Incident Detail — Jitsi Portal",
  meta: [
    {
      name: "description",
      content: "Детальная карточка инцидента с Correlation ID, compact coordination seam и optional ticket action.",
    },
  ],
};