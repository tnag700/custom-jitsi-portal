import { component$, type QRL } from "@qwik.dev/core";
import { RetryEscalationActions } from "~/lib/shared";
import type { JoinErrorPayload } from "../types";

interface JoinErrorPanelProps {
  error: JoinErrorPayload;
  retryCount: number;
  maxRetries: number;
  onRetry$: QRL<() => void>;
  onCopyReport$: QRL<() => void>;
  reportCopied?: boolean;
}

export const JoinErrorPanel = component$<JoinErrorPanelProps>(
  ({ error, retryCount, maxRetries, onRetry$, onCopyReport$, reportCopied }) => {
    const canRetry = retryCount < maxRetries;

    return (
      <div class="rounded border border-red-300 bg-red-50 p-4 text-text dark:border-red-700 dark:bg-red-950">
        <h3 class="mb-1 text-base font-semibold text-red-800 dark:text-red-200">
          {error.title}
        </h3>
        <p class="mb-2 text-sm text-red-700 dark:text-red-300">{error.detail}</p>
        <p class="mb-3 text-xs text-muted">Код ошибки: {error.errorCode}</p>

        <RetryEscalationActions
          canRetry={canRetry}
          copied={reportCopied}
          onRetry$={onRetry$}
          onCopy$={onCopyReport$}
        />
      </div>
    );
  },
);
