import { describe, expect, it } from "vitest";
import { JoinServiceError } from "../lib/domains/join/join.service";
import {
  canStartJoin,
  mapUpcomingMeetingsLoaderError,
  resolveExpectedJoinOrigin,
  validateJoinRedirect,
} from "../lib/domains/join/join-flow.helpers";

describe("join-flow.helpers runtime", () => {
  it("canStartJoin returns false while join request is in flight", () => {
    expect(canStartJoin(true)).toBe(false);
    expect(canStartJoin(false)).toBe(true);
  });

  it("mapUpcomingMeetingsLoaderError preserves JoinServiceError payload", () => {
    const payload = {
      title: "Unauthorized",
      detail: "Session expired",
      errorCode: "AUTH_REQUIRED",
      traceId: "trace-123",
    };
    const mapped = mapUpcomingMeetingsLoaderError(
      new JoinServiceError(payload),
    );
    expect(mapped).toEqual(payload);
  });

  it("mapUpcomingMeetingsLoaderError returns generic reusable payload for unknown errors", () => {
    const mapped = mapUpcomingMeetingsLoaderError(new Error("boom"));
    expect(mapped).toEqual({
      title: "Не удалось загрузить встречи",
      detail: "Список встреч временно недоступен. Обновите страницу позже.",
      errorCode: "UPCOMING_MEETINGS_LOAD_FAILED",
    });
  });

  it("accepts an HTTPS join URL only on the expected Jitsi origin", () => {
    expect(
      validateJoinRedirect(
        { joinUrl: "https://meet.example.test/room?jwt=token" },
        "https://meet.example.test",
      ),
    ).toEqual({
      joinUrl: "https://meet.example.test/room?jwt=token",
      error: null,
    });
  });

  it.each([
    [{}, "ответ без корректного joinUrl"],
    [
      { joinUrl: "http://meet.example.test/room" },
      "joinUrl с небезопасной схемой",
    ],
    [
      { joinUrl: "https://user:secret@meet.example.test/room" },
      "joinUrl с недопустимыми credentials",
    ],
    [
      { joinUrl: "https://attacker.example/room" },
      "joinUrl вне разрешенного origin",
    ],
  ])("rejects an unsafe join redirect: %s", (payload, expectedDetail) => {
    const result = validateJoinRedirect(payload, "https://meet.example.test");
    expect(result.joinUrl).toBeNull();
    expect(result.error?.errorCode).toBe("JOIN_RESPONSE_INVALID");
    expect(result.error?.detail).toContain(expectedDetail);
  });

  it("derives only an HTTPS expected join origin", () => {
    expect(resolveExpectedJoinOrigin("https://meet.example.test/room")).toBe(
      "https://meet.example.test",
    );
    expect(
      resolveExpectedJoinOrigin("http://meet.example.test/room"),
    ).toBeNull();
    expect(resolveExpectedJoinOrigin("not-a-url")).toBeNull();
  });
});
