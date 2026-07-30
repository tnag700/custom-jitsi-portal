import { component$ } from "@qwik.dev/core";
import { useLocation, type DocumentHead } from "@qwik.dev/router";
import { AdminRoleHistoryOverview } from "~/lib/domains/admin";
import { ApiErrorAlert } from "~/lib/shared";
import { useAdminRoleHistory } from "./loader";

export { useAdminRoleHistory } from "./loader";

export default component$(() => {
  const loader = useAdminRoleHistory();
  const location = useLocation();
  const { history, loadError, hasPrimaryFilter, filters } = loader.value;

  if (loadError) {
    return (
      <ApiErrorAlert
        title={loadError.title}
        message={loadError.detail}
        errorCode={loadError.errorCode}
        traceId={loadError.traceId}
      />
    );
  }

  return (
    <AdminRoleHistoryOverview
      currentUrl={location.url.href}
      history={history}
      hasPrimaryFilter={hasPrimaryFilter}
      filters={filters}
    />
  );
});

export const head: DocumentHead = {
  title: "История ролей — Jitsi Portal",
  meta: [
    {
      name: "description",
      content:
        "Аудит изменений ролей и назначений по пользователю, комнате и встрече.",
    },
  ],
};
