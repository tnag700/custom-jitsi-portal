import { $, component$, useSignal, useTask$ } from "@qwik.dev/core";
import {
  routeAction$,
  routeLoader$,
  z,
  zod$,
  type DocumentHead,
} from "@qwik.dev/router";
import {
  fetchUpcomingMeetings,
  issueAccessToken,
  JoinServiceError,
  UpcomingMeetingsList,
  JoinErrorPanel,
  canStartJoin,
  mapUpcomingMeetingsLoaderError,
  type UpcomingMeetingCard,
  type JoinErrorPayload,
} from "~/lib/domains/join";
import { RequestStatePanel } from "~/lib/shared";

interface UpcomingMeetingsLoaderData {
  meetings: UpcomingMeetingCard[];
  loadError: JoinErrorPayload | null;
}

export const useUpcomingMeetings = routeLoader$(async ({ sharedMap, cookie }) => {
  const apiUrl = sharedMap.get("apiUrl") as string;
  const sessionCookie = cookie.get("JSESSIONID")?.value ?? "";
  try {
    return {
      meetings: await fetchUpcomingMeetings(sessionCookie, apiUrl),
      loadError: null,
    } satisfies UpcomingMeetingsLoaderData;
  } catch (error) {
    return {
      meetings: [],
      loadError: mapUpcomingMeetingsLoaderError(error),
    } satisfies UpcomingMeetingsLoaderData;
  }
});

export const useJoinMeeting = routeAction$(
  async (data, { sharedMap, cookie, redirect, fail }) => {
    const apiUrl = sharedMap.get("apiUrl") as string;
    const sessionCookie = cookie.get("JSESSIONID")?.value ?? "";
    const csrfToken = cookie.get("XSRF-TOKEN")?.value ?? "";
    try {
      const result = await issueAccessToken(sessionCookie, apiUrl, csrfToken, data.meetingId);
      throw redirect(302, result.joinUrl);
    } catch (error) {
      if (error instanceof JoinServiceError) {
        if (error.payload.errorCode === "AUTH_REQUIRED") {
          throw redirect(302, "/auth");
        }
        return fail(400, { error: error.payload });
      }
      throw error; // re-throw redirect
    }
  },
  zod$(z.object({ meetingId: z.string().min(1) })),
);

export default component$(() => {
  const meetingsState = useUpcomingMeetings();
  const joinAction = useJoinMeeting();

  const joiningMeetingId = useSignal<string | null>(null);
  const retryCount = useSignal(0);
  const joinError = useSignal<JoinErrorPayload | null>(null);
  const clipboardCopied = useSignal(false);
  const MAX_RETRIES = 2;

  useTask$(({ track }) => {
    const result = track(() => joinAction.value);
    if (!result) return;

    if ("error" in result) {
      joinError.value = result.error as JoinErrorPayload;
      clipboardCopied.value = false;
    }
  });

  const handleJoin$ = $(async (meetingId: string) => {
    if (!canStartJoin(joinAction.isRunning)) {
      return;
    }
    joiningMeetingId.value = meetingId;
    joinError.value = null;
    retryCount.value = 0;
    await joinAction.submit({ meetingId });
  });

  const handleRetry$ = $(async () => {
    if (retryCount.value < MAX_RETRIES && joiningMeetingId.value) {
      retryCount.value++;
      joinError.value = null;
      await joinAction.submit({ meetingId: joiningMeetingId.value });
    }
  });

  const handleCopyReport$ = $(async () => {
    const report = {
      errorCode: joinError.value?.errorCode,
      traceId: joinError.value?.traceId,
      meetingId: joiningMeetingId.value,
      timestamp: new Date().toISOString(),
      retryCount: retryCount.value,
    };
    try {
      await navigator.clipboard.writeText(JSON.stringify(report, null, 2));
      clipboardCopied.value = true;
    } catch { /* ignore */ }
  });

  return (
    <>
      <h1 class="mb-6 text-2xl font-bold text-text">Личный кабинет</h1>

      {meetingsState.value.loadError && (
        <div class="mb-4">
          <RequestStatePanel
            tone="error"
            title={meetingsState.value.loadError.title}
            detail={meetingsState.value.loadError.detail}
          />
        </div>
      )}

      {joinError.value && (
        <div class="mb-4">
          <JoinErrorPanel
            error={joinError.value}
            retryCount={retryCount.value}
            maxRetries={MAX_RETRIES}
            onRetry$={handleRetry$}
            onCopyReport$={handleCopyReport$}
            reportCopied={clipboardCopied.value}
          />
        </div>
      )}

      <UpcomingMeetingsList
        meetings={meetingsState.value.meetings}
        joiningMeetingId={joinAction.isRunning ? joiningMeetingId.value : null}
        disabled={joinAction.isRunning}
        onJoin$={handleJoin$}
      />
    </>
  );
});

export const head: DocumentHead = {
  title: "Личный кабинет — Jitsi",
  meta: [
    {
      name: "description",
      content: "Личный кабинет — список предстоящих встреч",
    },
  ],
};
