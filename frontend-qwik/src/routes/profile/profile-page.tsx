import { component$, useTask$ } from "@qwik.dev/core";
import {
  ProfileForm,
  type ProfileErrorPayload,
  type UserProfileResponse,
} from "~/lib/domains/profile";
import { AppToast, useAppToast } from "~/lib/shared/components";
import { useUpsertProfile } from "./action";
import { useMyProfile } from "./loader";

export const ProfilePage = component$(() => {
  const profileData = useMyProfile();
  const upsertAction = useUpsertProfile();
  const { toast, showToast$, clearToast$ } = useAppToast();

  useTask$(async ({ track }) => {
    const result = track(() => upsertAction.value);
    if (!result) return;
    if ("success" in result && result.success) {
      await showToast$({ message: "Профиль сохранён", tone: "success" });
    }
  });

  const loaderData = profileData.value;
  const currentProfile =
    upsertAction.value &&
    "success" in upsertAction.value &&
    upsertAction.value.success
      ? (upsertAction.value.profile as UserProfileResponse)
      : loaderData.profile;

  const serverError: ProfileErrorPayload | null =
    upsertAction.value && "error" in upsertAction.value
      ? (upsertAction.value as { error: ProfileErrorPayload }).error
      : loaderData.loadError;

  return (
    <div class="mx-auto max-w-5xl space-y-6 py-4 md:py-6">
      <header class="rounded-3xl border border-border bg-surface px-5 py-5 shadow-sm md:px-7 md:py-6">
        <p class="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
          Личный кабинет
        </p>
        <h1 class="mt-2 text-2xl font-semibold tracking-tight text-text md:text-3xl">
          Профиль пользователя
        </h1>
        <p class="mt-2 max-w-2xl text-sm leading-6 text-muted">
          Эти данные отображаются организаторам и участникам встреч. Системные
          роли и права доступа здесь не изменяются.
        </p>
      </header>

      <div class="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_18rem]">
        <ProfileForm
          profile={currentProfile}
          isFirstRun={loaderData.isFirstRun && !currentProfile}
          isSubmitting={upsertAction.isRunning}
          serverError={serverError}
          action={upsertAction}
        />

        <aside class="rounded-3xl border border-border bg-surface p-5 shadow-sm">
          <h2 class="text-base font-semibold text-text">
            Где используются данные
          </h2>
          <ul class="mt-3 space-y-3 text-sm leading-5 text-muted">
            <li>ФИО — в списках участников и журнале действий.</li>
            <li>Учреждение — для поиска коллег внутри организации.</li>
            <li>Должность — как дополнительный контекст во встрече.</li>
          </ul>
          <p class="mt-4 rounded-2xl bg-primary-soft p-3 text-sm leading-5 text-primary">
            Изменить системную роль через профиль нельзя — это делает только
            уполномоченный администратор.
          </p>
        </aside>
      </div>

      <AppToast toast={toast.value} onDismiss$={clearToast$} />
    </div>
  );
});
