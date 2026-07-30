import { component$ } from "@qwik.dev/core";
import {
  buildIncidentQueueDerivedState,
  type IncidentQueueFilters,
} from "../admin-incidents.route-helpers";
import type { AdminIncidentList, AdminIncidentSearch } from "../types";
import { AdminIncidentQueueFilters } from "./AdminIncidentQueueFilters";
import { AdminIncidentQueueList } from "./AdminIncidentQueueList";
import { AdminIncidentQueueToolbar } from "./AdminIncidentQueueToolbar";
import { AdminIncidentSearchResult } from "./AdminIncidentSearchResult";

interface AdminIncidentQueueOverviewProps {
  currentUrl: string;
  incidents: AdminIncidentList;
  searchResult: AdminIncidentSearch | null;
  filters: IncidentQueueFilters;
}

export const AdminIncidentQueueOverview = component$(
  ({
    currentUrl,
    incidents,
    searchResult,
    filters,
  }: AdminIncidentQueueOverviewProps) => {
    const state = buildIncidentQueueDerivedState(incidents, filters);

    return (
      <div class="space-y-4 md:space-y-5">
        <AdminIncidentQueueToolbar
          currentUrl={currentUrl}
          incidents={incidents}
          filters={filters}
          state={state}
        />
        <AdminIncidentQueueFilters
          currentUrl={currentUrl}
          incidents={incidents}
          filters={filters}
          state={state}
        />
        {searchResult ? (
          <AdminIncidentSearchResult
            currentUrl={currentUrl}
            searchResult={searchResult}
            effectiveEnvironment={state.effectiveEnvironment}
          />
        ) : null}
        <AdminIncidentQueueList
          currentUrl={currentUrl}
          incidents={incidents}
          state={state}
        />
      </div>
    );
  },
);
