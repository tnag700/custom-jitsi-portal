import { component$ } from "@qwik.dev/core";
import type { DocumentHead } from "@qwik.dev/router";
import { AdminFrameworkVersionsOverview } from "~/lib/domains/admin";
import { ApiErrorAlert } from "~/lib/shared";
import {
  useAdminFrameworkVersions,
  useRefreshFrameworkVersions,
} from "./loader";

export { useAdminFrameworkVersions, useRefreshFrameworkVersions } from "./loader";

export default component$(() => {
  const loader = useAdminFrameworkVersions();
  const refreshAction = useRefreshFrameworkVersions();
  const snapshot =
    refreshAction.value && "snapshot" in refreshAction.value
      ? refreshAction.value.snapshot
      : loader.value.snapshot;
  const actionError =
    refreshAction.value && "error" in refreshAction.value
      ? refreshAction.value.error
      : null;

  if (loader.value.loadError) {
    return (
      <ApiErrorAlert
        title={loader.value.loadError.title}
        message={loader.value.loadError.detail}
        errorCode={loader.value.loadError.errorCode}
        traceId={loader.value.loadError.traceId}
      />
    );
  }

  if (!snapshot) {
    return (
      <ApiErrorAlert
        title="Данные недоступны"
        message="Снимок версий фреймворков пока не сформирован."
        errorCode="FRAMEWORK_VERSION_SNAPSHOT_EMPTY"
      />
    );
  }

  return (
    <div class="space-y-3">
      {actionError ? (
        <ApiErrorAlert
          title={actionError.title}
          message={actionError.detail}
          errorCode={actionError.errorCode}
          traceId={
            "traceId" in actionError &&
            typeof actionError.traceId === "string"
              ? actionError.traceId
              : undefined
          }
        />
      ) : null}
      <AdminFrameworkVersionsOverview
        snapshot={snapshot}
        canRefresh={loader.value.canRefresh}
        refreshing={refreshAction.isRunning}
        onRefresh$={() => refreshAction.submit({})}
      />
    </div>
  );
});

export const head: DocumentHead = {
  title: "Версии и CVE — Jitsi Portal",
  meta: [
    {
      name: "description",
      content:
        "Контроль версий серверных и клиентских фреймворков и известных уязвимостей.",
    },
  ],
};
