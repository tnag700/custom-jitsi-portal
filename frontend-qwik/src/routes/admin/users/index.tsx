import { component$ } from "@qwik.dev/core";
import { Form, type DocumentHead } from "@qwik.dev/router";
import { ApiErrorAlert } from "~/lib/shared";
import {
  useAdminUsers,
  useUpdateAdminUserProfile,
} from "./route-handlers";

export { useAdminUsers, useUpdateAdminUserProfile } from "./route-handlers";

export default component$(() => {
  const loader = useAdminUsers();
  const updateAction = useUpdateAdminUserProfile();
  const actionError =
    updateAction.value && "error" in updateAction.value
      ? updateAction.value.error
      : null;
  const updatedSubjectId =
    updateAction.value &&
    "profile" in updateAction.value &&
    updateAction.value.profile
      ? updateAction.value.profile.subjectId
      : null;

  return (
    <div class="space-y-4">
      <section class="rounded-3xl border border-border bg-surface p-5 shadow-sm">
        <div class="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <p class="text-xs uppercase tracking-[0.2em] text-muted">
              Управление профилями
            </p>
            <h2 class="mt-1 text-xl font-semibold text-text">
              Данные пользователей
            </h2>
            <p class="mt-1 max-w-2xl text-sm text-muted">
              Изменяйте ФИО, учреждение и должность пользователей своего tenant.
              Роли и учётные данные остаются под управлением Keycloak.
            </p>
          </div>
          <form method="get" class="flex w-full gap-2 lg:max-w-md">
            <label class="sr-only" for="admin-user-search">
              Поиск по ФИО
            </label>
            <input
              id="admin-user-search"
              name="q"
              type="search"
              value={loader.value.query}
              placeholder="Поиск по ФИО"
              class="min-w-0 flex-1 rounded-xl border border-border bg-bg px-3 py-2.5 text-sm text-text"
            />
            <button
              type="submit"
              class="rounded-xl bg-text px-4 py-2.5 text-sm font-medium text-bg"
            >
              Найти
            </button>
          </form>
        </div>
      </section>

      {loader.value.loadError ? (
        <ApiErrorAlert
          title={loader.value.loadError.title}
          message={loader.value.loadError.detail}
          errorCode={loader.value.loadError.errorCode}
          traceId={loader.value.loadError.traceId}
        />
      ) : null}

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

      {loader.value.users.length === 0 && !loader.value.loadError ? (
        <div class="rounded-3xl border border-dashed border-border bg-surface p-8 text-center text-sm text-muted">
          Пользователи не найдены.
        </div>
      ) : (
        <div class="grid gap-3">
          {loader.value.users.map((profile) => (
            <details
              key={profile.subjectId}
              class="group rounded-3xl border border-border bg-surface p-5 shadow-sm"
              open={updatedSubjectId === profile.subjectId}
            >
              <summary class="cursor-pointer list-none">
                <div class="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                  <div>
                    <p class="font-semibold text-text">{profile.fullName}</p>
                    <p class="mt-1 text-sm text-muted">
                      {profile.organization} · {profile.position}
                    </p>
                  </div>
                  <span class="text-sm font-medium text-primary">
                    Редактировать
                  </span>
                </div>
              </summary>

              <Form
                action={updateAction}
                class="mt-5 border-t border-border pt-5"
              >
                <input
                  type="hidden"
                  name="subjectId"
                  value={profile.subjectId}
                />
                <div class="grid gap-4 lg:grid-cols-3">
                  <label class="grid gap-1 text-sm font-medium text-text">
                    ФИО
                    <input
                      name="fullName"
                      value={profile.fullName}
                      required
                      minLength={2}
                      maxLength={500}
                      class="rounded-xl border border-border bg-bg px-3 py-2.5 font-normal"
                    />
                  </label>
                  <label class="grid gap-1 text-sm font-medium text-text">
                    Учреждение
                    <input
                      name="organization"
                      value={profile.organization}
                      required
                      minLength={2}
                      maxLength={500}
                      class="rounded-xl border border-border bg-bg px-3 py-2.5 font-normal"
                    />
                  </label>
                  <label class="grid gap-1 text-sm font-medium text-text">
                    Должность
                    <input
                      name="position"
                      value={profile.position}
                      required
                      minLength={2}
                      maxLength={500}
                      class="rounded-xl border border-border bg-bg px-3 py-2.5 font-normal"
                    />
                  </label>
                </div>
                <div class="mt-4 flex flex-col gap-2 border-t border-border pt-4 sm:flex-row sm:items-center sm:justify-between">
                  <p class="text-xs text-muted">
                    Subject ID: {profile.subjectId}
                  </p>
                  <button
                    type="submit"
                    disabled={updateAction.isRunning}
                    class="rounded-xl bg-primary px-4 py-2.5 text-sm font-semibold text-primary-foreground disabled:opacity-50"
                  >
                    {updateAction.isRunning
                      ? "Сохранение..."
                      : "Сохранить изменения"}
                  </button>
                </div>
              </Form>
            </details>
          ))}
        </div>
      )}
    </div>
  );
});

export const head: DocumentHead = {
  title: "Пользователи — Jitsi Portal",
  meta: [
    {
      name: "description",
      content: "Административное редактирование профилей пользователей.",
    },
  ],
};
