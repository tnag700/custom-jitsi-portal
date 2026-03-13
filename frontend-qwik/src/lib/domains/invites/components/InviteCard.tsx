import { component$, type QRL } from "@qwik.dev/core";
import { formatDateTime } from "~/lib/shared";
import type { Invite } from "../types";

interface InviteCardProps {
  invite: Invite;
  onRevoke$: QRL<(invite: Invite) => void>;
  onCopyLink$: QRL<(invite: Invite) => void>;
}

export const InviteCard = component$<InviteCardProps>(({ invite, onRevoke$, onCopyLink$ }) => {
  const roleClass =
    invite.role === "moderator"
      ? "bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200"
      : "bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200";

  const statusClass = invite.revokedAt
    ? "bg-rose-100 text-rose-800 dark:bg-rose-950 dark:text-rose-200"
    : invite.valid
      ? "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200"
      : "bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-200";

  const statusLabel = invite.revokedAt ? "Удалён" : invite.valid ? "Активен" : "Недействителен";

  return (
    <article class="rounded border border-border bg-surface p-4 text-text">
      <div class="mb-2 flex items-start justify-between gap-2">
        <span class={["inline-block rounded-full px-2 py-0.5 text-xs font-medium", roleClass]}>
          {invite.role}
        </span>
        <span class={["inline-block rounded-full px-2 py-0.5 text-xs font-medium", statusClass]}>
          {statusLabel}
        </span>
      </div>

      <div class="mb-2 text-sm text-muted">{invite.usedCount}/{invite.maxUses} использований</div>

      <div class="mb-1 text-xs text-muted">
        Истекает: {invite.expiresAt ? formatDateTime(invite.expiresAt) : "Бессрочный"}
      </div>
      <div class="mb-1 text-xs text-muted">Создан: {formatDateTime(invite.createdAt)}</div>
      {invite.revokedAt && <div class="mb-1 text-xs text-muted">Удалён: {formatDateTime(invite.revokedAt)}</div>}
      <div class="mb-3 text-xs text-muted">Кем: {invite.createdBy}</div>

      {!invite.revokedAt ? (
        <div class="flex flex-wrap gap-2">
          <button
            type="button"
            class="rounded border border-border px-3 py-1 text-sm hover:bg-bg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
            aria-label="Копировать ссылку инвайта"
            onClick$={() => onCopyLink$(invite)}
          >
            Копировать ссылку
          </button>
          {invite.valid && (
            <button
              type="button"
              class="rounded border border-amber-300 px-3 py-1 text-sm text-amber-700 hover:bg-amber-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500 dark:border-amber-700 dark:text-amber-300 dark:hover:bg-amber-950"
              aria-label="Удалить ссылку инвайта"
              onClick$={() => onRevoke$(invite)}
            >
              Удалить
            </button>
          )}
        </div>
      ) : (
        <p class="text-xs text-muted">Ссылка сохранена в истории и скрывается из рабочего списка активных инвайтов.</p>
      )}
    </article>
  );
});
