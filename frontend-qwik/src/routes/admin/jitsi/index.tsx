import { component$ } from "@qwik.dev/core";
import {
  routeLoader$,
  type DocumentHead,
  type RequestHandler,
} from "@qwik.dev/router";
import type { SafeUserProfile } from "~/lib/domains/auth";
import {
  fetchJoinReadiness,
  type JoinReadinessPayload,
} from "~/lib/domains/join";
import { hasPlatformAdminAccess } from "~/lib/shared/security";
import { buildServerRequestContext } from "~/lib/shared/routes/server-handlers";

interface JitsiAccessState {
  readiness: JoinReadinessPayload | null;
  error: string | null;
}

export const onRequest: RequestHandler = ({ sharedMap, redirect }) => {
  const user = (sharedMap.get("user") as SafeUserProfile | null) ?? null;
  if (!user || !hasPlatformAdminAccess(user.claims)) {
    throw redirect(302, "/admin");
  }
};

export const useJitsiAccessState = routeLoader$(
  async ({ sharedMap, cookie }): Promise<JitsiAccessState> => {
    try {
      return {
        readiness: await fetchJoinReadiness(
          buildServerRequestContext({ sharedMap, cookie }),
        ),
        error: null,
      };
    } catch {
      return {
        readiness: null,
        error:
          "Не удалось получить состояние Jitsi. Проверьте backend и повторите запрос.",
      };
    }
  },
);

export default component$(() => {
  const state = useJitsiAccessState();
  const readiness = state.value.readiness;
  const ready = readiness?.status === "ready";

  return (
    <section class="space-y-5">
      <div class="rounded-3xl border border-border bg-surface p-5 shadow-sm">
        <div class="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <p class="text-xs uppercase tracking-[0.2em] text-muted">
              Только для администратора платформы
            </p>
            <h2 class="mt-1 text-xl font-semibold text-text">
              Контур доступа Jitsi
            </h2>
            <p class="mt-2 max-w-3xl text-sm text-muted">
              Публичная стартовая страница Jitsi закрыта. Пользователи входят
              только в назначенную встречу по короткоживущему JWT, выданному
              порталом, а после выхода возвращаются в личный кабинет.
            </p>
          </div>
          <span
            class={[
              "shrink-0 rounded-full px-3 py-1 text-xs font-semibold",
              ready
                ? "bg-success/12 text-success"
                : "bg-warning/12 text-warning",
            ]}
          >
            {ready ? "Контур готов" : "Нужна проверка"}
          </span>
        </div>
      </div>

      {state.value.error ? (
        <div class="rounded-2xl border border-danger/30 bg-danger/10 p-4 text-sm text-danger">
          {state.value.error}
        </div>
      ) : (
        <div class="grid gap-4 lg:grid-cols-3">
          <article class="rounded-2xl border border-border bg-surface p-4">
            <h3 class="font-semibold text-text">Стартовая страница</h3>
            <p class="mt-2 text-sm text-muted">
              Прямое открытие Jitsi переводит пользователя в личный кабинет.
              Создание произвольных комнат через welcome-page отключено.
            </p>
          </article>
          <article class="rounded-2xl border border-border bg-surface p-4">
            <h3 class="font-semibold text-text">Вход во встречу</h3>
            <p class="mt-2 text-sm text-muted">
              Разрешён только URL комнаты с JWT. Пустые токены и гостевой вход
              запрещены на Jitsi и Prosody.
            </p>
          </article>
          <article class="rounded-2xl border border-border bg-surface p-4">
            <h3 class="font-semibold text-text">Завершение</h3>
            <p class="mt-2 text-sm text-muted">
              Экран завершения не показывается: браузер сразу возвращается на
              главную страницу портала.
            </p>
          </article>
        </div>
      )}

      {readiness && (
        <div class="rounded-2xl border border-border bg-surface p-4 text-sm">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p class="font-semibold text-text">Проверка backend</p>
              <p class="mt-1 text-muted">
                Статус: {readiness.status}; проверено: {readiness.checkedAt}
              </p>
            </div>
            {readiness.publicJoinUrl && (
              <span class="break-all rounded-xl bg-surface-alt px-3 py-2 text-xs text-muted">
                {readiness.publicJoinUrl}
              </span>
            )}
          </div>
        </div>
      )}

      <div class="flex flex-wrap gap-3">
        <a
          href="/meetings"
          class="rounded-xl bg-primary px-4 py-2 text-sm font-semibold text-white hover:bg-primary-hover"
        >
          Управление встречами
        </a>
        <a
          href="/"
          class="rounded-xl border border-border bg-surface px-4 py-2 text-sm font-medium text-text hover:bg-surface-alt"
        >
          В личный кабинет
        </a>
      </div>
    </section>
  );
});

export const head: DocumentHead = {
  title: "Доступ Jitsi — Администрирование",
  meta: [
    {
      name: "description",
      content: "Административная проверка закрытого контура доступа Jitsi",
    },
  ],
};
