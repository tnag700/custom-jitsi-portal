import { component$ } from "@qwik.dev/core";
import { Form, routeAction$, routeLoader$, zod$, z, useLocation } from "@qwik.dev/router";
import { ApiErrorAlert } from "~/lib/shared";
import {
  InviteExchangeError,
  exchangeInvite,
  exchangeInviteSchema,
  validateInviteToken,
  type InviteErrorPayload,
} from "~/lib/domains/invites";

const DEFAULT_SERVER_API_URL = "http://localhost:8080/api/v1";

export const useInviteTokenLoader = routeLoader$(async ({ params, sharedMap }) => {
  const apiUrl = (sharedMap.get("apiUrl") as string) || DEFAULT_SERVER_API_URL;
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
});

export const useExchangeInviteAction = routeAction$(
  async (data, { sharedMap, redirect, fail }) => {
    const apiUrl = (sharedMap.get("apiUrl") as string) || DEFAULT_SERVER_API_URL;

    try {
      const response = await exchangeInvite(apiUrl, data.inviteToken, data.displayName);
      throw redirect(302, response.joinUrl);
    } catch (error) {
      if (error instanceof InviteExchangeError) {
        return fail(400, { error: error.payload });
      }
      throw error;
    }
  },
  zod$(exchangeInviteSchema.extend({ inviteToken: z.string().min(1) })),
);

const ERROR_MESSAGES: Record<string, string> = {
  INVITE_EXPIRED: "Срок действия истек",
  INVITE_EXHAUSTED: "Лимит использований исчерпан",
  INVITE_REVOKED: "Инвайт отозван",
  INVITE_NOT_FOUND: "Инвайт не найден",
};

export default component$(() => {
  const tokenData = useInviteTokenLoader();
  const exchangeAction = useExchangeInviteAction();
  const loc = useLocation();

  const actionError: InviteErrorPayload | undefined =
    exchangeAction.value && "error" in exchangeAction.value
      ? (exchangeAction.value as { error: InviteErrorPayload }).error
      : undefined;

  const inviteError = actionError ?? tokenData.value.validationError;

  const errorText = inviteError ? ERROR_MESSAGES[inviteError.errorCode] ?? inviteError.detail : null;

  return (
    <div class="mx-auto max-w-lg rounded border border-border bg-surface p-6">
      <h1 class="mb-2 text-2xl font-bold text-text">Вход по инвайту</h1>
      <p class="mb-6 text-sm text-muted">Введите имя и присоединяйтесь к встрече.</p>

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
          <input type="hidden" name="inviteToken" value={tokenData.value.inviteToken} />
          <div class="mb-4">
            <label class="mb-1 block text-sm font-medium text-text" for="display-name">Имя</label>
            <input
              id="display-name"
              type="text"
              name="displayName"
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
        <p class="text-sm text-muted">Этот инвайт недействителен. Запросите новый у администратора встречи.</p>
      )}

      {loc.url.searchParams.get("error") && (
        <p class="mt-4 text-xs text-muted">Параметр ошибки: {loc.url.searchParams.get("error")}</p>
      )}
    </div>
  );
});
