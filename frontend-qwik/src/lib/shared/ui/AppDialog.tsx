import { $, component$, Slot, type Signal } from "@qwik.dev/core";
import { dialogA11yDefaults, sharedFocusVisibleAttrs } from "./a11y";

interface ModalRootProps {
  "bind:show"?: Signal<boolean>;
  closeOnBackdropClick?: boolean;
  [key: string]: unknown;
}

export interface AppDialogProps extends ModalRootProps {
  title: string;
  description?: string;
  maxWidth?: string;
  showTrigger?: boolean;
  showFooter?: boolean;
  showDefaultCloseAction?: boolean;
  triggerLabel?: string;
  closeLabel?: string;
}

export const AppDialog = component$<AppDialogProps>(
  ({
    title,
    description,
    maxWidth = "max-w-md",
    showTrigger = true,
    showFooter = true,
    showDefaultCloseAction = true,
    triggerLabel = dialogA11yDefaults.triggerLabel,
    closeLabel = dialogA11yDefaults.closeLabel,
    "bind:show": show,
    closeOnBackdropClick = true,
    ...rootProps
  }) => {
    const open$ = $(() => {
      if (show) {
        show.value = true;
      }
    });

    const closeOnNextFrame$ = $(() => {
      if (!show) {
        return;
      }

      if (typeof window === "undefined") {
        show.value = false;
        return;
      }

      window.requestAnimationFrame(() => {
        show.value = false;
      });
    });

    const close$ = $(() => {
      void closeOnNextFrame$();
    });

    const handleBackdropClick$ = $((event: MouseEvent, overlay: HTMLDivElement) => {
      if (!show || closeOnBackdropClick === false) {
        return;
      }

      if (event.target === overlay) {
        void closeOnNextFrame$();
      }
    });

    const handleKeyDown$ = $((event: KeyboardEvent) => {
      if (!show || event.key !== "Escape") {
        return;
      }

      event.preventDefault();
      void closeOnNextFrame$();
    });

    return (
      <div {...rootProps}>
        {showTrigger ? (
          <button
            type="button"
            {...sharedFocusVisibleAttrs}
            class="inline-flex"
            aria-label={triggerLabel}
            onClick$={open$}
          >
            <Slot name="trigger" />
          </button>
        ) : null}

        {show?.value ? (
          <div
            class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm"
            role="presentation"
            onClick$={handleBackdropClick$}
            onKeyDown$={handleKeyDown$}
          >
            <div
              role="dialog"
              aria-modal="true"
              aria-labelledby="app-dialog-title"
              aria-describedby={description ? "app-dialog-description" : undefined}
              tabIndex={-1}
              class={`w-full ${maxWidth} rounded-xl bg-surface p-6 text-text shadow-xl`}
            >
              <div class="space-y-1">
                <h2 id="app-dialog-title" class="text-lg font-bold text-text">{title}</h2>
                {description ? (
                  <p id="app-dialog-description" class="text-sm text-muted">{description}</p>
                ) : null}
              </div>

              <div class="mt-4">
                <Slot />
              </div>

              {showFooter ? (
                <div class="mt-6 flex items-center justify-end gap-2">
                  <Slot name="actions" />
                  {showDefaultCloseAction ? (
                    <button
                      type="button"
                      {...sharedFocusVisibleAttrs}
                      class="rounded-md border border-border px-3 py-2 text-sm text-text hover:bg-surface-2"
                      onClick$={close$}
                    >
                      {closeLabel}
                    </button>
                  ) : null}
                </div>
              ) : null}
            </div>
          </div>
        ) : null}
      </div>
    );
  },
);
