import { describe, expect, it } from "vitest";
import type { Meeting, MeetingErrorPayload } from "~/lib/domains/meetings";
import {
  buildMeetingsHref,
  findMeetingById,
  getActionError,
  getActionValidationFeedback,
  getFirstActionError,
  isSuccessfulAction,
} from "~/routes/meetings/meetings-page-state";

function createMeeting(overrides: Partial<Meeting> = {}): Meeting {
  return {
    meetingId: "meeting-1",
    roomId: "room-1",
    title: "Консилиум",
    description: null,
    meetingType: "standard",
    configSetId: "config-1",
    status: "scheduled",
    startsAt: "2026-07-29T10:00:00Z",
    endsAt: "2026-07-29T11:00:00Z",
    allowGuests: true,
    recordingEnabled: false,
    createdAt: "2026-07-29T09:00:00Z",
    updatedAt: "2026-07-29T09:00:00Z",
    ...overrides,
  };
}

function createError(errorCode: string): MeetingErrorPayload {
  return {
    title: "Ошибка",
    detail: `detail:${errorCode}`,
    errorCode,
    traceId: `trace:${errorCode}`,
  };
}

describe("meetings page state", () => {
  it("finds only a meeting present in the current room payload", () => {
    const meetings = [
      createMeeting(),
      createMeeting({ meetingId: "meeting-2", title: "Повторный приём" }),
    ];

    expect(findMeetingById(meetings, "meeting-2")?.title).toBe(
      "Повторный приём",
    );
    expect(findMeetingById(meetings, "missing")).toBeNull();
    expect(findMeetingById(meetings, "")).toBeNull();
  });

  it("extracts typed action errors and preserves priority", () => {
    const bulkError = createError("BULK_FAILED");
    const roleError = createError("ROLE_FAILED");

    expect(getActionError<MeetingErrorPayload>(undefined)).toBeUndefined();
    expect(
      getActionError<MeetingErrorPayload>({ success: true }),
    ).toBeUndefined();
    expect(getActionError<MeetingErrorPayload>({ error: bulkError })).toBe(
      bulkError,
    );
    expect(
      getFirstActionError<MeetingErrorPayload>(
        { success: true },
        { error: bulkError },
        { error: roleError },
      ),
    ).toBe(bulkError);
  });

  it("recognizes only explicit successful action results", () => {
    expect(isSuccessfulAction({ success: true })).toBe(true);
    expect(isSuccessfulAction({ success: false })).toBe(false);
    expect(isSuccessfulAction({ error: createError("FAILED") })).toBe(false);
    expect(isSuccessfulAction(null)).toBe(false);
  });

  it("normalizes route action validation failures for presentation", () => {
    expect(
      getActionValidationFeedback({
        failed: true,
        fieldErrors: {
          title: "Название обязательно",
          startsAt: ["Укажите дату", "Дата некорректна"],
        },
        formErrors: ["Проверьте параметры встречи"],
      }),
    ).toEqual({
      fieldErrors: {
        title: "Название обязательно",
        startsAt: "Укажите дату",
      },
      formErrors: ["Проверьте параметры встречи"],
    });
    expect(getActionValidationFeedback({ success: true })).toBeUndefined();
  });

  it("builds encoded room, participant, and invite URLs", () => {
    expect(buildMeetingsHref()).toBe("/meetings");
    expect(buildMeetingsHref("room a/b")).toBe("/meetings?roomId=room%20a%2Fb");
    expect(buildMeetingsHref("room-1", { meetingId: "meeting a/b" })).toBe(
      "/meetings?roomId=room-1&meetingId=meeting%20a%2Fb",
    );
    expect(
      buildMeetingsHref("room-1", { invitesMeetingId: "invite-meeting" }),
    ).toBe("/meetings?roomId=room-1&invitesMeetingId=invite-meeting");
  });
});
