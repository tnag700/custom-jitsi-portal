/**
 * Shared accessibility defaults for the thin shared/ui wrapper layer.
 *
 * Focus-visible styling stays centralized in src/global.css; wrappers should reuse that global.css
 * contract instead of introducing their own local focus ring implementation.
 */
export const sharedFocusVisibleContract =
  "Use the global :focus-visible styles from src/global.css; wrappers do not redefine local focus rings.";

export const sharedFocusVisibleAttrs = {
  "data-focus-visible-contract": "global-css",
} as const;

export const dialogA11yDefaults = {
  triggerLabel: "Открыть диалог",
  closeLabel: "Закрыть",
} as const;

export const popoverA11yDefaults = {
  triggerLabel: "Открыть popover",
} as const;

export const comboboxA11yDefaults = {
  triggerLabel: "Открыть список",
} as const;