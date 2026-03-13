import { component$, useComputed$, useSignal, type QRL } from "@qwik.dev/core";
import { formatDateTime } from "~/lib/shared";
import type { Invite } from "../types";
import { InviteCard } from "./InviteCard";
import {
  applyInviteListState,
  summarizeInviteList,
  type InviteVisibilityFilter,
} from "./invite-list-state";

interface InviteListProps {
  invites: Invite[];
  totalElements: number;
  onRevoke$: QRL<(invite: Invite) => void>;
  onCopyLink$: QRL<(invite: Invite) => void>;
  onCreateClick$: QRL<() => void>;
}

export const InviteList = component$<InviteListProps>(({ invites, totalElements, onRevoke$, onCopyLink$, onCreateClick$ }) => {
  const visibilityFilter = useSignal<InviteVisibilityFilter>("active");

  const filteredInvites = useComputed$(() => applyInviteListState(invites, visibilityFilter.value));
  const summary = useComputed$(() => summarizeInviteList(invites));

  const hasSourceInvites = invites.length > 0;
  const hasFilteredInvites = filteredInvites.value.length > 0;

  return (
    <section>
      <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
        <h2 class="text-2xl font-bold text-text">Инвайты</h2>
        <button
          type="button"
          class="rounded bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
          onClick$={() => onCreateClick$()}
        >
          Создать инвайт
        </button>
      </div>

      <div class="mb-4 flex flex-wrap items-center justify-between gap-3 rounded border border-border bg-bg/60 p-3">
        <div class="flex flex-wrap items-center gap-3">
          <div class="flex items-center gap-2">
            <label class="text-sm text-muted" for="invite-visibility-filter">Показать:</label>
            <select
              id="invite-visibility-filter"
              class="rounded border border-border bg-surface px-2 py-1 text-sm text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              value={visibilityFilter.value}
              onChange$={(_, el) => {
                visibilityFilter.value = el.value as InviteVisibilityFilter;
              }}
            >
              <option value="active">Активные</option>
              <option value="all">Все</option>
              <option value="deleted">Удалённые</option>
            </select>
          </div>

          <span class="inline-flex items-center rounded-full border border-amber-200 bg-amber-50 px-3 py-1 text-xs font-medium text-amber-800 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-200">
            Удалено: {summary.value.deletedCount}
          </span>
        </div>

        <p class="text-xs text-muted">
          {summary.value.lastDeletedAt
            ? ["Последнее удаление: ", <span key="last-deleted-at" class="font-medium text-text">{formatDateTime(summary.value.lastDeletedAt)}</span>]
            : "Удалённых ссылок пока нет"}
        </p>
      </div>

      {!hasSourceInvites ? (
        <div class="rounded border border-dashed border-border p-8 text-center">
          <h3 class="mb-2 text-lg font-semibold text-text">Нет инвайтов</h3>
          <p class="text-sm text-muted">Создайте первый инвайт для приглашения участников</p>
        </div>
      ) : !hasFilteredInvites ? (
        <div class="rounded border border-dashed border-border p-8 text-center">
          <h3 class="mb-2 text-lg font-semibold text-text">
            {visibilityFilter.value === "deleted" ? "Удалённых инвайтов нет" : "Нет инвайтов по выбранному фильтру"}
          </h3>
          <p class="mb-4 text-sm text-muted">
            {visibilityFilter.value === "active"
              ? "Активные ссылки скрывают удалённую историю. Переключите фильтр, если нужно поднять архив удалений."
              : visibilityFilter.value === "deleted"
                ? "История мягкого удаления пока пуста."
                : "Смените фильтр, чтобы вернуться к рабочему списку активных ссылок."}
          </p>
          <button
            type="button"
            class="rounded border border-border px-4 py-2 text-sm text-text hover:bg-bg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
            onClick$={() => {
              visibilityFilter.value = visibilityFilter.value === "deleted" ? "active" : "all";
            }}
          >
            {visibilityFilter.value === "deleted" ? "Показать активные" : "Показать все ссылки"}
          </button>
        </div>
      ) : (
        <>
          <div class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
            {filteredInvites.value.map((invite) => (
              <InviteCard key={invite.id} invite={invite} onRevoke$={onRevoke$} onCopyLink$={onCopyLink$} />
            ))}
          </div>

          <p class="mt-4 text-sm text-muted">
            Показано {filteredInvites.value.length} из {totalElements} ссылок
            {visibilityFilter.value === "active" ? `, активных: ${summary.value.activeCount}` : ""}
          </p>
        </>
      )}
    </section>
  );
});
