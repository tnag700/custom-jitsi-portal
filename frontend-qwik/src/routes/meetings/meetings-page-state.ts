import type { Meeting } from "~/lib/domains/meetings";
import type { AppToastState } from "~/lib/shared/components/AppToast";
import type { CopyTextOutcome } from "~/lib/shared/browser/copy-text";

interface MeetingsHrefOptions {
  meetingId?: string;
  invitesMeetingId?: string;
}

interface ActionErrorValue<TError> {
  error: TError;
}

interface SuccessfulActionValue {
  success: true;
}

export interface ActionValidationFeedback {
  fieldErrors: Record<string, string>;
  formErrors: string[];
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function firstErrorMessage(value: unknown): string | undefined {
  if (typeof value === "string" && value.length > 0) {
    return value;
  }
  if (Array.isArray(value)) {
    return value.find(
      (message): message is string =>
        typeof message === "string" && message.length > 0,
    );
  }
  return undefined;
}

export function findMeetingById(
  meetings: Meeting[],
  meetingId: string,
): Meeting | null {
  if (!meetingId) {
    return null;
  }

  return meetings.find((meeting) => meeting.meetingId === meetingId) ?? null;
}

export function getActionError<TError>(
  actionValue: unknown,
): TError | undefined {
  if (!isRecord(actionValue) || !("error" in actionValue)) {
    return undefined;
  }

  return (actionValue as unknown as ActionErrorValue<TError>).error;
}

export function getFirstActionError<TError>(
  ...actionValues: unknown[]
): TError | undefined {
  for (const actionValue of actionValues) {
    const error = getActionError<TError>(actionValue);
    if (error !== undefined) {
      return error;
    }
  }

  return undefined;
}

export function isSuccessfulAction(
  actionValue: unknown,
): actionValue is SuccessfulActionValue {
  return isRecord(actionValue) && actionValue.success === true;
}

export function shouldReloadAfterParticipantMutation(
  ...actionValues: unknown[]
): boolean {
  return actionValues.some(isSuccessfulAction);
}

export function resolveInviteCopyToast(
  outcome: CopyTextOutcome,
): AppToastState {
  if (outcome === "copied") {
    return { message: "Ссылка скопирована", tone: "info" };
  }

  if (outcome === "manual") {
    return {
      message: "Ссылка открыта для ручного копирования",
      tone: "warning",
    };
  }

  return { message: "Ссылка не скопирована", tone: "error" };
}

export function getActionValidationFeedback(
  actionValue: unknown,
): ActionValidationFeedback | undefined {
  if (!isRecord(actionValue) || actionValue.failed !== true) {
    return undefined;
  }

  const fieldErrors: Record<string, string> = {};
  if (isRecord(actionValue.fieldErrors)) {
    for (const [field, value] of Object.entries(actionValue.fieldErrors)) {
      const message = firstErrorMessage(value);
      if (message) {
        fieldErrors[field] = message;
      }
    }
  }

  const formErrors = Array.isArray(actionValue.formErrors)
    ? actionValue.formErrors.filter(
        (message): message is string =>
          typeof message === "string" && message.length > 0,
      )
    : [];

  return { fieldErrors, formErrors };
}

export function buildMeetingsHref(
  roomId = "",
  options: MeetingsHrefOptions = {},
): string {
  const params: string[] = [];

  if (roomId) {
    params.push(`roomId=${encodeURIComponent(roomId)}`);
  }
  if (options.meetingId) {
    params.push(`meetingId=${encodeURIComponent(options.meetingId)}`);
  }
  if (options.invitesMeetingId) {
    params.push(
      `invitesMeetingId=${encodeURIComponent(options.invitesMeetingId)}`,
    );
  }

  const query = params.join("&");
  return query ? `/meetings?${query}` : "/meetings";
}
