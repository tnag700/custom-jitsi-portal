import { component$, useContext } from "@qwik.dev/core";
import { ThemeContext } from "../stores/theme-context";

export const ThemeToggle = component$(() => {
  const { theme, toggle$ } = useContext(ThemeContext);
  const isDark = theme.value === "dark";

  return (
    <button
      role="switch"
      aria-checked={isDark}
      aria-label="Переключить тему"
      onClick$={toggle$}
      class="cursor-pointer rounded-md border border-border bg-surface px-3 py-1.5 text-sm text-text transition-colors hover:bg-surface-2"
    >
      {isDark ? "☀️ Светлая" : "🌙 Тёмная"}
    </button>
  );
});
