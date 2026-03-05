import { describe, expect, it } from "vitest";
import { JoinServiceError } from "../lib/domains/join/join.service";
import {
  canStartJoin,
  mapUpcomingMeetingsLoaderError,
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
    const mapped = mapUpcomingMeetingsLoaderError(new JoinServiceError(payload));
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
});
