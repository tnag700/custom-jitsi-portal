import { component$ } from "@qwik.dev/core";
import {
  Form,
  routeAction$,
  routeLoader$,
  type DocumentHead,
  type RequestHandler,
  zod$,
  z,
} from "@qwik.dev/router";
import { ApiErrorAlert } from "~/lib/shared";
import {
  InviteExchangeError,
  exchangeInvite,
  exchangeInviteSchema,
  validateInviteToken,
  type InviteErrorPayload,
} from "~/lib/domains/invites";
import {
  fetchJoinReadiness,
  resolveExpectedJoinOrigin,
  validateJoinRedirect,
} from "~/lib/domains/join";

const DEFAULT_SERVER_API_URL = "http://localhost:8080/api/v1";

export const onRequest: RequestHandler = ({ headers }) => {
  headers.set("Cache-Control", "private, no-store, max-age=0");
  headers.set("X-Robots-Tag", "noindex, nofollow, noarchive");
  headers.set("Referrer-Policy", "no-referrer");
};

export const useInviteTokenLoader = routeLoader$(
  async ({ params, sharedMap }) => {
    const apiUrl =
      (sharedMap.get("apiUrl") as string) || DEFAULT_SERVER_API_URL;
    const inviteToken = params.inviteToken;

    try {
      const validation = await validateInviteToken(apiUrl, inviteToken);
      return {
        inviteToken,
        isValid: validation.valid,
        validationError: undefined as InviteErrorPayload | undefined,
      };
    } catch (error) {
      if (error instanceof InviteExchangeError) {
        return {
          inviteToken,
          isValid: false,
          validationError: error.payload,
        };
      }
      throw error;
    }
  },
);

export const useExchangeInviteAction = routeAction$(
  async (data, { sharedMap, redirect, fail }) => {
    const apiUrl =
      (sharedMap.get("apiUrl") as string) || DEFAULT_SERVER_API_URL;

    const readiness = await fetchJoinReadiness(apiUrl).catch(() => null);
    if (!readiness) {
      return fail(502, {
        error: {
          title: "Не удалось проверить адрес конференции",
          detail: "Сервис готовности Jitsi временно недоступен.",
          errorCode: "JOIN_READINESS_UNAVAILABLE",
        },
      });
    }

    try {
      const expectedOrigin = resolveExpectedJoinOrigin(readiness.publicJoinUrl);
      if (!expectedOrigin) {
        const rejected = validateJoinRedirect({}, null);
        return fail(502, { error: rejected.error });
      }

      const response = await exchangeInvite(
        apiUrl,
        data.inviteToken,
        data.displayName,
      );
      const validated = validateJoinRedirect(response, expectedOrigin);
      if (validated.error || !validated.joinUrl) {
        return fail(502, { error: validated.error });
      }
      throw redirect(302, validated.joinUrl);
    } catch (error) {
      if (error instanceof InviteExchangeError) {
        const status =
          error.payload.errorCode === "INVITE_RESPONSE_INVALID" ? 502 : 400;
        return fail(status, { error: error.payload });
      }
      throw error;
    }
  },
  zod$(exchangeInviteSchema.extend({ inviteToken: z.string().min(1) })),
);

export const head: DocumentHead = {
  title: "Вход по приглашению — Jitsi",
  meta: [
    {
      name: "robots",
      content: "noindex,nofollow,noarchive",
    },
  ],
};

const ERROR_MESSAGES: Record<string, string> = {
  INVITE_EXPIRED: "Срок действия истек",
  INVITE_EXHAUSTED: "Лимит использований исчерпан",
  INVITE_REVOKED: "Инвайт отозван",
  INVITE_NOT_FOUND: "Инвайт не найден",
  GUEST_ACCESS_DISABLED: "Организатор отключил гостевой доступ",
};

export default component$(() => {
  const tokenData = useInviteTokenLoader();
  const exchangeAction = useExchangeInviteAction();

  const actionError: InviteErrorPayload | undefined =
    exchangeAction.value && "error" in exchangeAction.value
      ? (exchangeAction.value as { error: InviteErrorPayload }).error
      : undefined;

  const inviteError = actionError ?? tokenData.value.validationError;

  const errorText = inviteError
    ? (ERROR_MESSAGES[inviteError.errorCode] ?? inviteError.detail)
    : null;

  return (
    <div class="mx-auto max-w-lg rounded border border-border bg-surface p-6">
      <h1 class="mb-2 text-2xl font-bold text-text">Вход по инвайту</h1>
      <p class="mb-6 text-sm text-muted">
        Введите имя и присоединяйтесь к встрече.
      </p>

      {errorText && (
        <div class="mb-4" role="alert">
          <ApiErrorAlert
            title="Ошибка инвайта"
            message={errorText}
            errorCode={inviteError?.errorCode}
            traceId={inviteError?.traceId}
          />
        </div>
      )}

      {tokenData.value.isValid ? (
        <Form action={exchangeAction}>
          <input
            type="hidden"
            name="inviteToken"
            value={tokenData.value.inviteToken}
          />
          <div class="mb-4">
            <label
              class="mb-1 block text-sm font-medium text-text"
              for="display-name"
            >
              Имя
            </label>
            <input
              id="display-name"
              type="text"
              name="displayName"
              minLength={2}
              maxLength={80}
              required
              autoComplete="name"
              placeholder="Как к вам обращаться"
              class="w-full rounded border border-border bg-bg px-3 py-2 text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
            />
          </div>
          <button
            type="submit"
            class="w-full rounded bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 disabled:opacity-50"
            disabled={exchangeAction.isRunning}
          >
            {exchangeAction.isRunning ? "Входим..." : "Войти во встречу"}
          </button>
        </Form>
      ) : (
        <p class="text-sm text-muted">
          Этот инвайт недействителен. Запросите новый у администратора встречи.
        </p>
      )}
    </div>
  );
});
