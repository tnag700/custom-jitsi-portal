import { component$, Slot, type JSXOutput, type PropsOf } from "@qwik.dev/core";
import { Popover } from "@qwik-ui/headless";

type Placement = Exclude<PropsOf<typeof Popover.Root>["floating"], boolean | undefined>;

type ShimComponent = (
  props: Record<string, unknown>,
  key: string | null,
  flags: number,
  dev?: unknown,
) => JSXOutput;

const PopoverRoot = Popover.Root as unknown as ShimComponent;
const PopoverTrigger = Popover.Trigger as unknown as ShimComponent;
const PopoverPanel = Popover.Panel as unknown as ShimComponent;

export interface AppPopoverProps {
  floating?: Placement;
  gutter?: number;
  id: string;
}

export const AppPopover = component$<AppPopoverProps>(
  ({ floating = "bottom", gutter = 8, id }) => {
    return (
      <PopoverRoot id={id} floating={floating} gutter={gutter}>
        <PopoverTrigger class="inline-flex">
          <Slot name="trigger" />
        </PopoverTrigger>
        <PopoverPanel class="rounded-xl border border-border bg-surface p-4 shadow-2">
          <Slot />
        </PopoverPanel>
      </PopoverRoot>
    );
  },
);
