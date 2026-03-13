import { $, component$, useSignal, useTask$, type Signal } from "@qwik.dev/core";
import { Form } from "@qwik.dev/router";
import { ApiErrorAlert, AppDialog } from "~/lib/shared";
import type { Room, RoomErrorPayload } from "../types";
import { createRoomSchema, updateRoomSchema } from "../rooms.zod";

interface RoomFormProps {
  action: unknown;
  room?: Room;
  configSets: string[];
  isLoading: boolean;
  error?: RoomErrorPayload;
  isOpen: Signal<boolean>;
}

const ERROR_MESSAGES: Record<string, string> = {
  ROOM_NAME_CONFLICT: "Имя комнаты занято",
  CONFIG_SET_INVALID: "Неверная конфигурация",
  VALIDATION_ERROR: "Некорректные данные",
  ROOM_NOT_FOUND: "Комната не найдена",
};

export const RoomForm = component$<RoomFormProps>(
  ({ action, room, configSets, isLoading, error, isOpen }) => {
    const isEdit = !!room;
    const formId = isEdit ? "room-edit-form" : "room-create-form";
    const nameValue = useSignal(room?.name ?? "");
    const descriptionValue = useSignal(room?.description ?? "");
    const configSetIdValue = useSignal(room?.configSetId ?? (configSets[0] ?? ""));
    const validationErrors = useSignal<Record<string, string>>({});

    const handleSubmit$ = $((event: Event) => {
      const data = {
        name: nameValue.value,
        description: descriptionValue.value || undefined,
        configSetId: configSetIdValue.value,
      };

      const schema = isEdit ? updateRoomSchema : createRoomSchema;
      const result = schema.safeParse(data);

      if (!result.success) {
        const errors: Record<string, string> = {};
        for (const issue of result.error.issues) {
          const key = issue.path[0];
          if (typeof key === "string") {
            errors[key] = issue.message;
          }
        }
        validationErrors.value = errors;
        event.preventDefault();
        return;
      }

      validationErrors.value = {};
    });

    const errorMessage = error
      ? ERROR_MESSAGES[error.errorCode] ?? error.detail
      : null;

    useTask$(({ track }) => {
      const open = track(() => isOpen.value);
      const roomId = track(() => room?.roomId ?? null);

      if (!open) {
        return;
      }

      nameValue.value = room?.name ?? "";
      descriptionValue.value = room?.description ?? "";
      configSetIdValue.value = room?.configSetId ?? (configSets[0] ?? "");
      validationErrors.value = {};
      void roomId;
    });

    return (
      <AppDialog
        title={isEdit ? "Редактировать комнату" : "Создать комнату"}
        description={isEdit ? "Измените свойства комнаты и сохраните обновления." : "Заполните основные поля для новой комнаты."}
        maxWidth="max-w-md"
        showTrigger={false}
        closeOnBackdropClick={false}
        closeLabel="Отмена"
        bind:show={isOpen}
      >

          {errorMessage && (
            <div class="mb-4" role="alert" aria-live="polite">
              <ApiErrorAlert
                title="Ошибка операции с комнатой"
                message={errorMessage}
                errorCode={error?.errorCode}
                traceId={error?.traceId}
              />
            </div>
          )}

          <Form id={formId} action={action as never} onSubmit$={handleSubmit$}>
            <div class="space-y-4">
            <div>
              <label class="mb-1 block text-sm font-medium text-text" for="room-name">
                Название *
              </label>
              <input
                id="room-name"
                name="name"
                type="text"
                class="w-full rounded border border-border bg-bg px-3 py-2 text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                value={nameValue.value}
                onInput$={(_, el) => {
                  nameValue.value = el.value;
                }}
              />
              {validationErrors.value.name && (
                <p class="mt-1 text-xs text-red-600">{validationErrors.value.name}</p>
              )}
            </div>

            <div>
              <label class="mb-1 block text-sm font-medium text-text" for="room-description">
                Описание
              </label>
              <textarea
                id="room-description"
                name="description"
                class="w-full rounded border border-border bg-bg px-3 py-2 text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                rows={3}
                value={descriptionValue.value}
                onInput$={(_, el) => {
                  descriptionValue.value = el.value;
                }}
              />
              {validationErrors.value.description && (
                <p class="mt-1 text-xs text-red-600">
                  {validationErrors.value.description}
                </p>
              )}
            </div>

            <div>
              <label class="mb-1 block text-sm font-medium text-text" for="room-config-set">
                Конфигурация *
              </label>
              <select
                id="room-config-set"
                name="configSetId"
                class="w-full rounded border border-border bg-bg px-3 py-2 text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                value={configSetIdValue.value}
                onChange$={(_, el) => {
                  configSetIdValue.value = el.value;
                }}
              >
                {configSets.map((cs) => (
                  <option key={cs} value={cs}>
                    {cs}
                  </option>
                ))}
              </select>
              {validationErrors.value.configSetId && (
                <p class="mt-1 text-xs text-red-600">
                  {validationErrors.value.configSetId}
                </p>
              )}
            </div>
            </div>

            {room?.roomId && <input type="hidden" name="roomId" value={room.roomId} />}

          </Form>

          <button
            q:slot="actions"
            form={formId}
            type="submit"
            class="rounded bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 disabled:opacity-50"
            disabled={isLoading}
          >
            {isLoading ? "Сохранение..." : isEdit ? "Сохранить" : "Создать"}
          </button>
      </AppDialog>
    );
  },
);
