import { routeLoader$ } from "@qwik.dev/router";
import {
  fetchJoinReadiness,
  fetchUpcomingMeetings,
  JoinServiceError,
  mapUpcomingMeetingsLoaderError,
  type JoinErrorPayload,
  type JoinReadinessPayload,
  type UpcomingMeetingCard,
} from "~/lib/domains/join";
import { buildServerRequestContext } from "~/lib/shared/routes/server-handlers";

interface UpcomingMeetingsLoaderData {
  meetings: UpcomingMeetingCard[];
  loadError: JoinErrorPayload | null;
}

const DEFAULT_PUBLIC_API_URL = "http://localhost:8080/api/v1";

interface JoinPageRuntimeConfig {
  publicApiUrl: string;
}

// eslint-disable-next-line qwik/loader-location
export const useUpcomingMeetings = routeLoader$(
  async ({ sharedMap, cookie }) => {
    const requestContext = buildServerRequestContext({ sharedMap, cookie });
    try {
      return {
        meetings: await fetchUpcomingMeetings(requestContext),
        loadError: null,
      } satisfies UpcomingMeetingsLoaderData;
    } catch (error) {
      return {
        meetings: [],
        loadError: mapUpcomingMeetingsLoaderError(error),
      } satisfies UpcomingMeetingsLoaderData;
    }
  },
);

// eslint-disable-next-line qwik/loader-location
export const useJoinRuntimeConfig = routeLoader$(({ sharedMap }) => {
  return {
    publicApiUrl:
      (sharedMap.get("publicApiUrl") as string) || DEFAULT_PUBLIC_API_URL,
  } satisfies JoinPageRuntimeConfig;
});

// eslint-disable-next-line qwik/loader-location
export const useJoinReadiness = routeLoader$(async ({ sharedMap, cookie }) => {
  const requestContext = buildServerRequestContext({ sharedMap, cookie });
  try {
    return await fetchJoinReadiness(requestContext);
  } catch (error) {
    const payload = error instanceof JoinServiceError ? error.payload : null;
    return {
      status: "blocked",
      checkedAt: new Date().toISOString(),
      traceId: payload?.traceId ?? null,
      publicJoinUrl: null,
      systemChecks: [
        {
          key: "backend",
          status: "error",
          headline: "Не удалось получить readiness snapshot",
          reason:
            payload?.detail ??
            "Backend не вернул данные диагностики перед входом.",
          actions: ["Повторить диагностику", "Проверить доступность backend"],
          errorCode: payload?.errorCode ?? "JOIN_READINESS_UNAVAILABLE",
          blocking: true,
        },
      ],
    } satisfies JoinReadinessPayload;
  }
});
