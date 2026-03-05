import { afterEach, describe, expect, it, vi } from "vitest";
import {
  InviteExchangeError,
  exchangeInvite,
  validateInviteToken,
} from "../lib/domains/invites/invite-exchange.service";

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json",
    },
  });
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("invites exchange runtime", () => {
  it("validateInviteToken calls GET validate endpoint and returns payload", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse({ valid: true, meetingId: "m-1" }, 200),
    );

    const result = await validateInviteToken("http://localhost:8080/api/v1", "token-123");

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/invites/token-123/validate",
      {
        method: "GET",
        headers: {
          "Content-Type": "application/json",
        },
      },
    );
    expect(result).toEqual({ valid: true, meetingId: "m-1" });
  });

  it("maps 410 revoke semantics from problem details text", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse(
        {
          title: "Инвайт отозван",
          detail: "Инвайт был отозван администратором",
        },
        410,
      ),
    );

    await expect(exchangeInvite("http://localhost:8080/api/v1", "token-1")).rejects.toMatchObject({
      payload: {
        errorCode: "INVITE_REVOKED",
      },
    });
  });

  it("maps 410 fallback to INVITE_EXPIRED when revoke semantics absent", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse(
        {
          title: "Срок действия истёк",
          detail: "Токен более недействителен",
        },
        410,
      ),
    );

    await expect(exchangeInvite("http://localhost:8080/api/v1", "token-1")).rejects.toMatchObject({
      payload: {
        errorCode: "INVITE_EXPIRED",
      },
    });
  });

  it("throws InviteExchangeError for validateInviteToken non-2xx", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse(
        {
          title: "Инвайт не найден",
          detail: "Invite not found",
          errorCode: "INVITE_NOT_FOUND",
        },
        404,
      ),
    );

    try {
      await validateInviteToken("http://localhost:8080/api/v1", "missing");
      throw new Error("Expected validateInviteToken to throw");
    } catch (error) {
      expect(error).toBeInstanceOf(InviteExchangeError);
      const inviteError = error as InviteExchangeError;
      expect(inviteError.payload.errorCode).toBe("INVITE_NOT_FOUND");
    }
  });
});
