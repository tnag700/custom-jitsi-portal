import { describe, expect, it } from "vitest";
import {
  APPLICATION_TIME_ZONE,
  formatDate,
  formatDateTime,
  formatDateTimeLocalInput,
  parseDateTimeLocalInput,
} from "~/lib/shared/utils/format-date";

describe("application date formatting", () => {
  it("uses one deterministic application timezone for SSR and browser output", () => {
    expect(APPLICATION_TIME_ZONE).toBe("Europe/Minsk");
    expect(formatDateTime("2026-07-30T07:00:00Z")).toBe("30.07.2026, 10:00");
    expect(formatDate("2026-07-29T22:30:00Z")).toContain("30 июля 2026");
  });

  it("round-trips datetime-local values through backend UTC", () => {
    const localValue = formatDateTimeLocalInput("2026-07-30T07:00:00Z");

    expect(localValue).toBe("2026-07-30T10:00");
    expect(parseDateTimeLocalInput(localValue)).toBe(
      "2026-07-30T07:00:00.000Z",
    );
  });

  it("rejects malformed and impossible local values", () => {
    expect(formatDateTimeLocalInput("invalid")).toBe("");
    expect(parseDateTimeLocalInput("2026-02-30T10:00")).toBe("");
    expect(parseDateTimeLocalInput("not-a-date")).toBe("");
  });
});
