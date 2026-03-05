import { createContextId, type Signal, type QRL } from "@qwik.dev/core";

export type Theme = "light" | "dark";

export interface ThemeStore {
  theme: Signal<Theme>;
  /** Toggle between light and dark. Updates cookie + <html> class + signal. */
  toggle$: QRL<() => void>;
}

export const ThemeContext = createContextId<ThemeStore>("theme-context");
