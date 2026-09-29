import { $, component$, useSignal, useTask$, useVisibleTask$ } from "@qwik.dev/core";
import {
  createInitialPreflightReport,
  createPreflightJoinError,
  fetchJoinReadiness,
  JoinErrorPanel,
  JoinPreflightPanel,
  JoinServiceError,
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
  const joinRunning = useSignal(false);

  const redirectToJoin$ = $((payload: unknown) => {
    if (payload && typeof payload === "object" && "error" in payload) {
      joinError.value = payload.error as JoinErrorPayload;
      clipboardCopied.value = false;
      return;
    }
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
    if (preflightRunning.value) return null;
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
      if (!joiningMeetingId.value && joinError.value) {
        joinError.value = createPreflightJoinError(nextReport, scope);
        clipboardCopied.value = false;
      }
      return nextReport;
    } catch (error) {
      const payload = error instanceof JoinServiceError ? error.payload : {
        title: "Не удалось обновить диагностику",
        detail: "Проверка временно недоступна. Повторите попытку.",
        errorCode: "JOIN_READINESS_UNAVAILABLE",
      };
      joinError.value = payload;
      clipboardCopied.value = false;
      preflightReport.value = {
        ...preflightReport.value,
        status: "blocked",
        systemChecks: [{
          key: "backend", status: "error", headline: payload.title,
          reason: payload.detail, errorCode: payload.errorCode,
          actions: ["Повторить диагностику"], blocking: true,
        }],
      };
      return null;
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

  // Browser media APIs must run after the server-rendered page resumes.
  // eslint-disable-next-line qwik/no-use-visible-task
  useVisibleTask$(async () => {
    if (!joinRunning.value) await refreshPreflight$("full");
  }, { strategy: "document-ready" });

  const submitJoin$ = $(async (meetingId: string) => {
    try {
      const result = await joinAction.submit({ meetingId });
      if (typeof window !== "undefined" && result?.value) {
        await redirectToJoin$(result.value);
      }
    } catch {
      joinError.value = {
        title: "Не удалось войти во встречу",
        detail: "Соединение прервано. Повторите попытку.",
        errorCode: "NETWORK_UNREACHABLE",
      };
      clipboardCopied.value = false;
    }
  });

  const handleJoin$ = $(async (meetingId: string) => {
    if (!canStartJoin(joinRunning.value || preflightRunning.value || joinAction.isRunning)) {
      return;
    }
    joinRunning.value = true;
    joiningMeetingId.value = meetingId;
    joinError.value = null;
    retryCount.value = 0;
    clipboardCopied.value = false;
    try {
      await submitJoin$(meetingId);
    } finally {
      joinRunning.value = false;
    }
  });

  const handleRetry$ = $(async () => {
    if (joinRunning.value || preflightRunning.value || joinAction.isRunning) {
      return;
    }
    if (!joiningMeetingId.value) {
      await refreshPreflight$("full");
      return;
    }
    if (retryCount.value >= MAX_JOIN_RETRIES) return;
    joinRunning.value = true;
    const meetingId = joiningMeetingId.value;
    try {
      const scope = resolveRetryPreflightScope(joinError.value?.errorCode);
      const report = await refreshPreflight$(scope);
      if (!report) return;
      const preflightError = createPreflightJoinError(report, scope);
      if (preflightError) {
        joinError.value = preflightError;
        clipboardCopied.value = false;
        return;
      }

      retryCount.value++;
      joinError.value = null;
      clipboardCopied.value = false;
      await submitJoin$(meetingId);
    } finally {
      joinRunning.value = false;
    }
  });

  const handleRefreshPreflight$ = $(async () => {
    if (!joinRunning.value && !joinAction.isRunning) await refreshPreflight$("full");
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
            retryDisabled={joinRunning.value || preflightRunning.value || joinAction.isRunning}
          />
        </div>
      ) : null}

      <UpcomingMeetingsList
        meetings={meetingsState.value.meetings}
        joiningMeetingId={joinRunning.value || joinAction.isRunning ? joiningMeetingId.value : null}
        disabled={joinRunning.value || preflightRunning.value || joinAction.isRunning}
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
            running={joinRunning.value || preflightRunning.value || joinAction.isRunning}
            onRefresh$={handleRefreshPreflight$}
          />
        </div>
      </details>
    </>
  );
});
