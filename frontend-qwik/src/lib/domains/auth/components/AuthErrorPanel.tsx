import { component$ } from "@qwik.dev/core";
import { Link } from "@qwik.dev/router";
import { RequestStatePanel } from "~/lib/shared";
import type { AuthErrorPayload } from "../types";

export interface AuthErrorPanelProps {
  error: AuthErrorPayload;
}

export const AuthErrorPanel = component$<AuthErrorPanelProps>(({ error }) => {
  return (
    <section role="alert" aria-live="polite">
      <RequestStatePanel title={error.title} detail={error.reason}>
      <p class="mt-3 text-sm">{error.actions}</p>
      <p class="mt-3 text-xs text-muted">Код ошибки: {error.errorCode}</p>

      <div class="mt-4">
        <Link
          href="/auth"
          class="inline-flex items-center rounded-md border border-border px-3 py-1.5 text-sm font-medium text-text transition-colors hover:bg-surface-2 focus-visible:outline focus-visible:outline-3 focus-visible:outline-primary focus-visible:outline-offset-2"
        >
          Попробовать снова
        </Link>
      </div>
      </RequestStatePanel>
    </section>
  );
});
