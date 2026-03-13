import { $, component$, useSignal } from "@qwik.dev/core";
import { Form } from "@qwik.dev/router";
import { ApiErrorAlert } from "~/lib/shared";
import { profileFormSchema } from "../profile.schema";
import type { ProfileErrorPayload, UserProfileResponse } from "../types";

interface ProfileFormProps {
  profile: UserProfileResponse | null;
  isFirstRun: boolean;
  isSubmitting: boolean;
  serverError: ProfileErrorPayload | null;
  action: unknown;
}

export const ProfileForm = component$<ProfileFormProps>(
  ({ profile, isFirstRun, isSubmitting, serverError, action }) => {
    const fullNameValue = useSignal(profile?.fullName ?? "");
    const organizationValue = useSignal(profile?.organization ?? "");
    const positionValue = useSignal(profile?.position ?? "");
    const validationErrors = useSignal<Record<string, string>>({});

    const validateField$ = $((field: string, value: string) => {
      const result = profileFormSchema.shape[field as keyof typeof profileFormSchema.shape].safeParse(value);
      const errors = { ...validationErrors.value };
      if (!result.success) {
        errors[field] = result.error.issues[0].message;
      } else {
        delete errors[field];
      }
      validationErrors.value = errors;
    });

    const handleSubmit$ = $((event: Event) => {
      const data = {
        fullName: fullNameValue.value,
        organization: organizationValue.value,
        position: positionValue.value,
      };
      const result = profileFormSchema.safeParse(data);
      if (!result.success) {
        const errors: Record<string, string> = {};
        for (const issue of result.error.issues) {
          const key = issue.path[0];
          if (typeof key === "string") {
            errors[key] = issue.message;
          }
        }
        validationErrors.value = errors;
        event.preventDefault();
        return;
      }
      fullNameValue.value = result.data.fullName;
      organizationValue.value = result.data.organization;
      positionValue.value = result.data.position;
      validationErrors.value = {};
    });

    const errorMessage = serverError
      ? serverError.errorCode === "PROFILE_VALIDATION_FAILED"
        ? `Ошибка валидации профиля: ${serverError.detail}`
        : serverError.detail
      : null;

    return (
      <div class="rounded border border-border bg-surface p-4 md:p-6">
        <h1 class="mb-4 text-xl font-semibold text-text">Профиль</h1>

        {isFirstRun && (
          <div class="mb-4 rounded border border-blue-300 bg-blue-50 p-3 text-sm text-blue-800" role="status">
            Заполните профиль для продолжения работы
          </div>
        )}

        {errorMessage && (
          <div class="mb-4" role="alert" aria-live="polite">
            <ApiErrorAlert
              title="Ошибка операции с профилем"
              message={errorMessage}
              errorCode={serverError?.errorCode}
              traceId={serverError?.traceId}
            />
          </div>
        )}

        <Form action={action as never} onSubmit$={handleSubmit$}>
          <div class="space-y-4">
            <div>
              <label class="mb-1 block text-sm font-medium text-text" for="profile-fullName">
                ФИО *
              </label>
              <input
                id="profile-fullName"
                name="fullName"
                type="text"
                aria-label="ФИО"
                aria-describedby={validationErrors.value.fullName ? "fullName-error" : undefined}
                class="w-full rounded border border-border bg-bg px-3 py-2 text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                value={fullNameValue.value}
                onInput$={(_, el) => {
                  fullNameValue.value = el.value;
                }}
                onBlur$={() => validateField$("fullName", fullNameValue.value)}
              />
              {validationErrors.value.fullName && (
                <p id="fullName-error" class="mt-1 text-xs text-red-600">
                  {validationErrors.value.fullName}
                </p>
              )}
            </div>

            <div>
              <label class="mb-1 block text-sm font-medium text-text" for="profile-organization">
                Учреждение *
              </label>
              <input
                id="profile-organization"
                name="organization"
                type="text"
                aria-label="Учреждение"
                aria-describedby={validationErrors.value.organization ? "organization-error" : undefined}
                class="w-full rounded border border-border bg-bg px-3 py-2 text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                value={organizationValue.value}
                onInput$={(_, el) => {
                  organizationValue.value = el.value;
                }}
                onBlur$={() => validateField$("organization", organizationValue.value)}
              />
              {validationErrors.value.organization && (
                <p id="organization-error" class="mt-1 text-xs text-red-600">
                  {validationErrors.value.organization}
                </p>
              )}
            </div>

            <div>
              <label class="mb-1 block text-sm font-medium text-text" for="profile-position">
                Должность *
              </label>
              <input
                id="profile-position"
                name="position"
                type="text"
                aria-label="Должность"
                aria-describedby={validationErrors.value.position ? "position-error" : undefined}
                class="w-full rounded border border-border bg-bg px-3 py-2 text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                value={positionValue.value}
                onInput$={(_, el) => {
                  positionValue.value = el.value;
                }}
                onBlur$={() => validateField$("position", positionValue.value)}
              />
              {validationErrors.value.position && (
                <p id="position-error" class="mt-1 text-xs text-red-600">
                  {validationErrors.value.position}
                </p>
              )}
            </div>
          </div>

          {profile && (
            <details class="mt-6 rounded-lg border border-border bg-bg/60 p-3 text-xs text-muted">
              <summary class="cursor-pointer select-none text-sm font-medium text-text">
                Техническая информация профиля
              </summary>
              <div class="mt-3 space-y-1">
                <p>Subject ID: {profile.subjectId}</p>
                <p>Tenant ID: {profile.tenantId}</p>
                <p>Создан: {new Date(profile.createdAt).toLocaleString("ru-RU")}</p>
                <p>Обновлён: {new Date(profile.updatedAt).toLocaleString("ru-RU")}</p>
              </div>
            </details>
          )}

          <div class="mt-6 flex justify-end">
            <button
              type="submit"
              class="rounded bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 disabled:opacity-50"
              disabled={isSubmitting}
            >
              {isSubmitting ? (
                <span class="inline-flex items-center gap-2">
                  <span
                    class="h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white"
                    aria-hidden="true"
                  />
                  <span>Сохранение...</span>
                </span>
              ) : (
                "Сохранить"
              )}
            </button>
          </div>
        </Form>
      </div>
    );
  },
);
