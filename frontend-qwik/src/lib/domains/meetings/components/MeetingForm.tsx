import { $, component$, useSignal, useComputed$, type QRL } from "@qwik.dev/core";
import { Form } from "@qwik.dev/router";
import { ApiErrorAlert } from "~/lib/shared";
import type { Meeting, MeetingErrorPayload } from "../types";
import { createMeetingSchema, updateMeetingSchema } from "../meetings.zod";

interface MeetingFormProps {
  meeting?: Meeting;
  roomId: string;
  isLoading: boolean;
  error?: MeetingErrorPayload;
  onCancel$: QRL<() => void>;
  action: unknown;
}

function toDateTimeLocal(isoValue: string | undefined): string {
  if (!isoValue) return "";
  const date = new Date(isoValue);
  if (Number.isNaN(date.getTime())) return "";
  const timezoneOffsetInMs = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - timezoneOffsetInMs).toISOString().slice(0, 16);
}

function toIso(dateTimeLocal: string): string {
  if (!dateTimeLocal) return "";
  const parsed = new Date(dateTimeLocal);
  if (Number.isNaN(parsed.getTime())) return "";
  return parsed.toISOString();
}

const ERROR_MESSAGES: Record<string, string> = {
  INVALID_SCHEDULE: "Некорректное расписание",
  ROOM_INACTIVE: "Комната недоступна",
  MEETING_FINALIZED: "Встреча финализирована",
  VALIDATION_ERROR: "Некорректные данные",
};

