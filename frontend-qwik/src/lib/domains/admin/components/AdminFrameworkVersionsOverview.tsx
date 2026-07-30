import { component$, type QRL } from "@qwik.dev/core";
import {
  formatFrameworkCheckTime,
  frameworkScanStatusLabel,
  frameworkSecurityStatusLabel,
  resolveFrameworkStatusTone,
} from "../admin-framework-versions.presentation";
import type { AdminFrameworkVersions } from "../types";

interface AdminFrameworkVersionsOverviewProps {
  snapshot: AdminFrameworkVersions;
  canRefresh: boolean;
  refreshing: boolean;
  onRefresh$: QRL<() => void>;
}

const TONE_CLASSES = {
  danger: "border-danger/30 bg-danger/10 text-danger",
  warning: "border-warning/30 bg-warning/10 text-warning",
  success: "border-success/30 bg-success/10 text-success",
  neutral: "border-border bg-surface-alt text-muted",
} as const;

export const AdminFrameworkVersionsOverview = component$(
  ({
    snapshot,
    canRefresh,
    refreshing,
    onRefresh$,
  }: AdminFrameworkVersionsOverviewProps) => {
    const scanTone = resolveFrameworkStatusTone(snapshot.scanStatus);

    return (
      <div class="space-y-4">
        <section class="rounded-3xl border border-border bg-surface p-5 shadow-sm">
          <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
            <div>
              <p class="text-xs uppercase tracking-[0.2em] text-muted">
                Software composition
              </p>
              <h2 class="mt-1 text-xl font-semibold text-text">
                Версии фреймворков и известные CVE
              </h2>
              <p class="mt-2 max-w-3xl text-sm text-muted">
                Сервер сверяет фактически используемые версии с OSV и хранит
                последний результат, чтобы сбой внешнего источника не ломал
                административную консоль.
              </p>
            </div>
            {canRefresh ? (
              <button
                type="button"
                class="rounded-full bg-primary px-4 py-2 text-sm font-medium text-white transition-opacity disabled:cursor-wait disabled:opacity-60"
                disabled={refreshing}
                onClick$={onRefresh$}
              >
                {refreshing ? "Проверяем…" : "Проверить сейчас"}
              </button>
            ) : null}
          </div>

          <div class="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <SummaryMetric
              label="Состояние"
              value={frameworkScanStatusLabel(snapshot.scanStatus)}
              toneClass={TONE_CLASSES[scanTone]}
            />
            <SummaryMetric
              label="Компонентов"
              value={String(snapshot.components.length)}
            />
            <SummaryMetric
              label="Уязвимостей"
              value={String(snapshot.vulnerabilityCount)}
            />
            <SummaryMetric
              label="Критических"
              value={String(snapshot.criticalVulnerabilityCount)}
              toneClass={
                snapshot.criticalVulnerabilityCount > 0
                  ? TONE_CLASSES.danger
                  : TONE_CLASSES.success
              }
            />
          </div>

          <p class="mt-4 text-sm text-muted">{snapshot.statusMessage}</p>
          <p class="mt-1 text-xs text-muted">
            Последняя полная проверка:{" "}
            <time dateTime={snapshot.lastSuccessfulCheckAt ?? undefined}>
              {formatFrameworkCheckTime(snapshot.lastSuccessfulCheckAt)}
            </time>
          </p>
        </section>

        <div class="grid gap-4 xl:grid-cols-2">
          {snapshot.components.map((framework) => {
            const securityTone = resolveFrameworkStatusTone(
              framework.securityStatus,
            );
            return (
              <article
                key={framework.key}
                class="rounded-3xl border border-border bg-surface p-5 shadow-sm"
              >
                <div class="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <h3 class="text-lg font-semibold text-text">
                      {framework.displayName}
                    </h3>
                    <p class="mt-1 font-mono text-xs text-muted">
                      {framework.packageName}
                    </p>
                  </div>
                  <span
                    class={[
                      "rounded-full border px-2.5 py-1 text-xs font-medium",
                      TONE_CLASSES[securityTone],
                    ]}
                  >
                    {frameworkSecurityStatusLabel(framework.securityStatus)}
                  </span>
                </div>

                <dl class="mt-4 grid grid-cols-2 gap-3 rounded-2xl bg-surface-alt p-3">
                  <div>
                    <dt class="text-xs text-muted">Текущая версия</dt>
                    <dd class="mt-1 font-mono text-sm font-semibold text-text">
                      {framework.currentVersion}
                    </dd>
                  </div>
                  <div>
                    <dt class="text-xs text-muted">Проверка</dt>
                    <dd class="mt-1 text-sm font-medium text-text">
                      {frameworkScanStatusLabel(framework.scanStatus)}
                    </dd>
                  </div>
                </dl>

                {framework.advisories.length === 0 ? (
                  <p class="mt-4 text-sm text-muted">
                    Для этой версии известных уязвимостей не найдено.
                  </p>
                ) : (
                  <ul class="mt-4 space-y-3">
                    {framework.advisories.map((advisory) => (
                      <li
                        key={advisory.id}
                        class="rounded-2xl border border-border p-3"
                      >
                        <div class="flex flex-wrap items-center gap-2">
                          <a
                            href={advisory.advisoryUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            referrerPolicy="no-referrer"
                            class="font-mono text-sm font-semibold text-primary hover:underline"
                          >
                            {advisory.id}
                          </a>
                          <span class="rounded-full bg-surface-alt px-2 py-0.5 text-xs uppercase text-muted">
                            {advisory.severity}
                          </span>
                        </div>
                        <p class="mt-2 text-sm text-text">{advisory.summary}</p>
                        {advisory.aliases.length > 0 ? (
                          <p class="mt-2 text-xs text-muted">
                            Идентификаторы: {advisory.aliases.join(", ")}
                          </p>
                        ) : null}
                        <p class="mt-1 text-xs text-muted">
                          Исправленные версии:{" "}
                          {advisory.fixedVersions.length > 0
                            ? advisory.fixedVersions.join(", ")
                            : "источник не указал"}
                        </p>
                      </li>
                    ))}
                  </ul>
                )}
              </article>
            );
          })}
        </div>
      </div>
    );
  },
);

interface SummaryMetricProps {
  label: string;
  value: string;
  toneClass?: string;
}

const SummaryMetric = component$(
  ({ label, value, toneClass }: SummaryMetricProps) => (
    <div
      class={[
        "rounded-2xl border border-border bg-surface-alt p-3",
        toneClass,
      ]}
    >
      <p class="text-xs uppercase tracking-[0.14em] opacity-75">{label}</p>
      <p class="mt-1 text-lg font-semibold">{value}</p>
    </div>
  ),
);
