import {
  $,
  component$,
  useSignal,
  useTask$,
  type QRL,
  type Signal,
} from "@qwik.dev/core";
import {
  ApiErrorAlert,
  AppDialog,
  formatDateTimeLocalInput,
} from "~/lib/shared";
import type { Meeting, MeetingErrorPayload } from "../types";
import { getMeetingTypeOptions } from "../meeting-type-presentation";
import {
  buildMeetingFormSubmission,
  type MeetingFormSubmissionPayload,
} from "./meeting-form-state";

export interface MeetingFormAction {
  submit: QRL<
    (payload: MeetingFormSubmissionPayload) => Promise<unknown>
  >;
}

interface MeetingFormProps {
  meeting?: Meeting;
  roomId: string;
  isLoading: boolean;
  error?: MeetingErrorPayload;
  validationFeedback?: {
    fieldErrors: Record<string, string>;
    formErrors: string[];
  };
  isOpen: Signal<boolean>;
  action: MeetingFormAction;
}

function toDateTimeLocal(isoValue: string | undefined): string {
  return formatDateTimeLocalInput(isoValue);
}

const ERROR_MESSAGES: Record<string, string> = {
  INVALID_SCHEDULE: "Некорректное расписание",
  ROOM_INACTIVE: "Комната недоступна",
  MEETING_FINALIZED: "Встреча финализирована",
  VALIDATION_ERROR: "Некорректные данные",
};

