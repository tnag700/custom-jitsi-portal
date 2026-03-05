import { component$, type QRL } from "@qwik.dev/core";

interface RetryEscalationActionsProps {
  canRetry: boolean;
  retryLabel?: string;
  escalationText?: string;
  copyLabel?: string;
  copiedLabel?: string;
  copied?: boolean;
  onRetry$?: QRL<() => void>;
  onCopy$?: QRL<() => void>;
}

export const RetryEscalationActions = component$<RetryEscalationActionsProps>(
  ({
    canRetry,
    retryLabel = "Повторить",
    escalationText = "Обратитесь в поддержку",
    copyLabel = "Скопировать отчёт",
    copiedLabel = "Скопировано ✓",
    copied,
    onRetry$,
    onCopy$,
  }) => {
    return (
      <div class="flex flex-wrap gap-2">
        {canRetry ? (
          <button
            type="button"
            class="rounded bg-blue-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
            onClick$={() => onRetry$?.()}
          >
            {retryLabel}
          </button>
        ) : (
          <>
            <p class="w-full text-sm font-medium text-red-800 dark:text-red-200">
              {escalationText}
            </p>
            <button
              type="button"
              class="rounded border border-border px-3 py-1.5 text-sm hover:bg-bg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              onClick$={() => onCopy$?.()}
            >
              {copied ? copiedLabel : copyLabel}
            </button>
          </>
        )}
      </div>
    );
  },
);
