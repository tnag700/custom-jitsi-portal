/**
 * WHAT IS THIS FILE?
 *
 * SSR renderer function, used by Qwik Router.
 *
 * Note that this is the only place the Qwik renderer is called.
 * On the client, containers resume and do not call render.
 */
import { createRenderer } from "@qwik.dev/router";
import Root from "./root";

type Theme = "light" | "dark";

const THEME_COOKIE = "theme";

function parseCookieTheme(cookieHeader: string): Theme | null {
  const match = cookieHeader.match(/(?:^|;\s*)theme=(dark|light)(?:;|$)/i);
  return match?.[1]?.toLowerCase() === "dark"
    ? "dark"
    : match?.[1]?.toLowerCase() === "light"
      ? "light"
      : null;
}

function parseHintedTheme(prefersHeader: string): Theme | null {
  const normalized = prefersHeader.toLowerCase();
  if (normalized.includes("dark")) {
    return "dark";
  }
  if (normalized.includes("light")) {
    return "light";
  }
  return null;
}

function resolveRequestTheme(headers: Record<string, string>): Theme {
  const cookieTheme = parseCookieTheme(headers.cookie ?? "");
  const hintedTheme = parseHintedTheme(
    headers["sec-ch-prefers-color-scheme"] ?? "",
  );
  return cookieTheme ?? hintedTheme ?? "light";
}

function mergeClass(existingClass: string | undefined, theme: Theme): string | undefined {
  const classNames = new Set((existingClass ?? "").split(/\s+/).filter(Boolean));
  if (theme === "dark") {
    classNames.add("dark");
  } else {
    classNames.delete("dark");
  }
  return classNames.size > 0 ? Array.from(classNames).join(" ") : undefined;
}

export default createRenderer((opts) => {
  const theme = resolveRequestTheme(opts.serverData.requestHeaders);
  const mergedClass = mergeClass(opts.containerAttributes?.class, theme);

  return {
    jsx: <Root />,
    options: {
      ...opts,
      // Use container attributes to set attributes on the html tag.
      containerAttributes: {
        lang: "en-us",
        ...(opts.containerAttributes ?? {}),
        ...(mergedClass ? { class: mergedClass } : {}),
      },
      serverData: {
        ...opts.serverData,
        [THEME_COOKIE]: theme,
        // These are the default values for the document head and are overridden by the `head` exports
        // documentHead: {
        //   title: "My App",
        // },
      },
    },
  };
});
