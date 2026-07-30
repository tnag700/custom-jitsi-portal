import { component$ } from "@qwik.dev/core";
import { RequestStatePanel } from "~/lib/shared";
import type { AdminRoleHistory } from "../types";
import {
  describeAdminRoleTransition,
  formatAdminEnvironment,
  formatAdminRoleHistoryDateTime,
} from "../admin-role-history.presentation";

interface AdminRoleHistoryTimelineProps {
  history: AdminRoleHistory;
  previousHref: string | null;
  nextHref: string | null;
}

export const AdminRoleHistoryTimeline = component$(
  ({ history, previousHref, nextHref }: AdminRoleHistoryTimelineProps) => (
    <section
      class="rounded-3xl border border-border bg-surface p-5 shadow-sm md:p-6"
      aria-labelledby="role-history-events-title"
    >
      <div class="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p class="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
            Аудит назначений
          </p>
          <h2
            id="role-history-events-title"
            class="mt-1 text-xl font-semibold text-text"
          >
            Лента событий
          </h2>
          <p class="mt-1 text-sm text-muted">
            {history.environment === "all"
              ? "Все окружения"
              : `Окружение: ${formatAdminEnvironment(history.environment)}`}{" "}
            · найдено {String(history.totalElements)}
          </p>
        </div>
        <p class="text-sm text-muted">
          Страница {String(history.page + 1)} из{" "}
          {String(Math.max(history.totalPages, 1))}
        </p>
      </div>

      {history.content.length > 0 ? (
        <ol class="mt-6 space-y-4">
          {history.content.map((entry) => (
            <li
              key={`${entry.occurredAt}-${entry.traceId ?? entry.meetingId ?? entry.roomId ?? entry.subjectReference ?? entry.actionType}`}
              class="relative rounded-2xl border border-border bg-bg p-4 md:p-5"
            >
              <div class="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                <div class="min-w-0">
                  <span class="inline-flex rounded-full bg-primary-soft px-2.5 py-1 text-xs font-semibold text-primary">
                    {entry.actionLabel}
                  </span>
                  <h3 class="mt-3 text-base font-semibold text-text">
                    {entry.subjectLabel ?? "Субъект не указан"}
                  </h3>
                  <p class="mt-1 text-sm text-muted">
                    {describeAdminRoleTransition(entry.oldRole, entry.newRole)}
                  </p>
                </div>
                <time
                  class="shrink-0 text-sm text-muted"
                  dateTime={entry.occurredAt}
                >
                  {formatAdminRoleHistoryDateTime(entry.occurredAt)}
                </time>
              </div>

              <p class="mt-4 text-sm text-muted">
                Оператор:{" "}
                <span class="font-medium text-text">
                  {entry.actorLabel ?? "не указан"}
                </span>
              </p>

              <details class="mt-4 rounded-xl border border-border bg-surface/70">
                <summary class="cursor-pointer select-none px-3 py-2 text-sm font-medium text-text">
                  Технический контекст
                </summary>
                <dl class="grid gap-3 border-t border-border px-3 py-3 text-xs sm:grid-cols-2 xl:grid-cols-3">
                  {[
                    ["Субъект", entry.subjectReference],
                    ["Оператор", entry.actorReference],
                    ["Комната", entry.roomId],
                    ["Встреча", entry.meetingId],
                    ["Trace ID", entry.traceId],
                    ["Тенант", entry.tenantId],
                  ].map(([label, value]) => (
                    <div key={label}>
                      <dt class="font-medium text-muted">{label}</dt>
                      <dd class="mt-1 break-all text-text">
                        {value ?? "не указано"}
                      </dd>
                    </div>
                  ))}
                </dl>
              </details>
            </li>
          ))}
        </ol>
      ) : (
        <div class="mt-6">
          <RequestStatePanel
            title="История не найдена"
            detail="Измените основные фильтры или временное окно."
          />
        </div>
      )}

      {previousHref || nextHref ? (
        <nav
          class="mt-6 flex flex-wrap gap-3"
          aria-label="Страницы истории ролей"
        >
          {previousHref ? (
            <a
              href={previousHref}
              class="rounded-xl border border-border bg-surface px-4 py-2.5 text-sm font-medium text-text transition hover:bg-surface-2"
            >
              ← Предыдущая
            </a>
          ) : null}
          {nextHref ? (
            <a
              href={nextHref}
              class="rounded-xl border border-border bg-surface px-4 py-2.5 text-sm font-medium text-text transition hover:bg-surface-2"
            >
              Следующая →
            </a>
          ) : null}
        </nav>
      ) : null}
    </section>
  ),
);
