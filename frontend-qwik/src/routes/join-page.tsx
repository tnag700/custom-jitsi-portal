import { $, component$, useSignal, useTask$ } from "@qwik.dev/core";
import {
  createInitialPreflightReport,
  createPreflightJoinError,
  fetchJoinReadiness,
  JoinErrorPanel,
  JoinPreflightPanel,
  mergePreflightReport,
  resolveExpectedJoinOrigin,
  resolveRetryPreflightScope,
  runBrowserPreflight,
  UpcomingMeetingsList,
  validateJoinRedirect,
  canStartJoin,
  type JoinErrorPayload,
  type JoinPreflightReport,
  type JoinReadinessPayload,
  type PreflightScope,
} from "~/lib/domains/join";
import { RequestStatePanel } from "~/lib/shared";
import { useJoinMeeting } from "./join-action";
import {
  useJoinReadiness,
  useJoinRuntimeConfig,
  useUpcomingMeetings,
} from "./join-loaders";

const MAX_JOIN_RETRIES = 2;

export const JoinPage = component$(() => {
  const meetingsState = useUpcomingMeetings();
  const runtimeConfig = useJoinRuntimeConfig();
  const readinessState = useJoinReadiness();
  const joinAction = useJoinMeeting();

  const joiningMeetingId = useSignal<string | null>(null);
  const retryCount = useSignal(0);
  const joinError = useSignal<JoinErrorPayload | null>(null);
  const clipboardCopied = useSignal(false);
  const readinessSnapshot = useSignal<JoinReadinessPayload>(
    readinessState.value,
  );
  const preflightReport = useSignal<JoinPreflightReport>(
    createInitialPreflightReport(readinessState.value),
  );
  const preflightRunning = useSignal(false);

  const redirectToJoin$ = $((payload: unknown) => {
    const validated = validateJoinRedirect(
      payload,
      resolveExpectedJoinOrigin(readinessSnapshot.value.publicJoinUrl),
    );
    if (validated.error) {
      joinError.value = validated.error;
      clipboardCopied.value = false;
      return;
    }
    if (validated.joinUrl) {
      window.location.assign(validated.joinUrl);
    }
  });

  const refreshPreflight$ = $(async (scope: PreflightScope) => {
    preflightRunning.value = true;
    try {
      const snapshot =
        scope === "media"
          ? null
          : await fetchJoinReadiness(runtimeConfig.value.publicApiUrl);
      if (snapshot) {
        readinessSnapshot.value = snapshot;
      }

      const browserChecks = await runBrowserPreflight({
        publicJoinUrl:
          snapshot?.publicJoinUrl ??
          readinessSnapshot.value.publicJoinUrl ??
          null,
        scope,
      });

      const nextReport = mergePreflightReport(
        preflightReport.value,
        snapshot,
        browserChecks,
        scope,
      );
      preflightReport.value = nextReport;
      return nextReport;
    } finally {
      preflightRunning.value = false;
    }
  });

  useTask$(({ track }) => {
    const result = track(() => joinAction.value);
    if (!result) return;

    if ("error" in result) {
      joinError.value = result.error as JoinErrorPayload;
      clipboardCopied.value = false;
    }
  });

  useTask$(async () => {
    if (typeof window === "undefined") {
      return;
    }
    await refreshPreflight$("full");
  });

  const handleJoin$ = $(async (meetingId: string) => {
    if (!canStartJoin(joinAction.isRunning)) {
      return;
    }
    joiningMeetingId.value = meetingId;
    joinError.value = null;
    retryCount.value = 0;
    clipboardCopied.value = false;
    const result = await joinAction.submit({ meetingId });
    if (typeof window !== "undefined" && result?.value) {
      await redirectToJoin$(result.value);
    }
  });

  const handleRetry$ = $(async () => {
    if (retryCount.value >= MAX_JOIN_RETRIES || !joiningMeetingId.value) {
      return;
    }

    const scope = resolveRetryPreflightScope(joinError.value?.errorCode);
    const report = await refreshPreflight$(scope);
    const preflightError = createPreflightJoinError(report, scope);
    if (preflightError) {
      joinError.value = preflightError;
      clipboardCopied.value = false;
      return;
    }

    retryCount.value++;
    joinError.value = null;
    clipboardCopied.value = false;
    const result = await joinAction.submit({
      meetingId: joiningMeetingId.value,
    });
    if (typeof window !== "undefined" && result?.value) {
      await redirectToJoin$(result.value);
    }
  });

  const handleRefreshPreflight$ = $(async () => {
    await refreshPreflight$("full");
  });

  const handleCopyReport$ = $(async () => {
    const report = {
      errorCode: joinError.value?.errorCode,
      traceId: joinError.value?.traceId,
      meetingId: joiningMeetingId.value,
      timestamp: new Date().toISOString(),
      retryCount: retryCount.value,
      preflight: preflightReport.value,
    };
    try {
      await navigator.clipboard.writeText(JSON.stringify(report, null, 2));
      clipboardCopied.value = true;
    } catch {
      /* Browser may deny clipboard access. */
    }
  });

  const readinessStatusLabel =
    preflightReport.value.status === "checking"
      ? "Идёт проверка"
      : preflightReport.value.status === "ready"
        ? "Можно входить"
        : preflightReport.value.status === "blocked"
          ? "Нужно исправить проблемы"
          : "Есть предупреждения";

  const readinessStatusClass =
    preflightReport.value.status === "ready"
      ? "bg-success/12 text-success"
      : preflightReport.value.status === "blocked"
        ? "bg-danger/12 text-danger"
        : "bg-warning/12 text-warning";

  return (
    <>
      <h1 class="mb-2 text-2xl font-bold text-text">Ближайшие встречи</h1>
      <p class="mb-6 max-w-3xl text-sm text-muted">
        Здесь главное действие — быстро войти во встречу. Проверку оборудования
        и подключения можно открыть ниже, если вход не срабатывает или есть
        проблемы со звуком и камерой.
      </p>

      {meetingsState.value.loadError ? (
        <div class="mb-4">
          <RequestStatePanel
            tone="error"
            title={meetingsState.value.loadError.title}
            detail={meetingsState.value.loadError.detail}
          />
        </div>
      ) : null}

      {joinError.value ? (
        <div class="mb-4">
          <JoinErrorPanel
            error={joinError.value}
            retryCount={retryCount.value}
            maxRetries={MAX_JOIN_RETRIES}
            onRetry$={handleRetry$}
            onCopyReport$={handleCopyReport$}
            reportCopied={clipboardCopied.value}
          />
        </div>
      ) : null}

      <UpcomingMeetingsList
        meetings={meetingsState.value.meetings}
        joiningMeetingId={joinAction.isRunning ? joiningMeetingId.value : null}
        disabled={joinAction.isRunning}
        onJoin$={handleJoin$}
      />

      <details class="mt-8 overflow-hidden rounded-2xl border border-border bg-surface shadow-1">
        <summary class="flex cursor-pointer list-none items-center justify-between gap-4 px-4 py-4">
          <div>
            <h2 class="text-base font-semibold text-text">
              Проверка оборудования и подключения
            </h2>
            <p class="text-sm text-muted">
              Откройте этот блок, если нужно проверить доступ к серверу,
              браузерные разрешения и камеру с микрофоном.
            </p>
          </div>
          <span
            class={[
              "shrink-0 rounded-full px-3 py-1 text-xs font-semibold",
              readinessStatusClass,
            ]}
          >
            {readinessStatusLabel}
          </span>
        </summary>

        <div class="border-t border-border px-4 py-4">
          <JoinPreflightPanel
            report={preflightReport.value}
            running={preflightRunning.value}
            onRefresh$={handleRefreshPreflight$}
          />
        </div>
      </details>
    </>
  );
});
