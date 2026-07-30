import { type DocumentHead } from "@qwik.dev/router";
import { JoinPage } from "./join-page";

export {
  useJoinReadiness,
  useJoinRuntimeConfig,
  useUpcomingMeetings,
} from "./join-loaders";
export { useJoinMeeting } from "./join-action";

export default JoinPage;

export const head: DocumentHead = {
  title: "Личный кабинет — Jitsi",
  meta: [
    {
      name: "description",
      content: "Личный кабинет — список предстоящих встреч",
    },
  ],
};
