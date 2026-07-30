import { JoinServiceError } from "./join.service";
import type { JoinErrorPayload } from "./types";

export function canStartJoin(joinInFlight: boolean): boolean {
  return !joinInFlight;
}

export function mapUpcomingMeetingsLoaderError(
  error: unknown,
): JoinErrorPayload {
  if (error instanceof JoinServiceError) {
    return error.payload;
  }

  return {
    title: "Не удалось загрузить встречи",
    detail: "Список встреч временно недоступен. Обновите страницу позже.",
    errorCode: "UPCOMING_MEETINGS_LOAD_FAILED",
  };
}

export interface ValidatedJoinRedirect {
  joinUrl: string | null;
  error: JoinErrorPayload | null;
}

function createInvalidJoinUrlError(detail: string): JoinErrorPayload {
  return {
    title: "Небезопасный redirect отклонен",
    detail,
    errorCode: "JOIN_RESPONSE_INVALID",
  };
}

export function resolveExpectedJoinOrigin(
  publicJoinUrl: string | null | undefined,
): string | null {
  if (!publicJoinUrl) {
    return null;
  }
  try {
    const url = new URL(publicJoinUrl);
    return url.protocol === "https:" ? url.origin : null;
  } catch {
    return null;
  }
}

export function validateJoinRedirect(
  payload: unknown,
  expectedOrigin: string | null,
): ValidatedJoinRedirect {
  if (
    !payload ||
    typeof payload !== "object" ||
    !("joinUrl" in payload) ||
    typeof payload.joinUrl !== "string"
  ) {
    return {
      joinUrl: null,
      error: createInvalidJoinUrlError(
        "Backend вернул ответ без корректного joinUrl.",
      ),
    };
  }

  try {
    const url = new URL(payload.joinUrl);
    if (url.protocol !== "https:") {
      return {
        joinUrl: null,
        error: createInvalidJoinUrlError(
          "Backend вернул joinUrl с небезопасной схемой.",
        ),
      };
    }
    if (url.username || url.password) {
      return {
        joinUrl: null,
        error: createInvalidJoinUrlError(
          "Backend вернул joinUrl с недопустимыми credentials.",
        ),
      };
    }
    if (expectedOrigin && url.origin !== expectedOrigin) {
      return {
        joinUrl: null,
        error: createInvalidJoinUrlError(
          "Backend вернул joinUrl вне разрешенного origin.",
        ),
      };
    }
    return { joinUrl: url.toString(), error: null };
  } catch {
    return {
      joinUrl: null,
      error: createInvalidJoinUrlError(
        "Backend вернул синтаксически некорректный joinUrl.",
      ),
    };
  }
}
