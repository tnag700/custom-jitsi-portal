import { describe, expect, expectTypeOf, it, vi } from "vitest";
import {
  buildMeetingFormSubmission,
  type MeetingFormSubmissionPayload,
} from "~/lib/domains/meetings/components/meeting-form-state";
import type { MeetingForm } from "~/lib/domains/meetings/components/MeetingForm";
import type {
  useCreateMeeting,
  useUpdateMeeting,
} from "~/routes/meetings/meeting-actions";
import type { MeetingsOverview } from "~/routes/meetings/components/MeetingsOverview";

vi.mock("~/lib/shared", async () => {
  const actual = await import("~/lib/shared/utils/format-date");
  return {
    parseDateTimeLocalInput: actual.parseDateTimeLocalInput,
  };
});

describe("meeting form state", () => {
  it("keeps create and update route actions distinct through the overview", () => {
    type OverviewProps = Parameters<typeof MeetingsOverview>[0];
    expectTypeOf<ReturnType<typeof useUpdateMeeting>>().not.toExtend<
      OverviewProps["createAction"]
    >();
    expectTypeOf<ReturnType<typeof useCreateMeeting>>().not.toExtend<
      OverviewProps["updateAction"]
    >();
  });

  it("requires the corresponding resource identifier for programmatic submission", () => {
    type CreateAction = ReturnType<typeof useCreateMeeting>;
    type UpdateAction = ReturnType<typeof useUpdateMeeting>;
    type FormProps = Parameters<typeof MeetingForm>[0];
    type CreateFormProps = Extract<FormProps, { meeting?: undefined }>;
    type UpdateFormProps = Extract<FormProps, { meeting: object }>;

    expectTypeOf<MeetingFormSubmissionPayload>().not.toExtend<
      Parameters<CreateAction["submit"]>[0]
    >();
    expectTypeOf<MeetingFormSubmissionPayload>().not.toExtend<
      Parameters<UpdateAction["submit"]>[0]
    >();
    expectTypeOf<MeetingFormSubmissionPayload & { roomId: string }>().toExtend<
      Parameters<CreateAction["submit"]>[0]
    >();
    expectTypeOf<
      MeetingFormSubmissionPayload & { meetingId: string }
    >().toExtend<Parameters<UpdateAction["submit"]>[0]>();
    expectTypeOf<CreateAction["submit"]>().toExtend<
      CreateFormProps["onSubmit$"]
    >();
    expectTypeOf<UpdateAction["submit"]>().toExtend<
      UpdateFormProps["onSubmit$"]
    >();
    expectTypeOf<UpdateAction["submit"]>().not.toExtend<
      CreateFormProps["onSubmit$"]
    >();
    expectTypeOf<CreateAction["submit"]>().not.toExtend<
      UpdateFormProps["onSubmit$"]
    >();
  });

  it("returns all actionable field errors without submitting invalid values", () => {
    expect(
      buildMeetingFormSubmission({
        title: "",
        description: "",
        meetingType: "standard",
        startsAtLocal: "",
        endsAtLocal: "",
        allowGuests: true,
        recordingEnabled: false,
      }),
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
      buildMeetingFormSubmission({
        title: "Консилиум",
        description: "",
        meetingType: "standard",
        startsAtLocal: "2026-07-30T14:00",
        endsAtLocal: "2026-07-30T15:00",
        allowGuests: true,
        recordingEnabled: false,
      }),
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
      },
    });
  });

  it("rejects an inverted schedule before invoking the route action", () => {
    const result = buildMeetingFormSubmission({
      title: "Консилиум",
      description: "Рабочая встреча",
      meetingType: "workshop",
      startsAtLocal: "2026-07-30T15:00",
      endsAtLocal: "2026-07-30T14:00",
      allowGuests: false,
      recordingEnabled: true,
    });

    expect(result).toEqual({
      success: false,
      fieldErrors: {
        endsAt: "Время начала должно быть раньше времени окончания",
      },
    });
  });

  it("validates edit values without adding a creation room identifier", () => {
    expect(
      buildMeetingFormSubmission(
        {
          title: "Обновлённый консилиум",
          description: "Рабочая встреча",
          meetingType: "workshop",
          startsAtLocal: "2026-07-30T14:00",
          endsAtLocal: "2026-07-30T15:00",
          allowGuests: false,
          recordingEnabled: true,
        },
        true,
      ),
    ).toEqual({
      success: true,
      payload: {
        title: "Обновлённый консилиум",
        description: "Рабочая встреча",
        meetingType: "workshop",
        startsAt: "2026-07-30T11:00:00.000Z",
        endsAt: "2026-07-30T12:00:00.000Z",
        allowGuests: false,
        recordingEnabled: true,
      },
    });
  });
});