export const MeetingForm = component$<MeetingFormProps>(
  ({
    meeting,
    roomId,
    isLoading,
    error,
    validationFeedback,
    isOpen,
    action,
  }) => {
    const isEdit = !!meeting;
    const formId = isEdit ? "meeting-edit-form" : "meeting-create-form";
    const titleValue = useSignal(meeting?.title ?? "");
    const descriptionValue = useSignal(meeting?.description ?? "");
    const meetingTypeValue = useSignal(meeting?.meetingType ?? "standard");
    const startsAtLocal = useSignal(toDateTimeLocal(meeting?.startsAt));
    const endsAtLocal = useSignal(toDateTimeLocal(meeting?.endsAt));
    const allowGuestsValue = useSignal(meeting?.allowGuests ?? true);
    const recordingEnabledValue = useSignal(meeting?.recordingEnabled ?? false);
    const validationErrors = useSignal<Record<string, string>>({});
    const submissionErrors = useSignal<string[]>([]);
    const submitAction = action.submit;

    const clearFieldError$ = $((field: string) => {
      const nextErrors = { ...validationErrors.value };
      delete nextErrors[field];
      validationErrors.value = nextErrors;
      submissionErrors.value = [];
    });

    const handleSubmit$ = $(async () => {
      const submission = buildMeetingFormSubmission(
        {
          title: titleValue.value,
          description: descriptionValue.value,
          meetingType: meetingTypeValue.value,
          startsAtLocal: startsAtLocal.value,
          endsAtLocal: endsAtLocal.value,
          allowGuests: allowGuestsValue.value,
          recordingEnabled: recordingEnabledValue.value,
        },
        {
          roomId,
          meetingId: meeting?.meetingId,
        },
      );

      if (!submission.success) {
        validationErrors.value = submission.fieldErrors;
        submissionErrors.value = ["Проверьте выделенные поля"];
        return;
      }

      validationErrors.value = {};
      submissionErrors.value = [];
      await submitAction(submission.payload);
    });

    const meetingTypeOptions = getMeetingTypeOptions(meetingTypeValue.value);

    const errorMessage = error
      ? ERROR_MESSAGES[error.errorCode] ?? error.detail
      : null;

    useTask$(({ track }) => {
      const feedback = track(() => validationFeedback);
      if (!feedback) {
        return;
      }

      validationErrors.value = {
        ...validationErrors.value,
        ...feedback.fieldErrors,
      };
      submissionErrors.value = feedback.formErrors.length
        ? feedback.formErrors
        : ["Проверьте выделенные поля"];
    });

    return (
      <AppDialog
        title={isEdit ? "Редактировать встречу" : "Создать встречу"}
        description={
          isEdit
            ? "Обновите параметры встречи и сохраните изменения."
            : "Заполните расписание и параметры новой встречи."
        }
        maxWidth="max-w-2xl"
        showTrigger={false}
        closeOnBackdropClick={false}
        closeLabel="Отмена"
        bind:show={isOpen}
      >
        {submissionErrors.value.length > 0 && (
          <div
            class="mb-4 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-100"
            role="alert"
            aria-live="polite"
          >
            {submissionErrors.value.join(". ")}
          </div>
        )}

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

        <form
          id={formId}
          preventdefault:submit
          onSubmit$={handleSubmit$}
        >
          <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div class="md:col-span-2">
              <label
                for="meeting-title"
                class="mb-1 block text-sm font-medium text-text"
              >
                Название *
              </label>
              <input
                id="meeting-title"
                type="text"
                name="title"
                value={titleValue.value}
                aria-invalid={validationErrors.value.title ? "true" : undefined}
                aria-describedby={
                  validationErrors.value.title
                    ? "meeting-title-error"
                    : undefined
                }
                class="w-full rounded border border-border bg-bg px-3 py-2 text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                onInput$={(_, element) => {
                  titleValue.value = element.value;
                  void clearFieldError$("title");
                }}
              />
              {validationErrors.value.title && (
                <p
                  id="meeting-title-error"
                  class="mt-1 text-xs text-red-600"
                >
                  {validationErrors.value.title}
                </p>
              )}
            </div>

            <div class="md:col-span-2">
              <label
                for="meeting-description"
                class="mb-1 block text-sm font-medium text-text"
              >
                Описание
              </label>
              <textarea
                id="meeting-description"
                rows={3}
                name="description"
                value={descriptionValue.value}
                aria-invalid={
                  validationErrors.value.description ? "true" : undefined
                }
                class="w-full rounded border border-border bg-bg px-3 py-2 text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                onInput$={(_, element) => {
                  descriptionValue.value = element.value;
                  void clearFieldError$("description");
                }}
              />
              {validationErrors.value.description && (
                <p class="mt-1 text-xs text-red-600">
                  {validationErrors.value.description}
                </p>
              )}
            </div>

            <fieldset class="md:col-span-2">
              <legend class="mb-2 text-sm font-medium text-text">
                Формат встречи *
              </legend>
              <div class="grid gap-2 sm:grid-cols-3">
                {meetingTypeOptions.map((option) => {
                  const isSelected = meetingTypeValue.value === option.value;
                  return (
                    <label
                      key={option.value}
                      class={[
                        "flex cursor-pointer gap-2 rounded-lg border p-3 transition-colors focus-within:ring-2 focus-within:ring-blue-500",
                        isSelected
                          ? "border-blue-500 bg-blue-50 dark:bg-blue-950"
                          : "border-border bg-bg hover:border-blue-300",
                      ]}
                    >
                      <input
                        type="radio"
                        name="meetingType"
                        value={option.value}
                        checked={isSelected}
                        class="mt-1"
                        onChange$={() => {
                          meetingTypeValue.value = option.value;
                          void clearFieldError$("meetingType");
                        }}
                      />
                      <span>
                        <span class="block text-sm font-medium text-text">
                          {option.label}
                        </span>
                        <span class="mt-1 block text-xs leading-5 text-muted">
                          {option.description}
                        </span>
                      </span>
                    </label>
                  );
                })}
              </div>
              {validationErrors.value.meetingType && (
                <p class="mt-1 text-xs text-red-600">
                  {validationErrors.value.meetingType}
                </p>
              )}
            </fieldset>

            <div>
              <label
                for="meeting-starts-at"
                class="mb-1 block text-sm font-medium text-text"
              >
                Начало *
              </label>
              <input
                id="meeting-starts-at"
                type="datetime-local"
                value={startsAtLocal.value}
                aria-invalid={
                  validationErrors.value.startsAt ? "true" : undefined
                }
                aria-describedby={
                  validationErrors.value.startsAt
                    ? "meeting-starts-at-error"
                    : undefined
                }
                class="w-full rounded border border-border bg-bg px-3 py-2 text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                onInput$={(_, element) => {
                  startsAtLocal.value = element.value;
                  void clearFieldError$("startsAt");
                }}
              />
              {validationErrors.value.startsAt && (
                <p
                  id="meeting-starts-at-error"
                  class="mt-1 text-xs text-red-600"
                >
                  {validationErrors.value.startsAt}
                </p>
              )}
            </div>

            <div>
              <label
                for="meeting-ends-at"
                class="mb-1 block text-sm font-medium text-text"
              >
                Окончание *
              </label>
              <input
                id="meeting-ends-at"
                type="datetime-local"
                value={endsAtLocal.value}
                aria-invalid={
                  validationErrors.value.endsAt ? "true" : undefined
                }
                aria-describedby={
                  validationErrors.value.endsAt
                    ? "meeting-ends-at-error"
                    : undefined
                }
                class="w-full rounded border border-border bg-bg px-3 py-2 text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                onInput$={(_, element) => {
                  endsAtLocal.value = element.value;
                  void clearFieldError$("endsAt");
                }}
              />
              {validationErrors.value.endsAt && (
                <p
                  id="meeting-ends-at-error"
                  class="mt-1 text-xs text-red-600"
                >
                  {validationErrors.value.endsAt}
                </p>
              )}
            </div>

            <div class="flex items-center gap-2">
              <input
                id="meeting-allow-guests"
                type="checkbox"
                checked={allowGuestsValue.value}
                onChange$={(_, element) => {
                  allowGuestsValue.value = element.checked;
                }}
              />
              <label for="meeting-allow-guests" class="text-sm text-text">
                Разрешить гостей
              </label>
            </div>

            <div class="flex items-center gap-2">
              <input
                id="meeting-recording-enabled"
                type="checkbox"
                checked={recordingEnabledValue.value}
                onChange$={(_, element) => {
                  recordingEnabledValue.value = element.checked;
                }}
              />
              <label for="meeting-recording-enabled" class="text-sm text-text">
                Запись встречи
              </label>
            </div>
          </div>
        </form>

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
