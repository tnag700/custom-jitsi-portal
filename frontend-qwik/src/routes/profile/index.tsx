import { component$, useSignal, useTask$ } from "@qwik.dev/core";
import {
  routeAction$,
  routeLoader$,
  zod$,
  type DocumentHead,
} from "@qwik.dev/router";
import {
  fetchMyProfile,
  profileFormSchema,
  upsertMyProfile,
  ProfileServiceError,
  ProfileForm,
  type ProfileErrorPayload,
  type UserProfileResponse,
} from "~/lib/domains/profile";

interface ProfileLoaderData {
  profile: UserProfileResponse | null;
  isFirstRun: boolean;
  loadError: ProfileErrorPayload | null;
}

export const useMyProfile = routeLoader$(async ({ sharedMap, cookie, redirect }) => {
  const apiUrl = sharedMap.get("apiUrl") as string;
  const sessionCookie = cookie.get("JSESSIONID")?.value ?? "";
  try {
    const profile = await fetchMyProfile(sessionCookie, apiUrl);
    if (profile === null) {
      return { profile: null, isFirstRun: true, loadError: null } satisfies ProfileLoaderData;
    }
    return { profile, isFirstRun: false, loadError: null } satisfies ProfileLoaderData;
  } catch (error) {
    if (error instanceof ProfileServiceError) {
      if (error.payload.errorCode === "AUTH_REQUIRED") {
        throw redirect(302, "/auth");
      }
      return {
        profile: null,
        isFirstRun: false,
        loadError: error.payload,
      } satisfies ProfileLoaderData;
    }
    return {
      profile: null,
      isFirstRun: false,
      loadError: {
        title: "Ошибка загрузки",
        detail: "Не удалось загрузить профиль.",
        errorCode: "PROFILE_SERVICE_UNAVAILABLE",
      },
    } satisfies ProfileLoaderData;
  }
});

export const useUpsertProfile = routeAction$(
  async (data, { sharedMap, cookie, redirect, fail }) => {
    const apiUrl = sharedMap.get("apiUrl") as string;
    const sessionCookie = cookie.get("JSESSIONID")?.value ?? "";
    const csrfToken = cookie.get("XSRF-TOKEN")?.value ?? "";
    try {
      const profile = await upsertMyProfile(sessionCookie, apiUrl, csrfToken, data);
      return { success: true as const, profile };
    } catch (error) {
      if (error instanceof ProfileServiceError) {
        if (error.payload.errorCode === "AUTH_REQUIRED") {
          throw redirect(302, "/auth");
        }
        return fail(400, { error: error.payload });
      }
      return fail(500, {
        error: {
          title: "Ошибка",
          detail: "Неизвестная ошибка",
          errorCode: "PROFILE_UNKNOWN",
        },
      });
    }
  },
  zod$(
    profileFormSchema,
  ),
);

export default component$(() => {
  const profileData = useMyProfile();
  const upsertAction = useUpsertProfile();
  const successMessage = useSignal<string | null>(null);

  useTask$(({ track, cleanup }) => {
    const result = track(() => upsertAction.value);
    if (!result) return;
    if ("success" in result && result.success) {
      successMessage.value = "Профиль сохранён";
      const timer = setTimeout(() => {
        successMessage.value = null;
      }, 3000);
      cleanup(() => clearTimeout(timer));
    }
  });

  const loaderData = profileData.value;
  const currentProfile =
    upsertAction.value && "success" in upsertAction.value && upsertAction.value.success
      ? (upsertAction.value.profile as UserProfileResponse)
      : loaderData.profile;

  const serverError: ProfileErrorPayload | null =
    upsertAction.value && "error" in upsertAction.value
      ? (upsertAction.value as { error: ProfileErrorPayload }).error
      : loaderData.loadError;

  return (
    <div class="mx-auto max-w-lg py-6">
      <ProfileForm
        profile={currentProfile}
        isFirstRun={loaderData.isFirstRun && !currentProfile}
        isSubmitting={upsertAction.isRunning}
        serverError={serverError}
        successMessage={successMessage.value}
        action={upsertAction}
      />
    </div>
  );
});

export const head: DocumentHead = {
  title: "Профиль — Jitsi Portal",
  meta: [
    {
      name: "description",
      content: "Управление профилем пользователя",
    },
  ],
};
