import { describe, expect, it, vi } from "vitest";
import { copyTextWithFallback } from "~/lib/shared/browser/copy-text";

describe("copyTextWithFallback", () => {
  it("reports success only after the Clipboard API resolves", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    const prompt = vi.fn();

    await expect(
      copyTextWithFallback("https://portal.example/invite/token", {
        clipboard: { writeText },
        prompt,
      }),
    ).resolves.toBe("copied");

    expect(writeText).toHaveBeenCalledOnce();
    expect(writeText).toHaveBeenCalledWith(
      "https://portal.example/invite/token",
    );
    expect(prompt).not.toHaveBeenCalled();
  });

  it("opens a selectable manual fallback when Clipboard API is denied", async () => {
    const writeText = vi.fn().mockRejectedValue(new Error("denied"));
    const prompt = vi
      .fn()
      .mockReturnValue("https://portal.example/invite/token");

    await expect(
      copyTextWithFallback("https://portal.example/invite/token", {
        clipboard: { writeText },
        prompt,
      }),
    ).resolves.toBe("manual");

    expect(prompt).toHaveBeenCalledWith(
      "Автоматическое копирование недоступно. Скопируйте ссылку вручную и передавайте её только приглашённому гостю:",
      "https://portal.example/invite/token",
    );
  });

  it("reports cancellation when no Clipboard API exists and fallback is closed", async () => {
    const prompt = vi.fn().mockReturnValue(null);

    await expect(
      copyTextWithFallback("https://portal.example/invite/token", {
        clipboard: undefined,
        prompt,
      }),
    ).resolves.toBe("cancelled");
  });
});
