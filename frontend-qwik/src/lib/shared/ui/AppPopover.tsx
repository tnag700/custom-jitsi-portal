import { component$, Slot, type PropsOf } from "@qwik.dev/core";
import { Popover } from "@qwik-ui/headless";
import { popoverA11yDefaults, sharedFocusVisibleAttrs } from "./a11y";
import { asHeadlessComponent } from "./headless-typing";

type Placement = Exclude<PropsOf<typeof Popover.Root>["floating"], boolean | undefined>;

const PopoverRoot = asHeadlessComponent(Popover.Root);
const PopoverTrigger = asHeadlessComponent(Popover.Trigger);
const PopoverPanel = asHeadlessComponent(Popover.Panel);

interface PopoverRootProps {
  [key: string]: unknown;
}

export interface AppPopoverProps extends PopoverRootProps {
  id: string;
  floating?: Placement;
  gutter?: number;
  triggerLabel?: string;
}

export const AppPopover = component$<AppPopoverProps>(
  ({ floating = "bottom", gutter = 8, id, triggerLabel = popoverA11yDefaults.triggerLabel, ...rootProps }) => {
    return (
      <PopoverRoot {...rootProps} id={id} floating={floating} gutter={gutter}>
        <PopoverTrigger {...sharedFocusVisibleAttrs} class="inline-flex" aria-label={triggerLabel}>
          <Slot name="trigger" />
        </PopoverTrigger>
        <PopoverPanel class="rounded-xl border border-border bg-surface p-4 shadow-2">
          <Slot />
        </PopoverPanel>
      </PopoverRoot>
    );
  },
);
