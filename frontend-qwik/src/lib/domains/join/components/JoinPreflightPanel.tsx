import { component$, type QRL } from "@qwik.dev/core";
import type { JoinPreflightReport } from "../preflight";

interface JoinPreflightPanelProps {
  report: JoinPreflightReport;
  running: boolean;
  onRefresh$: QRL<() => void>;
}

export const JoinPreflightPanel = component$<JoinPreflightPanelProps>(({ report, running, onRefresh$ }) => {
  return (
    <section class="rounded-2xl border border-border/70 bg-surface/85 p-4 shadow-soft" aria-live="polite">
      <div class="mb-3 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 class="text-base font-semibold text-text">Детали диагностики</h2>
          <p class="text-sm text-muted">
            Статус: {resolveReportStatusLabel(report.status)}
          </p>
        </div>
        <button
          type="button"
          class="rounded-full border border-border px-3 py-1.5 text-sm text-text transition hover:border-primary hover:text-primary disabled:cursor-not-allowed disabled:opacity-60"
          onClick$={() => onRefresh$()}
          disabled={running}
        >
          {running ? "Проверяем..." : "Обновить проверку"}
        </button>
      </div>

      <div class="grid gap-4 lg:grid-cols-2">
        <PreflightSection title="Системные проверки" checks={report.systemChecks} />
        <PreflightSection title="Проверка аудио и видео" checks={report.mediaChecks} />
      </div>

      <div class="mt-3 flex flex-wrap gap-4 text-xs text-muted">
        <span>Проверено: {report.checkedAt ?? "ещё не завершено"}</span>
        {report.traceId && <span>traceId: {report.traceId}</span>}
        {report.publicJoinUrl && <span>Jitsi: {report.publicJoinUrl}</span>}
      </div>
    </section>
  );
});

interface PreflightSectionProps {
  title: string;
  checks: Array<JoinPreflightReport["systemChecks"][number]>;
}

const PreflightSection = component$<PreflightSectionProps>(({ title, checks }) => {
  return (
    <div class="rounded-2xl border border-border/60 bg-bg/65 p-3">
      <h3 class="mb-3 text-sm font-semibold text-text">{title}</h3>
      <div class="space-y-3">
        {checks.map((check) => (
          <article key={check.key} class="rounded-xl border border-border/60 bg-surface/75 p-3">
            <div class="mb-1 flex items-center justify-between gap-3">
              <p class="text-sm font-semibold text-text">{check.headline}</p>
              <span class={resolveStatusClass(check.status)}>{resolveCheckStatusLabel(check.status)}</span>
            </div>
            <p class="text-sm text-muted">{check.reason}</p>
            {check.actions.length > 0 && (
              <p class="mt-2 text-xs text-text">Что сделать: {check.actions.join(" · ")}</p>
            )}
            {check.errorCode && <p class="mt-1 text-xs text-muted">errorCode: {check.errorCode}</p>}
          </article>
        ))}
      </div>
    </div>
  );
});

function resolveStatusClass(status: JoinPreflightReport["systemChecks"][number]["status"]) {
  if (status === "ok") {
    return "text-xs font-semibold uppercase tracking-wide text-success";
  }
  if (status === "warn") {
    return "text-xs font-semibold uppercase tracking-wide text-warning";
  }
  return "text-xs font-semibold uppercase tracking-wide text-danger";
}

function resolveCheckStatusLabel(status: JoinPreflightReport["systemChecks"][number]["status"]) {
  if (status === "ok") {
    return "OK";
  }
  if (status === "warn") {
    return "Предупреждение";
  }
  if (status === "timeout") {
    return "Таймаут";
  }
  return "Ошибка";
}

function resolveReportStatusLabel(status: JoinPreflightReport["status"]) {
  if (status === "checking") {
    return "идёт проверка";
  }
  if (status === "ready") {
    return "можно входить";
  }
  if (status === "blocked") {
    return "вход блокируется";
  }
  return "есть предупреждения";
}