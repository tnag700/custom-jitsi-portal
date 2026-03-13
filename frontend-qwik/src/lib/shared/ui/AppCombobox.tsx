import { component$, type QRL } from "@qwik.dev/core";
import { Combobox } from "@qwik-ui/headless";
import { comboboxA11yDefaults, sharedFocusVisibleAttrs } from "./a11y";
import { asHeadlessComponent } from "./headless-typing";

const ComboboxRoot = asHeadlessComponent(Combobox.Root);
const ComboboxLabel = asHeadlessComponent(Combobox.Label);
const ComboboxControl = asHeadlessComponent(Combobox.Control);
const ComboboxInput = asHeadlessComponent(Combobox.Input);
const ComboboxTrigger = asHeadlessComponent(Combobox.Trigger);
const ComboboxPopover = asHeadlessComponent(Combobox.Popover);
const ComboboxItem = asHeadlessComponent(Combobox.Item);
const ComboboxItemLabel = asHeadlessComponent(Combobox.ItemLabel);
const ComboboxItemIndicator = asHeadlessComponent(Combobox.ItemIndicator);

export interface AppComboboxItem<TValue extends string = string> {
  value: TValue;
  label: string;
}

interface ComboboxRootProps {
  name?: string;
  loop?: boolean;
  [key: string]: unknown;
}

export interface AppComboboxProps<TValue extends string = string> extends ComboboxRootProps {
  items: AppComboboxItem<TValue>[];
  value?: TValue;
  onChange$?: QRL<(value: TValue) => void>;
  placeholder?: string;
  disabled?: boolean;
  label?: string;
  triggerLabel?: string;
}

export async function forwardComboboxValue<TValue extends string = string>(
  nextValue: unknown,
  onChange$?: QRL<(value: TValue) => void>,
): Promise<void> {
  if (onChange$ && typeof nextValue === "string") {
    await onChange$(nextValue as TValue);
  }
}

export const AppCombobox = component$(
  <TValue extends string = string,>({
    items,
    value,
    onChange$,
    placeholder = "Выберите значение",
    disabled = false,
    label,
    triggerLabel = comboboxA11yDefaults.triggerLabel,
    ...rootProps
  }: AppComboboxProps<TValue>) => {
    return (
      <ComboboxRoot
        {...rootProps}
        value={value}
        filter
        disabled={disabled}
        onChange$={async (nextValue: unknown) => forwardComboboxValue(nextValue, onChange$)}
      >
        {label ? <ComboboxLabel class="text-sm font-medium text-text">{label}</ComboboxLabel> : null}

        <ComboboxControl class="mt-1 flex items-center border border-border rounded-md bg-surface">
          <ComboboxInput
            {...sharedFocusVisibleAttrs}
            class="w-full bg-transparent px-3 py-2 text-text placeholder:text-muted"
            placeholder={placeholder}
          />
          <ComboboxTrigger {...sharedFocusVisibleAttrs} class="px-3 py-2 text-muted" aria-label={triggerLabel}>
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
