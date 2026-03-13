import { $, component$, useSignal, type QRL } from "@qwik.dev/core";

export type AppToastTone = "success" | "info" | "warning" | "error";

export interface AppToastState {
  message: string;
  tone?: AppToastTone;
  title?: string;
}

export interface AppToastProps {
  toast: AppToastState | null;
  onDismiss$?: QRL<() => void>;
}

export function useAppToast(defaultDuration = 3000) {
  const toast = useSignal<AppToastState | null>(null);
  const timeoutId = useSignal<number | null>(null);
  const fallbackDuration = defaultDuration;

  const clearToast$ = $(() => {
    if (typeof window !== "undefined" && timeoutId.value !== null) {
      window.clearTimeout(timeoutId.value);
    }

    timeoutId.value = null;
    toast.value = null;
  });

  const showToast$ = $((nextToast: AppToastState, duration?: number) => {
    const resolvedDuration = duration ?? fallbackDuration;

    if (typeof window !== "undefined" && timeoutId.value !== null) {
      window.clearTimeout(timeoutId.value);
    }

    toast.value = nextToast;

    if (typeof window !== "undefined" && resolvedDuration > 0) {
      timeoutId.value = window.setTimeout(() => {
        toast.value = null;
        timeoutId.value = null;
      }, resolvedDuration);
    }
  });

  return {
    toast,
    clearToast$,
    showToast$,
  };
}

export const AppToast = component$<AppToastProps>(({ toast, onDismiss$ }) => {
  if (!toast) {
    return null;
  }

  const tone = toast.tone ?? "success";
  const containerClass =
    tone === "success"
      ? "border-success/25 bg-success text-white"
      : tone === "error"
        ? "border-danger/25 bg-danger text-white"
        : tone === "warning"
          ? "border-warning/25 bg-warning text-text"
          : "border-info/25 bg-info text-white";

  return (
    <div class="pointer-events-none fixed bottom-4 right-4 z-50 max-w-sm">
      <div
        class={["pointer-events-auto rounded-xl border px-4 py-3 shadow-xl", containerClass]}
        role="status"
        aria-live={tone === "error" ? "assertive" : "polite"}
      >
        <div class="flex items-start gap-3">
          <div class="min-w-0 flex-1">
            {toast.title ? <p class="text-sm font-semibold">{toast.title}</p> : null}
            <p class="text-sm">{toast.message}</p>
          </div>

          {onDismiss$ ? (
            <button
              type="button"
              class="rounded-md border border-white/30 px-2 py-1 text-xs font-medium hover:bg-white/10"
              onClick$={onDismiss$}
              aria-label="Закрыть уведомление"
            >
              Закрыть
            </button>
          ) : null}
        </div>
      </div>
    </div>
  );
});