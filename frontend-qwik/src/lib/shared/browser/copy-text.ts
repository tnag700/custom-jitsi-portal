export type CopyTextOutcome = "copied" | "manual" | "cancelled";

interface ClipboardWriter {
  writeText(value: string): Promise<void>;
}

export interface CopyTextEnvironment {
  clipboard?: ClipboardWriter;
  prompt(message: string, defaultValue?: string): string | null;
}

function resolveBrowserEnvironment(): CopyTextEnvironment {
  return {
    clipboard:
      typeof navigator === "undefined" ? undefined : navigator.clipboard,
    prompt:
      typeof window === "undefined"
        ? () => null
        : window.prompt.bind(window),
  };
}

export async function copyTextWithFallback(
  value: string,
  environment = resolveBrowserEnvironment(),
): Promise<CopyTextOutcome> {
  if (environment.clipboard) {
    try {
      await environment.clipboard.writeText(value);
      return "copied";
    } catch {
      // Continue with an explicit, user-controlled fallback.
    }
  }

  try {
    const result = environment.prompt(
      "Автоматическое копирование недоступно. Скопируйте ссылку вручную и передавайте её только приглашённому гостю:",
      value,
    );
    return result === null ? "cancelled" : "manual";
  } catch {
    return "cancelled";
  }
}
