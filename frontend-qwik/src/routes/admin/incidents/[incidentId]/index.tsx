import type { DocumentHead } from "@qwik.dev/router";

export {
  useCreateIncidentTicket,
  useIncidentDetail,
  useUpdateIncidentCoordination,
} from "./route-handlers";
export { default } from "./incident-detail-page";

export const head: DocumentHead = {
  title: "Расследование инцидента — Jitsi Portal",
  meta: [
    {
      name: "description",
      content:
        "Карточка расследования инцидента с диагностикой, связанным контекстом, координацией и тикетами.",
    },
  ],
};
