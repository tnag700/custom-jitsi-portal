import { component$ } from "@qwik.dev/core";
import { RequestStatePanel } from "~/lib/shared";
import {
  buildIncidentEmptyStateHref,
  buildIncidentNextActionHref,
  buildIncidentRelatedHref,
  formatIncidentDateTime,
} from "../admin-incidents.route-helpers";
import type { AdminIncidentDetail } from "../types";

interface AdminIncidentInvestigationProps {
  currentUrl: string;
  incident: AdminIncidentDetail;
}

export const AdminIncidentInvestigation = component$(
  ({ currentUrl, incident }: AdminIncidentInvestigationProps) => {
    const url = new URL(currentUrl);
    const { timeline, evidence, relatedLinks, nextActions } = incident;

    return (
      <div class="space-y-4">
        <section class="rounded-3xl border border-border bg-surface p-4 shadow-sm sm:p-5">
          <div class="flex flex-wrap items-end justify-between gap-2">
            <div>
              <p class="text-xs uppercase tracking-[0.18em] text-muted">
                Данные расследования
              </p>
              <h3 class="mt-1 text-lg font-semibold text-text">
                Сигналы и диагностика
              </h3>
            </div>
            <p class="text-xs text-muted">
              {timeline.length} событий · {evidence.length} блоков
            </p>
          </div>

          <div class="mt-4 space-y-3">
            {timeline.length > 0 ? (
              timeline.map((entry) => (
                <article
                  key={`${entry.occurredAt}-${entry.traceId ?? entry.correlationId ?? entry.meetingId ?? entry.roomId ?? entry.title}`}
                  class="rounded-2xl border border-border bg-bg p-3 sm:p-4"
                >
                  <div class="flex flex-col gap-1 sm:flex-row sm:items-start sm:justify-between">
                    <div>
                      <p class="text-sm font-medium text-text">
                        {entry.title}
                      </p>
                      <p class="mt-1 text-sm text-muted">{entry.summary}</p>
                    </div>
                    <time
                      dateTime={entry.occurredAt}
                      class="shrink-0 text-xs text-muted"
                    >
                      {formatIncidentDateTime(entry.occurredAt)}
                    </time>
                  </div>
                  <div class="mt-3 grid gap-1 text-xs text-muted sm:grid-cols-2">
                    <p>Пользователь: {entry.subjectDisplay ?? "нет данных"}</p>
                    <p>Роль: {entry.role ?? "нет данных"}</p>
                    <p class="break-all">Trace ID: {entry.traceId ?? "n/a"}</p>
                    <p class="break-all">
                      Correlation ID: {entry.correlationId ?? "n/a"}
                    </p>
                  </div>
                </article>
              ))
            ) : (
              <RequestStatePanel
                title="Нет событий"
                detail="Для инцидента не найден связанный таймлайн."
              />
            )}
          </div>

          <div class="mt-5 border-t border-border pt-4">
            <h4 class="text-sm font-semibold text-text">
              Диагностические данные
            </h4>
            {evidence.length > 0 ? (
              <div class="mt-3 space-y-3">
                {evidence.map((block) => {
                  const emptyStateHref = block.emptyState
                    ? buildIncidentEmptyStateHref(
                        url,
                        block.emptyState.nextActionTarget,
                        relatedLinks,
                        incident.environment,
                      )
                    : null;

                  return (
                    <article
                      key={`${block.kind}-${block.status}-${block.traceId ?? block.correlationId ?? block.title}`}
                      class="rounded-2xl border border-border bg-bg p-3 sm:p-4"
                    >
                      <div class="flex flex-wrap items-start justify-between gap-2">
                        <div>
                          <p class="text-sm font-medium text-text">
                            {block.title}
                          </p>
                          <p class="mt-0.5 text-xs text-muted">
                            Статус: {block.status}
                          </p>
                        </div>
                        {block.traceUrl ? (
                          <a
                            href={block.traceUrl}
                            target="_blank"
                            rel="noreferrer"
                            class="text-sm font-medium underline"
                          >
                            Открыть трассировку
                          </a>
                        ) : null}
                      </div>
                      {block.summary ? (
                        <p class="mt-3 text-sm text-text">{block.summary}</p>
                      ) : null}
                      <p class="mt-1 text-sm text-muted">{block.detail}</p>
                      <div class="mt-3 grid gap-1 text-xs text-muted sm:grid-cols-2">
                        <p class="break-all">
                          Trace ID: {block.traceId ?? "n/a"}
                        </p>
                        <p class="break-all">
                          Correlation ID: {block.correlationId ?? "n/a"}
                        </p>
                      </div>
                      {block.emptyState ? (
                        <div class="mt-3 rounded-xl border border-dashed border-border px-3 py-3 text-sm text-muted">
                          <p class="font-medium text-text">
                            {block.emptyState.title}
                          </p>
                          <p class="mt-1">{block.emptyState.detail}</p>
                          {emptyStateHref ? (
                            <a
                              href={emptyStateHref}
                              class="mt-2 inline-block text-sm font-medium underline"
                            >
                              {block.emptyState.nextActionLabel}
                            </a>
                          ) : (
                            <p class="mt-2 text-xs">
                              {block.emptyState.nextActionLabel}
                            </p>
                          )}
                        </div>
                      ) : null}
                    </article>
                  );
                })}
              </div>
            ) : (
              <RequestStatePanel
                title="Нет диагностических данных"
                detail="Дополнительные evidence-блоки для инцидента отсутствуют."
              />
            )}
          </div>
        </section>

        <section class="rounded-3xl border border-border bg-surface p-4 shadow-sm sm:p-5">
          <h3 class="text-lg font-semibold text-text">
            Связанный контекст
          </h3>
          {relatedLinks.length > 0 ? (
            <div class="mt-3 grid gap-3 md:grid-cols-2">
              {relatedLinks.map((link) => {
                const href = buildIncidentRelatedHref(
                  url,
                  link,
                  incident.environment,
                );
                return (
                  <article
                    key={`${link.kind}-${link.traceId ?? link.subjectId ?? link.roomId ?? link.meetingId ?? link.label}`}
                    class="rounded-2xl border border-border bg-bg p-3 sm:p-4"
                  >
                    <p class="text-sm font-medium text-text">{link.label}</p>
                    <p class="mt-2 text-xs text-muted">Тип: {link.kind}</p>
                    <p class="mt-1 break-all text-xs text-muted">
                      Пользователь: {link.subjectId ?? "n/a"}
                    </p>
                    <p class="break-all text-xs text-muted">
                      Комната: {link.roomId ?? "n/a"} · Встреча:{" "}
                      {link.meetingId ?? "n/a"}
                    </p>
                    {href ? (
                      <a
                        href={href}
                        target={link.externalUrl ? "_blank" : undefined}
                        rel={link.externalUrl ? "noreferrer" : undefined}
                        class="mt-3 inline-block text-sm font-medium underline"
                      >
                        Открыть
                      </a>
                    ) : (
                      <p class="mt-3 text-sm text-muted">
                        Прямой переход недоступен.
                      </p>
                    )}
                  </article>
                );
              })}
            </div>
          ) : (
            <div class="mt-3 rounded-2xl border border-dashed border-border bg-bg p-4">
              <p class="text-sm font-medium text-text">
                Связанный контекст недоступен
              </p>
              <p class="mt-1 text-sm text-muted">
                Продолжите расследование через очередь или историю ролей.
              </p>
              <div class="mt-3 flex flex-wrap gap-3">
                {nextActions
                  .filter(
                    (action) =>
                      action.target === "queue-return" ||
                      action.target === "role-history",
                  )
                  .map((action) => {
                    const href = buildIncidentNextActionHref(
                      url,
                      action,
                      relatedLinks,
                      incident.environment,
                    );
                    return href ? (
                      <a
                        key={`${action.kind}-${action.target}-${action.label}`}
                        href={href}
                        class="text-sm font-medium underline"
                      >
                        {action.label}
                      </a>
                    ) : null;
                  })}
              </div>
            </div>
          )}
        </section>

        <details class="rounded-3xl border border-border bg-surface p-4 shadow-sm sm:p-5">
          <summary class="cursor-pointer text-base font-semibold text-text">
            Технические детали ({incident.affectedAttempts.length})
          </summary>
          {incident.affectedAttempts.length > 0 ? (
            <div class="mt-4 space-y-3">
              {incident.affectedAttempts.map((attempt) => (
                <article
                  key={`${attempt.correlationId ?? attempt.traceId ?? attempt.occurredAt}-${attempt.occurredAt}`}
                  class="rounded-2xl border border-border bg-bg p-4 text-xs text-muted"
                >
                  <p>Время: {attempt.occurredAt}</p>
                  <p>Пользователь: {attempt.subjectDisplay ?? "n/a"}</p>
                  <p>Роль: {attempt.role ?? "n/a"}</p>
                  <p class="break-all">Trace ID: {attempt.traceId ?? "n/a"}</p>
                  <p class="break-all">
                    Correlation ID: {attempt.correlationId ?? "n/a"}
                  </p>
                  <p class="break-all">Комната: {attempt.roomId ?? "n/a"}</p>
                  <p class="break-all">Встреча: {attempt.meetingId ?? "n/a"}</p>
                </article>
              ))}
            </div>
          ) : (
            <RequestStatePanel
              title="Нет технических деталей"
              detail="Попытки, попавшие в ограниченную выборку, отсутствуют."
            />
          )}
        </details>
      </div>
    );
  },
);
