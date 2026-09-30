import { parseDateTimeLocalInput } from "~/lib/shared";
import { createMeetingSchema, updateMeetingSchema } from "../meetings.zod";
import type { CreateMeetingRequest } from "../types";

export interface MeetingFormValues {
  title: string;
  description: string;
  meetingType: string;
  startsAtLocal: string;
  endsAtLocal: string;
  allowGuests: boolean;
  recordingEnabled: boolean;
}

export type MeetingFormSubmissionPayload = CreateMeetingRequest & {
  allowGuests: boolean;
  recordingEnabled: boolean;
};

type MeetingFormSubmission =
  | {
      success: true;
      payload: MeetingFormSubmissionPayload;
    }
  | {
      success: false;
      fieldErrors: Record<string, string>;
    };

export function buildMeetingFormSubmission(
  values: MeetingFormValues,
  isEdit = false,
): MeetingFormSubmission {
  const startsAt = parseDateTimeLocalInput(values.startsAtLocal);
  const endsAt = parseDateTimeLocalInput(values.endsAtLocal);
  const fieldErrors: Record<string, string> = {};

  if (!startsAt) {
    fieldErrors.startsAt = "Укажите корректную дату начала";
  }
  if (!endsAt) {
    fieldErrors.endsAt = "Укажите корректную дату окончания";
  }

  const candidate = {
    title: values.title,
    description: values.description || undefined,
    meetingType: values.meetingType,
    startsAt,
    endsAt,
    allowGuests: values.allowGuests,
    recordingEnabled: values.recordingEnabled,
  };
  const schema = isEdit ? updateMeetingSchema : createMeetingSchema;
  const result = schema.safeParse(candidate);

  if (!result.success) {
    for (const issue of result.error.issues) {
      const key = issue.path[0];
      if (typeof key === "string" && !fieldErrors[key]) {
        fieldErrors[key] = issue.message;
      }
    }
  }

  if (Object.keys(fieldErrors).length > 0) {
    return {
      success: false,
      fieldErrors,
    };
  }

  return {
    success: true,
    payload: candidate,
  };
}
