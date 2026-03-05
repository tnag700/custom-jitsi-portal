import { component$, type JSXOutput, type QRL } from "@qwik.dev/core";
import { Combobox } from "@qwik-ui/headless";

type ShimComponent = (
  props: Record<string, unknown>,
  key: string | null,
  flags: number,
  dev?: unknown,
) => JSXOutput;

const ComboboxRoot = Combobox.Root as unknown as ShimComponent;
const ComboboxLabel = Combobox.Label as unknown as ShimComponent;
const ComboboxControl = Combobox.Control as unknown as ShimComponent;
const ComboboxInput = Combobox.Input as unknown as ShimComponent;
const ComboboxTrigger = Combobox.Trigger as unknown as ShimComponent;
const ComboboxPopover = Combobox.Popover as unknown as ShimComponent;
const ComboboxItem = Combobox.Item as unknown as ShimComponent;
const ComboboxItemLabel = Combobox.ItemLabel as unknown as ShimComponent;
const ComboboxItemIndicator = Combobox.ItemIndicator as unknown as ShimComponent;

export interface AppComboboxItem<TValue extends string = string> {
  value: TValue;
  label: string;
}

export interface AppComboboxProps<TValue extends string = string> {
  items: AppComboboxItem<TValue>[];
  value?: TValue;
  onChange$?: QRL<(value: TValue) => void>;
  placeholder?: string;
  disabled?: boolean;
  label?: string;
}

export const AppCombobox = component$(
  <TValue extends string = string,>({
    items,
    value,
    onChange$,
    placeholder = "Выберите значение",
    disabled = false,
    label,
  }: AppComboboxProps<TValue>) => {
    return (
      <ComboboxRoot
        value={value}
        filter
        disabled={disabled}
        onChange$={async (nextValue: unknown) => {
          if (onChange$ && typeof nextValue === "string") {
            await onChange$(nextValue as TValue);
          }
        }}
      >
        {label ? <ComboboxLabel class="text-sm font-medium text-text">{label}</ComboboxLabel> : null}

        <ComboboxControl class="mt-1 flex items-center border border-border rounded-md bg-surface">
          <ComboboxInput
            class="w-full bg-transparent px-3 py-2 text-text placeholder:text-muted"
            placeholder={placeholder}
          />
          <ComboboxTrigger class="px-3 py-2 text-muted" aria-label="Открыть список">
            <span aria-hidden="true">▾</span>
          </ComboboxTrigger>
        </ComboboxControl>

        <ComboboxPopover class="mt-1 max-h-60 overflow-y-auto rounded-md border border-border bg-surface shadow-2">
          {items.map((item) => (
            <ComboboxItem
              key={item.value}
              value={item.value}
              class="flex cursor-pointer items-center justify-between px-3 py-2 text-text data-[highlighted]:bg-primary-soft data-[selected]:font-medium"
            >
              <ComboboxItemLabel>{item.label}</ComboboxItemLabel>
              <ComboboxItemIndicator>✓</ComboboxItemIndicator>
            </ComboboxItem>
          ))}
        </ComboboxPopover>
      </ComboboxRoot>
    );
  },
);
