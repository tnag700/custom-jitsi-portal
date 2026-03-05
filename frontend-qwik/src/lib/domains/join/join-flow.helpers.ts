import { JoinServiceError } from "./join.service";
import type { JoinErrorPayload } from "./types";

export function canStartJoin(joinInFlight: boolean): boolean {
  return !joinInFlight;
}

export function mapUpcomingMeetingsLoaderError(error: unknown): JoinErrorPayload {
  if (error instanceof JoinServiceError) {
    return error.payload;
  }

  return {
    title: "Не удалось загрузить встречи",
    detail: "Список встреч временно недоступен. Обновите страницу позже.",
    errorCode: "UPCOMING_MEETINGS_LOAD_FAILED",
  };
}
