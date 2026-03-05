import { component$, type QRL } from "@qwik.dev/core";
import type { Invite } from "../types";
import { InviteCard } from "./InviteCard";

interface InviteListProps {
  invites: Invite[];
  totalElements: number;
  onRevoke$: QRL<(invite: Invite) => void>;
  onCopyLink$: QRL<(invite: Invite) => void>;
  onCreateClick$: QRL<() => void>;
}

export const InviteList = component$<InviteListProps>(({ invites, totalElements, onRevoke$, onCopyLink$, onCreateClick$ }) => {
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

      {invites.length === 0 ? (
        <div class="rounded border border-dashed border-border p-8 text-center">
          <h3 class="mb-2 text-lg font-semibold text-text">Нет инвайтов</h3>
          <p class="text-sm text-muted">Создайте первый инвайт для приглашения участников</p>
        </div>
      ) : (
        <>
          <div class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
            {invites.map((invite) => (
              <InviteCard key={invite.id} invite={invite} onRevoke$={onRevoke$} onCopyLink$={onCopyLink$} />
            ))}
          </div>

          <p class="mt-4 text-sm text-muted">Показано {invites.length} из {totalElements} инвайтов</p>
        </>
      )}
    </section>
  );
});
