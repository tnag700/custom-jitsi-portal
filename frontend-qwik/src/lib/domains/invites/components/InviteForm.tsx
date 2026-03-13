import { $, component$, useSignal, useTask$, type QRL } from "@qwik.dev/core";
import { Form } from "@qwik.dev/router";
import { ApiErrorAlert } from "~/lib/shared";
import type { InviteErrorPayload } from "../types";

interface InviteFormProps {
  meetingId: string;
  isLoading: boolean;
  error?: InviteErrorPayload;
  onCancel$: QRL<() => void>;
  action: unknown;
}

const ERROR_MESSAGES: Record<string, string> = {
  MEETING_FINALIZED: "Встреча финализирована",
  MEETING_NOT_FOUND: "Встреча не найдена",
  VALIDATION_ERROR: "Некорректные данные",
};

export const InviteForm = component$<InviteFormProps>(({ meetingId, isLoading, error, onCancel$, action }) => {
  const roleValue = useSignal<"participant" | "moderator">("participant");
  const maxUsesValue = useSignal("1");
  const expiresInHoursValue = useSignal("");
  const panelRef = useSignal<HTMLDivElement>();

  const errorMessage = error ? ERROR_MESSAGES[error.errorCode] ?? error.detail : null;

  const requestClose$ = $(() => {
    if (typeof window === "undefined") {
      void onCancel$();
      return;
    }

    window.requestAnimationFrame(() => {
      void onCancel$();
    });
  });

  const handleBackdropClick$ = $((event: MouseEvent, overlay: HTMLDivElement) => {
    if (event.target === overlay) {
      void requestClose$();
    }
  });

  const handleKeyDown$ = $((event: KeyboardEvent) => {
    if (event.key !== "Escape") {
      return;
    }

    event.preventDefault();
    void requestClose$();
  });

  useTask$(({ track }) => {
    track(() => panelRef.value);
    if (typeof window !== "undefined" && panelRef.value) {
      queueMicrotask(() => {
        panelRef.value?.focus();
      });
    }
  });

  return (
    <div
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm"
      role="presentation"
      onClick$={handleBackdropClick$}
      onKeyDown$={handleKeyDown$}
    >
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="invite-form-title"
        tabIndex={-1}
        class="w-full max-w-lg rounded border border-border bg-surface p-6"
      >
        <h2 class="mb-4 text-lg font-semibold text-text">Создать инвайт</h2>

        {errorMessage && (
          <div class="mb-4" role="alert">
            <ApiErrorAlert
              title="Ошибка операции с инвайтом"
              message={errorMessage}
              errorCode={error?.errorCode}
              traceId={error?.traceId}
            />
          </div>
        )}

        <Form action={action as never}>
          <input type="hidden" name="meetingId" value={meetingId} />

          <div class="space-y-4">
            <div>
              <label class="mb-1 block text-sm font-medium text-text" for="invite-role">Роль *</label>
              <select
                id="invite-role"
                name="role"
                value={roleValue.value}
                class="w-full rounded border border-border bg-bg px-3 py-2 text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                onChange$={(_, el) => {
                  roleValue.value = el.value as "participant" | "moderator";
                }}
              >
                <option value="participant">participant</option>
                <option value="moderator">moderator</option>
              </select>
            </div>

            <div>
              <label class="mb-1 block text-sm font-medium text-text" for="invite-max-uses">Макс. использований</label>
              <input
                id="invite-max-uses"
                type="number"
                name="maxUses"
                value={maxUsesValue.value}
                min={1}
                class="w-full rounded border border-border bg-bg px-3 py-2 text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                onInput$={(_, el) => {
                  maxUsesValue.value = el.value;
                }}
              />
            </div>

            <div>
              <label class="mb-1 block text-sm font-medium text-text" for="invite-expires">Истекает через (часы)</label>
              <input
                id="invite-expires"
                type="number"
                name="expiresInHours"
                value={expiresInHoursValue.value}
                min={1}
                max={168}
                placeholder="Бессрочный"
                class="w-full rounded border border-border bg-bg px-3 py-2 text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                onInput$={(_, el) => {
                  expiresInHoursValue.value = el.value;
                }}
              />
            </div>
          </div>

          <div class="mt-6 flex justify-end gap-3">
            <button
              type="button"
              class="rounded border border-border px-4 py-2 text-sm text-text hover:bg-bg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              onClick$={requestClose$}
              disabled={isLoading}
            >
              Отмена
            </button>
            <button
              type="submit"
              class="rounded bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 disabled:opacity-50"
              disabled={isLoading}
            >
              {isLoading ? "Создание..." : "Создать инвайт"}
            </button>
          </div>
        </Form>
      </div>
    </div>
  );
});
