import type { InviteErrorPayload, InviteExchangeResponse, InviteValidationResponse } from "./types";

export class InviteExchangeError extends Error {
  payload: InviteErrorPayload;

  constructor(payload: InviteErrorPayload) {
    super(payload.detail);
    this.name = "InviteExchangeError";
    this.payload = payload;
  }
}

interface ProblemDetailsLike {
  title?: unknown;
  detail?: unknown;
  errorCode?: unknown;
  traceId?: unknown;
}

function fallbackErrorCode(status: number): string {
  if (status === 404) return "INVITE_NOT_FOUND";
  if (status === 409) return "INVITE_EXHAUSTED";
  if (status === 410) return "INVITE_EXPIRED";
  if (status >= 500) return "INVITE_SERVICE_UNAVAILABLE";
  return "INVITE_UNKNOWN";
}

function resolveGoneErrorCode(problem: ProblemDetailsLike): "INVITE_EXPIRED" | "INVITE_REVOKED" {
  const haystack = `${String(problem.title ?? "")} ${String(problem.detail ?? "")}`.toLowerCase();
  if (haystack.includes("revoke") || haystack.includes("отоз")) {
    return "INVITE_REVOKED";
  }
  return "INVITE_EXPIRED";
}

async function readProblemDetails(response: Response): Promise<ProblemDetailsLike> {
  try {
    return (await response.json()) as ProblemDetailsLike;
  } catch {
    return {};
  }
}

async function adaptExchangeProblemDetails(response: Response): Promise<InviteErrorPayload> {
  const problem = await readProblemDetails(response);
  const explicitCode = typeof problem.errorCode === "string" && problem.errorCode.length > 0
    ? problem.errorCode
    : undefined;
  const errorCode = explicitCode
    ?? (response.status === 410 ? resolveGoneErrorCode(problem) : fallbackErrorCode(response.status));

  return {
    title:
      typeof problem.title === "string" && problem.title.length > 0
        ? problem.title
        : "Ошибка гостевого входа",
    detail:
      typeof problem.detail === "string" && problem.detail.length > 0
        ? problem.detail
        : "Не удалось обработать инвайт.",
    errorCode,
    traceId:
      typeof problem.traceId === "string" && problem.traceId.length > 0
        ? problem.traceId
        : undefined,
  };
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
