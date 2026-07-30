import { describe, expect, it, vi } from "vitest";
import { buildMeetingFormSubmission } from "~/lib/domains/meetings/components/meeting-form-state";

vi.mock("~/lib/shared", async () => {
  const actual = await import("~/lib/shared/utils/format-date");
  return {
    parseDateTimeLocalInput: actual.parseDateTimeLocalInput,
  };
});

describe("meeting form state", () => {
  it("returns all actionable field errors without submitting invalid values", () => {
    expect(
      buildMeetingFormSubmission(
        {
          title: "",
          description: "",
          meetingType: "standard",
          startsAtLocal: "",
          endsAtLocal: "",
          allowGuests: true,
          recordingEnabled: false,
        },
        {
          roomId: "room-1",
        },
      ),
    ).toEqual({
      success: false,
      fieldErrors: {
        title: "Название обязательно",
        startsAt: "Укажите корректную дату начала",
        endsAt: "Укажите корректную дату окончания",
      },
    });
  });

  it("builds a typed create payload with deterministic application time", () => {
    expect(
      buildMeetingFormSubmission(
        {
          title: "Консилиум",
          description: "",
          meetingType: "standard",
          startsAtLocal: "2026-07-30T14:00",
          endsAtLocal: "2026-07-30T15:00",
          allowGuests: true,
          recordingEnabled: false,
        },
        {
          roomId: "room-1",
        },
      ),
    ).toEqual({
      success: true,
      payload: {
        title: "Консилиум",
        description: undefined,
        meetingType: "standard",
        startsAt: "2026-07-30T11:00:00.000Z",
        endsAt: "2026-07-30T12:00:00.000Z",
        allowGuests: true,
        recordingEnabled: false,
        roomId: "room-1",
      },
    });
  });

  it("rejects an inverted schedule before invoking the route action", () => {
    const result = buildMeetingFormSubmission(
      {
        title: "Консилиум",
        description: "Рабочая встреча",
        meetingType: "workshop",
        startsAtLocal: "2026-07-30T15:00",
        endsAtLocal: "2026-07-30T14:00",
        allowGuests: false,
        recordingEnabled: true,
      },
      {
        roomId: "room-1",
      },
    );

    expect(result).toEqual({
      success: false,
      fieldErrors: {
        endsAt: "Время начала должно быть раньше времени окончания",
      },
    });
  });
});
