import { component$ } from "@qwik.dev/core";
import { buildIncidentDetailHref } from "../admin-incidents.route-helpers";
import type {
  AdminIncidentSearch,
} from "../types";

interface AdminIncidentSearchResultProps {
  currentUrl: string;
  searchResult: AdminIncidentSearch;
  effectiveEnvironment: string;
}

const SEARCH_OUTCOME_LABELS: Record<string, string> = {
  candidates: "Найдены близкие совпадения",
  "no-match": "Совпадений не найдено",
  "exact-match": "Найден точный инцидент",
};

export const AdminIncidentSearchResult = component$(
  ({
    currentUrl,
    searchResult,
    effectiveEnvironment,
  }: AdminIncidentSearchResultProps) => (
    <section class="rounded-2xl border border-border bg-surface p-4 shadow-sm sm:p-5">
      <div class="flex flex-wrap items-start justify-between gap-2">
        <div>
          <p class="text-xs uppercase tracking-[0.18em] text-muted">
            Точный поиск
          </p>
          <h3 class="mt-1 text-base font-semibold text-text">
            {SEARCH_OUTCOME_LABELS[searchResult.outcome] ??
              searchResult.outcome}
          </h3>
        </div>
        <span class="rounded-full border border-border px-2.5 py-1 text-xs text-muted">
          {searchResult.candidates.length} кандидатов
        </span>
      </div>
      {searchResult.message ? (
        <p class="mt-2 text-sm text-muted">{searchResult.message}</p>
      ) : null}
      {searchResult.candidates.length > 0 ? (
        <div class="mt-3 grid gap-2 lg:grid-cols-2">
          {searchResult.candidates.map((candidate) => (
            <a
              key={candidate.incidentId}
              href={buildIncidentDetailHref(
                new URL(currentUrl),
                candidate.incidentId,
                effectiveEnvironment,
              )}
              class="flex items-center justify-between gap-3 rounded-xl border border-border bg-bg px-3 py-2.5 text-sm text-text transition-colors hover:bg-surface-alt"
            >
              <span class="font-medium">{candidate.errorCode}</span>
              <span class="text-xs text-muted">{candidate.occurredAt}</span>
            </a>
          ))}
        </div>
      ) : null}
    </section>
  ),
);
