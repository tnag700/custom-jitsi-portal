import { component$, useSignal, useComputed$, type QRL } from "@qwik.dev/core";
import type { Room } from "../types";
import { RoomCard } from "./RoomCard";

interface RoomListProps {
  rooms: Room[];
  totalElements: number;
  onEdit$: QRL<(room: Room) => void>;
  onClose$: QRL<(room: Room) => void>;
  onDelete$: QRL<(room: Room) => void>;
  onCreateClick$: QRL<() => void>;
}

export const RoomList = component$<RoomListProps>(
  ({ rooms, totalElements, onEdit$, onClose$, onDelete$, onCreateClick$ }) => {
    const statusFilter = useSignal<"all" | "active" | "closed">("all");
    const sortBy = useSignal<"name" | "createdAt">("name");

    const filteredRooms = useComputed$(() => {
      const filtered =
        statusFilter.value === "all"
          ? rooms
          : rooms.filter((r) => r.status === statusFilter.value);

      return [...filtered].sort((a, b) => {
        if (sortBy.value === "name") {
          return a.name.localeCompare(b.name, "ru");
        }
        return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
      });
    });

    return (
      <div>
        <div class="mb-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <h1 class="text-2xl font-bold text-text">Комнаты для встреч</h1>
          <button
            type="button"
            class="w-full rounded bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary sm:w-auto"
            onClick$={() => onCreateClick$()}
          >
            Создать комнату
          </button>
        </div>

        <div class="mb-4 grid gap-3 sm:grid-cols-2">
          <div class="grid gap-1.5">
            <label class="text-sm text-muted" for="status-filter">
              Статус:
            </label>
            <select
              id="status-filter"
              class="w-full rounded border border-border bg-bg px-3 py-2 text-sm text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              value={statusFilter.value}
              onChange$={(_, el) => {
                statusFilter.value = el.value as "all" | "active" | "closed";
              }}
            >
              <option value="all">Все</option>
              <option value="active">Активные</option>
              <option value="closed">Закрытые</option>
            </select>
          </div>

          <div class="grid gap-1.5">
            <label class="text-sm text-muted" for="sort-by">
              Сортировка:
            </label>
            <select
              id="sort-by"
              class="w-full rounded border border-border bg-bg px-3 py-2 text-sm text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              value={sortBy.value}
              onChange$={(_, el) => {
                sortBy.value = el.value as "name" | "createdAt";
              }}
            >
              <option value="name">По имени</option>
              <option value="createdAt">По дате создания</option>
            </select>
          </div>
        </div>

        {rooms.length === 0 ? (
          <div class="flex flex-col items-center justify-center py-16 text-center">
            <svg
              class="mb-4 h-16 w-16 text-muted"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="1.5"
                d="M2.25 12l8.954-8.955a1.126 1.126 0 011.591 0L21.75 12M4.5 9.75v10.125c0 .621.504 1.125 1.125 1.125H9.75v-4.875c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21h4.125c.621 0 1.125-.504 1.125-1.125V9.75M8.25 21h8.25"
              />
            </svg>
            <h2 class="mb-2 text-lg font-semibold text-text">
              Нет созданных комнат
            </h2>
            <p class="mb-4 text-sm text-muted">Создайте первую комнату</p>
            <button
              type="button"
              class="rounded bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              onClick$={() => onCreateClick$()}
            >
              Создать комнату
            </button>
          </div>
        ) : (
          <>
            <div class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
              {filteredRooms.value.map((room) => (
                <RoomCard
                  key={room.roomId}
                  room={room}
                  onEdit$={onEdit$}
                  onClose$={onClose$}
                  onDelete$={onDelete$}
                />
              ))}
            </div>

            {totalElements > rooms.length && (
              <p class="mt-4 text-center text-sm text-muted">
                Показано {rooms.length} из {totalElements} комнат
              </p>
            )}
          </>
        )}
      </div>
    );
  },
);