export const MeetingForm = component$<MeetingFormProps>(
  ({ meeting, roomId, isLoading, error, onCancel$, action }) => {
    const isEdit = !!meeting;
    const titleValue = useSignal(meeting?.title ?? "");
    const descriptionValue = useSignal(meeting?.description ?? "");
    const meetingTypeValue = useSignal(meeting?.meetingType ?? "standard");
    const startsAtLocal = useSignal(toDateTimeLocal(meeting?.startsAt));
    const endsAtLocal = useSignal(toDateTimeLocal(meeting?.endsAt));
    const allowGuestsValue = useSignal(meeting?.allowGuests ?? true);
    const recordingEnabledValue = useSignal(meeting?.recordingEnabled ?? false);
    const validationErrors = useSignal<Record<string, string>>({});

    const startsAtIso = useComputed$(() => toIso(startsAtLocal.value));
    const endsAtIso = useComputed$(() => toIso(endsAtLocal.value));

    const handleSubmit$ = $((event: Event) => {
      const startsAt = startsAtIso.value;
      const endsAt = endsAtIso.value;
      
      const nextValidationErrors: Record<string, string> = {};
      let hasError = false;

      if (!startsAt) {
        nextValidationErrors.startsAt = "Укажите корректную дату начала";
        hasError = true;
      }
      if (!endsAt) {
        nextValidationErrors.endsAt = "Укажите корректную дату окончания";
        hasError = true;
      }

      if (startsAt && endsAt && new Date(startsAt).getTime() >= new Date(endsAt).getTime()) {
        nextValidationErrors.endsAt = "Время начала должно быть раньше времени окончания";
        hasError = true;
      }

      if (hasError) {
        validationErrors.value = nextValidationErrors;
        event.preventDefault();
        return;
      }

      const payload = {
        title: titleValue.value,
        description: descriptionValue.value || undefined,
        meetingType: meetingTypeValue.value,
        startsAt,
        endsAt,
        allowGuests: allowGuestsValue.value ? "true" : "false",
        recordingEnabled: recordingEnabledValue.value ? "true" : "false",
      };

      const schema = isEdit ? updateMeetingSchema : createMeetingSchema;
      const result = schema.safeParse(payload);
      if (!result.success) {
        const nextErrors: Record<string, string> = {};
        for (const issue of result.error.issues) {
          const key = issue.path[0];
          if (typeof key === "string") {
            nextErrors[key] = issue.message;
          }
        }
        validationErrors.value = nextErrors;
        event.preventDefault();
        return;
      }

      validationErrors.value = {};
    });

    const errorMessage = error ? ERROR_MESSAGES[error.errorCode] ?? error.detail : null;

    return (
      <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
        <div class="w-full max-w-xl rounded border border-border bg-surface p-6">
          <h2 class="mb-4 text-lg font-semibold text-text">
            {isEdit ? "Редактировать встречу" : "Создать встречу"}
          </h2>

          {errorMessage && (
            <div class="mb-4" role="alert">
              <ApiErrorAlert
                title="Ошибка операции со встречей"
                message={errorMessage}
                errorCode={error?.errorCode}
                traceId={error?.traceId}
              />
            </div>
          )}

          <Form action={action as never} onSubmit$={handleSubmit$}>
            <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div class="md:col-span-2">
                <label for="meeting-title" class="mb-1 block text-sm font-medium text-text">Название *</label>
                <input
                  id="meeting-title"
                  type="text"
                  name="title"
                  value={titleValue.value}
                  class="w-full rounded border border-border bg-bg px-3 py-2 text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                  onInput$={(_, el) => {
                    titleValue.value = el.value;
                  }}
                />
                {validationErrors.value.title && <p class="mt-1 text-xs text-red-600">{validationErrors.value.title}</p>}
              </div>

              <div class="md:col-span-2">
                <label for="meeting-description" class="mb-1 block text-sm font-medium text-text">Описание</label>
                <textarea
                  id="meeting-description"
                  rows={3}
                  name="description"
                  value={descriptionValue.value}
                  class="w-full rounded border border-border bg-bg px-3 py-2 text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                  onInput$={(_, el) => {
                    descriptionValue.value = el.value;
                  }}
                />
                {validationErrors.value.description && <p class="mt-1 text-xs text-red-600">{validationErrors.value.description}</p>}
              </div>

              <div>
                <label for="meeting-type" class="mb-1 block text-sm font-medium text-text">Тип *</label>
                <select
                  id="meeting-type"
                  name="meetingType"
                  value={meetingTypeValue.value}
                  class="w-full rounded border border-border bg-bg px-3 py-2 text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                  onChange$={(_, el) => {
                    meetingTypeValue.value = el.value;
                  }}
                >
                  <option value="standard">standard</option>
                  <option value="webinar">webinar</option>
                  <option value="workshop">workshop</option>
                </select>
                {validationErrors.value.meetingType && <p class="mt-1 text-xs text-red-600">{validationErrors.value.meetingType}</p>}
              </div>

              <div>
                <label for="meeting-starts-at" class="mb-1 block text-sm font-medium text-text">Начало *</label>
                <input
                  id="meeting-starts-at"
                  type="datetime-local"
                  value={startsAtLocal.value}
                  class="w-full rounded border border-border bg-bg px-3 py-2 text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                  onInput$={(_, el) => {
                    startsAtLocal.value = el.value;
                  }}
                />
                {validationErrors.value.startsAt && <p class="mt-1 text-xs text-red-600">{validationErrors.value.startsAt}</p>}
              </div>

              <div>
                <label for="meeting-ends-at" class="mb-1 block text-sm font-medium text-text">Окончание *</label>
                <input
                  id="meeting-ends-at"
                  type="datetime-local"
                  value={endsAtLocal.value}
                  class="w-full rounded border border-border bg-bg px-3 py-2 text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                  onInput$={(_, el) => {
                    endsAtLocal.value = el.value;
                  }}
                />
                {validationErrors.value.endsAt && <p class="mt-1 text-xs text-red-600">{validationErrors.value.endsAt}</p>}
              </div>

              <div class="flex items-center gap-2">
                <input
                  id="meeting-allow-guests"
                  type="checkbox"
                  checked={allowGuestsValue.value}
                  onChange$={(_, el) => {
                    allowGuestsValue.value = el.checked;
                  }}
                />
                <label for="meeting-allow-guests" class="text-sm text-text">Разрешить гостей</label>
              </div>

              <div class="flex items-center gap-2">
                <input
                  id="meeting-recording-enabled"
                  type="checkbox"
                  checked={recordingEnabledValue.value}
                  onChange$={(_, el) => {
                    recordingEnabledValue.value = el.checked;
                  }}
                />
                <label for="meeting-recording-enabled" class="text-sm text-text">Запись встречи</label>
              </div>
            </div>

            <input type="hidden" name="startsAt" value={startsAtIso.value} />
            <input type="hidden" name="endsAt" value={endsAtIso.value} />
            <input type="hidden" name="allowGuests" value={allowGuestsValue.value ? "true" : "false"} />
            <input type="hidden" name="recordingEnabled" value={recordingEnabledValue.value ? "true" : "false"} />
            {!isEdit && <input type="hidden" name="roomId" value={roomId} />}
            {meeting?.meetingId && <input type="hidden" name="meetingId" value={meeting.meetingId} />}

            <div class="mt-6 flex justify-end gap-3">
              <button
                type="button"
                class="rounded border border-border px-4 py-2 text-sm text-text hover:bg-bg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                onClick$={() => onCancel$()}
                disabled={isLoading}
              >
                Отмена
              </button>
              <button
                type="submit"
                class="rounded bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 disabled:opacity-50"
                disabled={isLoading}
              >
                {isLoading ? "Сохранение..." : isEdit ? "Сохранить" : "Создать"}
              </button>
            </div>
          </Form>
        </div>
      </div>
    );
  },
);
