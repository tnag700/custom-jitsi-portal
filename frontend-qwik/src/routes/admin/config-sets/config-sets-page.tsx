import { component$ } from "@qwik.dev/core";
import { useLocation } from "@qwik.dev/router";
import { AdminConfigSetsOverview } from "~/lib/domains/admin";
import { ApiErrorAlert } from "~/lib/shared";
import { PageNavigation } from "~/lib/shared/components/PageNavigation";
import {
  getAdminConfigActionError,
  getAdminConfigActionOperation,
  useAdminConfigSets,
  useCompatibilityCheck,
  useRollbackConfigSet,
  useRolloutConfigSet,
  useSaveConfigSet,
} from "./route-handlers";

export default component$(() => {
  const loader = useAdminConfigSets();
  const saveAction = useSaveConfigSet();
  const compatibilityAction = useCompatibilityCheck();
  const rolloutAction = useRolloutConfigSet();
  const rollbackAction = useRollbackConfigSet();
  const location = useLocation();
  const { items, selectedConfig, capability, loadError, filters, page, totalPages, totalElements } =
    loader.value;

  if (loadError) {
    return (
      <ApiErrorAlert
        title={loadError.title ?? "Ошибка"}
        message={loadError.detail ?? "Неизвестная ошибка"}
        errorCode={loadError.errorCode ?? "ADMIN_CONFIG_UNKNOWN"}
        traceId={loadError.traceId}
      />
    );
  }

  const activeOperation =
    getAdminConfigActionOperation(saveAction.value) ??
    getAdminConfigActionOperation(compatibilityAction.value) ??
    getAdminConfigActionOperation(rolloutAction.value) ??
    getAdminConfigActionOperation(rollbackAction.value);
  const activeError =
    getAdminConfigActionError(saveAction.value) ??
    getAdminConfigActionError(compatibilityAction.value) ??
    getAdminConfigActionError(rolloutAction.value) ??
    getAdminConfigActionError(rollbackAction.value);

  return (
    <>
      <AdminConfigSetsOverview
        currentUrl={location.url.href}
        items={items}
        selectedConfig={selectedConfig}
        capability={capability}
        filters={filters}
        saveAction={saveAction}
        compatibilityAction={compatibilityAction}
        rolloutAction={rolloutAction}
        rollbackAction={rollbackAction}
        activeOperation={activeOperation}
        activeError={activeError}
      />
      <PageNavigation
        currentUrl={location.url.href}
        parameter="configSetsPage"
        page={page}
        totalPages={totalPages}
        label="Страницы конфигураций"
      />
      <p class="text-sm text-muted">Всего по фильтру: {totalElements}</p>
    </>
  );
});
