import { component$, Slot, type JSXOutput, type Signal } from "@qwik.dev/core";
import { Modal } from "@qwik-ui/headless";

type ShimComponent = (
  props: Record<string, unknown>,
  key: string | null,
  flags: number,
  dev?: unknown,
) => JSXOutput;

const ModalRoot = Modal.Root as unknown as ShimComponent;
const ModalTrigger = Modal.Trigger as unknown as ShimComponent;
const ModalPanel = Modal.Panel as unknown as ShimComponent;
const ModalTitle = Modal.Title as unknown as ShimComponent;
const ModalDescription = Modal.Description as unknown as ShimComponent;
const ModalClose = Modal.Close as unknown as ShimComponent;

export interface AppDialogProps {
  title: string;
  description?: string;
  maxWidth?: string;
  showTrigger?: boolean;
  triggerLabel?: string;
  closeLabel?: string;
  "bind:show": Signal<boolean>;
}

export const AppDialog = component$<AppDialogProps>(
  ({
    title,
    description,
    maxWidth = "max-w-md",
    showTrigger = true,
    triggerLabel = "Открыть диалог",
    closeLabel = "Закрыть",
    ...props
  }) => {
    return (
      <ModalRoot bind:show={props["bind:show"]}>
        {showTrigger ? (
          <ModalTrigger class="inline-flex" aria-label={triggerLabel}>
            <Slot name="trigger" />
          </ModalTrigger>
        ) : null}

        <ModalPanel
          class={`mx-auto w-full ${maxWidth} rounded-xl bg-surface p-6 text-text shadow-xl backdrop:bg-black/50 backdrop:backdrop-blur-sm`}
        >
          <div class="space-y-1">
            <ModalTitle class="text-lg font-bold text-text">{title}</ModalTitle>
            {description ? (
              <ModalDescription class="text-sm text-muted">{description}</ModalDescription>
            ) : null}
          </div>

          <div class="mt-4">
            <Slot />
          </div>

          <div class="mt-6 flex items-center justify-end gap-2">
            <Slot name="actions" />
            <ModalClose class="rounded-md border border-border px-3 py-2 text-sm text-text hover:bg-surface-2">
              {closeLabel}
            </ModalClose>
          </div>
        </ModalPanel>
      </ModalRoot>
    );
  },
);
