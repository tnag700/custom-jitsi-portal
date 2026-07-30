import type { DocumentHead } from "@qwik.dev/router";

export {
  useAdminConfigSets,
  useCompatibilityCheck,
  useRollbackConfigSet,
  useRolloutConfigSet,
  useSaveConfigSet,
} from "./route-handlers";
export { default } from "./config-sets-page";

export const head: DocumentHead = {
  title: "Конфиг-наборы администратора — Jitsi Portal",
  meta: [
    {
      name: "description",
      content:
        "Управление конфиг-наборами, проверкой совместимости, развёртыванием и откатом из административной консоли.",
    },
  ],
};
