import type { AuthErrorPayload } from "./types";

export function mapAuthErrorCodeToPayload(errorCode: string): AuthErrorPayload {
  if (errorCode === "ACCESS_DENIED") {
    return {
      title: "Доступ запрещен",
      reason: "У текущего пользователя недостаточно прав для входа.",
      actions: "Обратитесь к администратору.",
      errorCode,
    };
  }

  if (errorCode === "AUTH_SERVICE_UNAVAILABLE") {
    return {
      title: "Сервис аутентификации недоступен",
      reason: "В данный момент профиль пользователя не может быть загружен.",
      actions: "Повторите попытку позже.",
      errorCode,
    };
  }

  if (errorCode === "AUTH_REQUIRED" || !errorCode) {
    return {
      title: "Требуется вход",
      reason: "Сессия не найдена или истекла.",
      actions: "Выполните вход через SSO.",
      errorCode: "AUTH_REQUIRED",
    };
  }

  return {
    title: "Ошибка входа",
    reason: "Произошла ошибка аутентификации. Повторите попытку входа.",
    actions: "Повторите вход через SSO. Если проблема повторится, обратитесь в поддержку.",
    errorCode,
  };
}
