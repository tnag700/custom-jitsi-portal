import { component$ } from "@qwik.dev/core";
import { ApiErrorAlert, RequestStatePanel } from "~/lib/shared";
import type {
  AdminDashboardDrillDown,
  AdminDashboardErrorPayload,
} from "../types";

interface AdminDashboardDrillDownPanelProps {
  drillDown: AdminDashboardDrillDown | null;
  drillDownError: AdminDashboardErrorPayload | null;
  activeIncidentsHref: string;
  activeSelectionSummary: string;
}

export const AdminDashboardDrillDownPanel = component$(
  ({
    drillDown,
    drillDownError,
    activeIncidentsHref,
    activeSelectionSummary,
  }: AdminDashboardDrillDownPanelProps) => (
    <article class="rounded-3xl border border-border bg-surface p-4 shadow-sm md:p-5">
      <div class="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <p class="text-xs uppercase tracking-[0.2em] text-muted">
            Активная детализация
          </p>
          <h3 class="mt-1 text-lg font-semibold text-text">
            {drillDown
              ? `${drillDown.selectionType}: ${drillDown.selectionValue}`
              : "Сигнал не выбран"}
          </h3>
          <p class="mt-1 text-sm text-muted">
            {drillDown
              ? "Последние отказы для выбранного контекста."
              : activeSelectionSummary
                ? `Выбранный контекст: ${activeSelectionSummary}`
                : "Система не нашла активный сигнал. Для полного просмотра откройте очередь."}
          </p>
        </div>
        <a
          href={activeIncidentsHref}
          class="inline-flex shrink-0 justify-center rounded-xl border border-border bg-bg px-4 py-2 text-sm font-medium text-text transition-colors hover:bg-surface-alt"
        >
          Открыть очередь
        </a>
      </div>

      {drillDownError ? (
        <div class="mt-4">
          <ApiErrorAlert
            title={drillDownError.title}
            message={drillDownError.detail}
            errorCode={drillDownError.errorCode}
            traceId={drillDownError.traceId}
          />
        </div>
      ) : null}

      {drillDown ? (
        <div class="mt-4 space-y-4">
          <dl class="grid gap-3 sm:grid-cols-3">
            <div class="rounded-2xl bg-bg px-4 py-3">
              <dt class="text-xs text-muted">Отказы</dt>
              <dd class="mt-1 text-2xl font-semibold text-text">
                {drillDown.failureCount}
              </dd>
            </div>
            <div class="rounded-2xl bg-bg px-4 py-3">
              <dt class="text-xs text-muted">Окружение</dt>
              <dd class="mt-1 font-semibold uppercase text-text">
                {drillDown.environment}
              </dd>
            </div>
            <div class="rounded-2xl bg-bg px-4 py-3">
              <dt class="text-xs text-muted">Окно</dt>
              <dd class="mt-1 font-semibold text-text">{drillDown.period}</dd>
            </div>
          </dl>

          {drillDown.entityFilter.roomId ||
          drillDown.entityFilter.meetingId ? (
            <dl class="grid gap-3 rounded-2xl border border-border bg-bg px-4 py-3 text-sm sm:grid-cols-2">
              {drillDown.entityFilter.roomId ? (
                <div>
                  <dt class="text-xs text-muted">ID комнаты</dt>
                  <dd class="mt-1 break-all font-medium text-text">
                    {drillDown.entityFilter.roomId}
                  </dd>
                </div>
              ) : null}
              {drillDown.entityFilter.meetingId ? (
                <div>
                  <dt class="text-xs text-muted">ID встречи</dt>
                  <dd class="mt-1 break-all font-medium text-text">
                    {drillDown.entityFilter.meetingId}
                  </dd>
                </div>
              ) : null}
            </dl>
          ) : null}

          <div>
            <div class="flex flex-wrap items-center justify-between gap-2">
              <h4 class="font-semibold text-text">Последние отказы</h4>
              {drillDown.sampleWindowLimited ? (
                <span class="text-xs text-muted">
                  Безопасное окно выборки
                </span>
              ) : null}
            </div>
            <div class="mt-3 space-y-3">
              {drillDown.recentSamples.length > 0 ? (
                drillDown.recentSamples.map((sample) => (
                  <article
                    key={`${sample.occurredAt}-${sample.traceId ?? sample.roomId ?? sample.meetingId ?? sample.userMessage}`}
                    class="rounded-2xl border border-border bg-bg px-4 py-3"
                  >
                    <div class="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                      <div>
                        <p class="text-sm font-medium text-text">
                          {sample.userMessage}
                        </p>
                        <p class="mt-1 text-xs text-muted">
                          {sample.occurredAt}
                        </p>
                      </div>
                      {sample.traceUrl ? (
                        <a
                          href={sample.traceUrl}
                          class="text-sm font-medium text-text underline underline-offset-4"
                        >
                          Трассировка
                        </a>
                      ) : null}
                    </div>
                    <dl class="mt-3 grid gap-3 text-sm sm:grid-cols-2 xl:grid-cols-4">
                      {sample.errorCode ? (
                        <div>
                          <dt class="text-xs text-muted">Код</dt>
                          <dd class="mt-1 break-all text-text">
                            {sample.errorCode}
                          </dd>
                        </div>
                      ) : null}
                      {sample.reasonCategory ? (
                        <div>
                          <dt class="text-xs text-muted">Категория</dt>
                          <dd class="mt-1 text-text">
                            {sample.reasonCategory}
                          </dd>
                        </div>
                      ) : null}
                      {sample.roomId ? (
                        <div>
                          <dt class="text-xs text-muted">Комната</dt>
                          <dd class="mt-1 break-all text-text">
                            {sample.roomId}
                          </dd>
                        </div>
                      ) : null}
                      {sample.meetingId ? (
                        <div>
                          <dt class="text-xs text-muted">Встреча</dt>
                          <dd class="mt-1 break-all text-text">
                            {sample.meetingId}
                          </dd>
                        </div>
                      ) : null}
                    </dl>
                  </article>
                ))
              ) : (
                <RequestStatePanel
                  title="Нет свежих выборок"
                  detail="Для активного сигнала не нашлось недавних отказов в пределах безопасного окна чтения."
                />
              )}
            </div>
          </div>
        </div>
      ) : null}
    </article>
  ),
);
