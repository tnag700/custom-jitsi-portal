import { component$ } from "@qwik.dev/core";
import { RequestStatePanel } from "./RequestStatePanel";

interface ApiErrorAlertProps {
  message: string;
  errorCode?: string;
  traceId?: string;
  title?: string;
}

export const ApiErrorAlert = component$<ApiErrorAlertProps>(
  ({ message, errorCode, traceId, title = "Ошибка операции" }) => {
    return (
      <RequestStatePanel tone="error" title={title} detail={message}>
        {errorCode ? <p class="mt-2 text-xs text-muted">Код: {errorCode}</p> : null}
        {traceId ? <p class="mt-1 text-xs text-muted">Trace ID: {traceId}</p> : null}
      </RequestStatePanel>
    );
  },
);
