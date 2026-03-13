import type { ApiErrorPayload } from "../../shared/api";
import { adaptProblemDetails } from "../../shared/api";
import type { InviteErrorPayload, InviteExchangeResponse, InviteValidationResponse } from "./types";

export class InviteExchangeError extends Error {
  payload: InviteErrorPayload;

  constructor(payload: InviteErrorPayload) {
    super(payload.detail);
    this.name = "InviteExchangeError";
    this.payload = payload;
  }
}

function fallbackErrorCode(status: number): string {
  if (status === 404) return "INVITE_NOT_FOUND";
  if (status === 409) return "INVITE_EXHAUSTED";
  if (status === 410) return "INVITE_EXPIRED";
  if (status >= 500) return "INVITE_SERVICE_UNAVAILABLE";
  return "INVITE_UNKNOWN";
}

function resolveGoneErrorCode(problem: ApiErrorPayload): "INVITE_EXPIRED" | "INVITE_REVOKED" {
  const haystack = `${problem.title} ${problem.detail}`.toLowerCase();
  if (haystack.includes("revoke") || haystack.includes("отоз")) {
    return "INVITE_REVOKED";
  }
  return "INVITE_EXPIRED";
}

async function adaptExchangeProblemDetails(response: Response): Promise<InviteErrorPayload> {
  const payload = await adaptProblemDetails(
    response,
    response.status,
    fallbackErrorCode,
    "Ошибка гостевого входа",
    "Не удалось обработать инвайт.",
  );

  const errorCode =
    response.status === 410 && payload.errorCode === "INVITE_EXPIRED"
      ? resolveGoneErrorCode(payload)
      : payload.errorCode;

  return { title: payload.title, detail: payload.detail, errorCode, traceId: payload.traceId };
}

export async function exchangeInvite(
  apiUrl: string,
  inviteToken: string,
  displayName?: string,
): Promise<InviteExchangeResponse> {
  const response = await fetch(`${apiUrl}/invites/exchange`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ inviteToken, displayName }),
  });

  if (!response.ok) {
    throw new InviteExchangeError(await adaptExchangeProblemDetails(response));
  }

  return (await response.json()) as InviteExchangeResponse;
}

export async function validateInviteToken(
  apiUrl: string,
  inviteToken: string,
): Promise<InviteValidationResponse> {
  const response = await fetch(`${apiUrl}/invites/${encodeURIComponent(inviteToken)}/validate`, {
    method: "GET",
    headers: {
      "Content-Type": "application/json",
    },
  });

  if (!response.ok) {
    throw new InviteExchangeError(await adaptExchangeProblemDetails(response));
  }

  return (await response.json()) as InviteValidationResponse;
}
