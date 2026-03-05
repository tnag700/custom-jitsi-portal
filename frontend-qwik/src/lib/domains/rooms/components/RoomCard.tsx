import { component$, type QRL } from "@qwik.dev/core";
import type { Room } from "../types";
import { formatDate } from "~/lib/shared";

interface RoomCardProps {
  room: Room;
  onEdit$: QRL<(room: Room) => void>;
  onClose$: QRL<(room: Room) => void>;
  onDelete$: QRL<(room: Room) => void>;
}

export const RoomCard = component$<RoomCardProps>(
  ({ room, onEdit$, onClose$, onDelete$ }) => {
    const isActive = room.status === "active";

    return (
      <div class="rounded border border-border bg-surface p-4">
        <div class="mb-2 flex items-start justify-between">
          <h3 class="text-lg font-semibold text-text">{room.name}</h3>
          <span
            class={[
              "inline-block rounded-full px-2 py-0.5 text-xs font-medium",
              isActive
                ? "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200"
                : "bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300",
            ]}
          >
            {isActive ? "Активна" : "Закрыта"}
          </span>
        </div>

        {room.description && (
          <p class="mb-2 line-clamp-2 text-sm text-muted">
            {room.description}
          </p>
        )}

        <div class="mb-3 space-y-1 text-xs text-muted">
          <p>Конфигурация: {room.configSetId}</p>
          <p>Создана: {formatDate(room.createdAt)}</p>
        </div>

        <div class="flex flex-wrap gap-2">
          <button
            type="button"
            class="w-full rounded border border-border px-3 py-1 text-sm text-text hover:bg-bg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 sm:w-auto"
            aria-label={`Редактировать комнату ${room.name}`}
            onClick$={() => onEdit$(room)}
          >
            Редактировать
          </button>

          {isActive && (
            <button
              type="button"
              class="w-full rounded border border-border px-3 py-1 text-sm text-muted hover:bg-bg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 sm:w-auto"
              aria-label={`Закрыть комнату ${room.name}`}
              onClick$={() => onClose$(room)}
            >
              Закрыть
            </button>
          )}

          <button
            type="button"
            class="w-full rounded border border-red-300 px-3 py-1 text-sm text-red-600 hover:bg-red-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500 dark:border-red-700 dark:text-red-400 dark:hover:bg-red-950 sm:w-auto"
            aria-label={`Удалить комнату ${room.name}`}
            onClick$={() => onDelete$(room)}
          >
            Удалить
          </button>
        </div>
      </div>
    );
  },
);
