import { component$, Slot, type QRL } from "@qwik.dev/core";

interface RequestStatePanelProps {
  title: string;
  detail: string;
  tone?: "error" | "info";
  actionLabel?: string;
  onAction$?: QRL<() => void>;
}

export const RequestStatePanel = component$<RequestStatePanelProps>(
  ({ title, detail, tone = "info", actionLabel, onAction$ }) => {
    const palette =
      tone === "error"
        ? {
            root: "border-red-300 bg-red-50 dark:border-red-700 dark:bg-red-950",
            title: "text-red-800 dark:text-red-200",
            detail: "text-red-700 dark:text-red-300",
          }
        : {
            root: "border-border bg-surface",
            title: "text-text",
            detail: "text-muted",
          };

    return (
      <section class={["rounded border p-4", palette.root]}>
        <h3 class={["mb-1 text-base font-semibold", palette.title]}>{title}</h3>
        <p class={["text-sm", palette.detail]}>{detail}</p>

        {actionLabel && onAction$ ? (
          <button
            type="button"
            class="mt-3 rounded border border-border px-3 py-1.5 text-sm text-text hover:bg-bg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
            onClick$={() => onAction$()}
          >
            {actionLabel}
          </button>
        ) : null}

        <Slot />
      </section>
    );
  },
);
