import { type DocumentHead } from "@qwik.dev/router";
import { ProfilePage } from "./profile-page";

export { useMyProfile } from "./loader";
export { useUpsertProfile } from "./action";

export default ProfilePage;

export const head: DocumentHead = {
  title: "Профиль — Jitsi Portal",
  meta: [
    {
      name: "description",
      content: "Управление профилем пользователя",
    },
  ],
};
