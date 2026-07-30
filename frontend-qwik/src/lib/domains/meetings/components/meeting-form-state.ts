import { parseDateTimeLocalInput } from "~/lib/shared";
import { createMeetingSchema, updateMeetingSchema } from "../meetings.zod";

export interface MeetingFormValues {
  title: string;
  description: string;
  meetingType: string;
  startsAtLocal: string;
  endsAtLocal: string;
  allowGuests: boolean;
  recordingEnabled: boolean;
}

interface MeetingFormContext {
  roomId: string;
  meetingId?: string;
}

export interface MeetingFormSubmissionPayload {
  title: string;
  description?: string;
  meetingType: string;
  startsAt: string;
  endsAt: string;
  allowGuests: boolean;
  recordingEnabled: boolean;
  roomId?: string;
  meetingId?: string;
}

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
  context: MeetingFormContext,
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
  const schema = context.meetingId
    ? updateMeetingSchema
    : createMeetingSchema;
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
    payload: {
      ...candidate,
      ...(context.meetingId
        ? { meetingId: context.meetingId }
        : { roomId: context.roomId }),
    },
  };
}
