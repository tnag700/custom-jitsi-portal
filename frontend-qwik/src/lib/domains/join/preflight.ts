import type { JoinErrorPayload, JoinReadinessCheck, JoinReadinessPayload } from "./types";

export type PreflightScope = "full" | "system" | "media";

export interface JoinPreflightReport {
  status: "checking" | "ready" | "degraded" | "blocked";
  checkedAt: string | null;
  traceId: string | null;
  publicJoinUrl: string | null;
  systemChecks: JoinReadinessCheck[];
  mediaChecks: JoinReadinessCheck[];
}

interface BrowserPreflightOptions {
  publicJoinUrl: string | null;
  scope: PreflightScope;
}

const CHECK_TIMEOUT_MS = 3000;

export function createInitialPreflightReport(snapshot: JoinReadinessPayload): JoinPreflightReport {
  return {
    status: snapshot.status,
    checkedAt: snapshot.checkedAt,
    traceId: snapshot.traceId ?? null,
    publicJoinUrl: snapshot.publicJoinUrl ?? null,
    systemChecks: snapshot.systemChecks,
    mediaChecks: [
      {
        key: "media-pending",
        status: "timeout",
        headline: "Медиа-проверки ещё не запускались",
        reason: "Браузерная диагностика выполнится после загрузки страницы или по кнопке обновления.",
        actions: ["Разрешить доступ к камере и микрофону при запросе браузера"],
        errorCode: "PREFLIGHT_NOT_RUN",
        blocking: false,
      },
    ],
  };
}

export function resolveRetryPreflightScope(errorCode?: string | null): PreflightScope {
  if (!errorCode) {
    return "full";
  }

  if (/MEDIA|MIC|CAM|DEVICE|PERMISSION/i.test(errorCode)) {
    return "media";
  }

  if (/AUTH|ACCESS|ROLE|NETWORK|CONFIG|SERVICE|TOKEN|PRECHECK/i.test(errorCode)) {
    return "system";
  }

  return "full";
}

export function createPreflightJoinError(report: JoinPreflightReport, scope: PreflightScope): JoinErrorPayload | null {
  const failingCheck = collectScopedChecks(report, scope)
    .find((check) => check.blocking && (check.status === "error" || check.status === "timeout"));

  if (!failingCheck) {
    return null;
  }

  return {
    title: failingCheck.headline,
    detail: [failingCheck.reason, ...failingCheck.actions].join(" "),
    errorCode: failingCheck.errorCode ?? "PRECHECK_FAILED",
    traceId: report.traceId ?? undefined,
  };
}

export function mergePreflightReport(
  current: JoinPreflightReport,
  snapshot: JoinReadinessPayload | null,
  browserChecks: { systemChecks: JoinReadinessCheck[]; mediaChecks: JoinReadinessCheck[] },
  scope: PreflightScope,
): JoinPreflightReport {
  const nextSystemChecks = scope === "media"
    ? current.systemChecks
    : [...(snapshot?.systemChecks ?? current.systemChecks), ...browserChecks.systemChecks];
  const nextMediaChecks = scope === "system"
    ? current.mediaChecks
    : browserChecks.mediaChecks;

  return {
    status: resolveReportStatus(nextSystemChecks, nextMediaChecks),
    checkedAt: snapshot?.checkedAt ?? current.checkedAt,
    traceId: snapshot?.traceId ?? current.traceId,
    publicJoinUrl: snapshot?.publicJoinUrl ?? current.publicJoinUrl,
    systemChecks: nextSystemChecks,
    mediaChecks: nextMediaChecks,
  };
}

export async function runBrowserPreflight(
  options: BrowserPreflightOptions,
): Promise<{ systemChecks: JoinReadinessCheck[]; mediaChecks: JoinReadinessCheck[] }> {
  const systemChecks = options.scope === "media"
    ? []
    : await Promise.all([runJitsiReachabilityCheck(options.publicJoinUrl)]);

  const mediaChecks = options.scope === "system"
    ? []
    : await runMediaChecks();

  return { systemChecks, mediaChecks };
}

function resolveReportStatus(systemChecks: JoinReadinessCheck[], mediaChecks: JoinReadinessCheck[]) {
  const allChecks = [...systemChecks, ...mediaChecks];
  const hasBlockingFailure = allChecks.some(
    (check) => check.blocking && (check.status === "error" || check.status === "timeout"),
  );
  if (hasBlockingFailure) {
    return "blocked";
  }

  const hasWarning = allChecks.some((check) => check.status === "warn" || check.status === "error");
  return hasWarning ? "degraded" : "ready";
}

function collectScopedChecks(report: JoinPreflightReport, scope: PreflightScope) {
  if (scope === "system") {
    return report.systemChecks;
  }
  if (scope === "media") {
    return report.mediaChecks;
  }
  return [...report.systemChecks, ...report.mediaChecks];
}

async function runMediaChecks(): Promise<JoinReadinessCheck[]> {
  const apiCheck = resolveMediaApiCheck();
  if (apiCheck.status === "error") {
    return [apiCheck];
  }

  const [permissionsCheck, devicesCheck, smokeCheck] = await Promise.all([
    runPermissionsCheck(),
    runEnumerateDevicesCheck(),
    runGetUserMediaCheck(),
  ]);

  return [apiCheck, permissionsCheck, devicesCheck, smokeCheck];
}

function resolveMediaApiCheck(): JoinReadinessCheck {
  if (typeof navigator === "undefined" || !navigator.mediaDevices) {
    return {
      key: "browser-media-api",
      status: "error",
      headline: "Браузер не поддерживает media APIs",
      reason: "navigator.mediaDevices недоступен, preflight не сможет проверить камеру и микрофон.",
      actions: ["Обновить браузер", "Открыть кабинет в Chrome или Edge"],
      errorCode: "BROWSER_MEDIA_UNAVAILABLE",
      blocking: true,
    };
  }

  return {
    key: "browser-media-api",
    status: "ok",
    headline: "Браузер поддерживает media APIs",
    reason: "Можно продолжать smoke-check камеры и микрофона.",
    actions: ["При запросе браузера разрешить доступ к устройствам"],
    errorCode: null,
    blocking: false,
  };
}

async function runPermissionsCheck(): Promise<JoinReadinessCheck> {
  if (typeof navigator === "undefined" || !("permissions" in navigator) || !navigator.permissions?.query) {
    return {
      key: "media-permissions",
      status: "warn",
      headline: "Статус разрешений не удалось прочитать заранее",
      reason: "Permissions API недоступен, поэтому точный статус камеры и микрофона выяснится на smoke-check.",
      actions: ["Если браузер покажет запрос, разрешить доступ к камере и микрофону"],
      errorCode: "MEDIA_PERMISSION_STATUS_UNKNOWN",
      blocking: false,
    };
  }

  try {
    const permissions = await withTimeout(
      Promise.all([
        navigator.permissions.query({ name: "camera" as PermissionName }),
        navigator.permissions.query({ name: "microphone" as PermissionName }),
      ]),
      CHECK_TIMEOUT_MS,
      () => null,
    );

    if (!permissions) {
      return {
        key: "media-permissions",
        status: "timeout",
        headline: "Проверка разрешений не успела завершиться",
        reason: "Браузер слишком долго отвечает на запрос статуса permissions.",
        actions: ["Повторить диагностику", "При необходимости проверить доступ вручную в настройках сайта"],
        errorCode: "MEDIA_PERMISSION_TIMEOUT",
        blocking: false,
      };
    }

    const [camera, microphone] = permissions;

    if (camera.state === "denied" || microphone.state === "denied") {
      return {
        key: "media-permissions",
        status: "error",
        headline: "Доступ к камере или микрофону запрещён",
        reason: "Браузер уже знает, что хотя бы одно разрешение отклонено.",
        actions: ["Открыть настройки сайта и разрешить доступ", "После изменения нажать «Обновить диагностику»"],
        errorCode: "MEDIA_PERMISSION_DENIED",
        blocking: true,
      };
    }

    return {
      key: "media-permissions",
      status: camera.state === "prompt" || microphone.state === "prompt" ? "warn" : "ok",
      headline: camera.state === "prompt" || microphone.state === "prompt"
        ? "Разрешения ещё не подтверждены"
        : "Разрешения на медиа готовы",
      reason: camera.state === "prompt" || microphone.state === "prompt"
        ? "Браузер запросит доступ во время smoke-check или при входе во встречу."
        : "Камера и микрофон уже доступны для этого сайта.",
      actions: ["При появлении системного диалога разрешить доступ"],
      errorCode: camera.state === "prompt" || microphone.state === "prompt"
        ? "MEDIA_PERMISSION_PROMPT"
        : null,
      blocking: false,
    };
  } catch {
    return {
      key: "media-permissions",
      status: "warn",
      headline: "Статус разрешений не удалось определить",
      reason: "Permissions API вернул ошибку.",
      actions: ["Продолжить вход и проверить браузерный запрос", "При проблеме открыть настройки сайта вручную"],
      errorCode: "MEDIA_PERMISSION_STATUS_UNKNOWN",
      blocking: false,
    };
  }
}

async function runEnumerateDevicesCheck(): Promise<JoinReadinessCheck> {
  try {
    const devices = await withTimeout(
      navigator.mediaDevices.enumerateDevices(),
      CHECK_TIMEOUT_MS,
      () => null,
    );

    if (!devices) {
      return {
        key: "media-devices",
        status: "timeout",
        headline: "Список устройств не получен вовремя",
        reason: "Браузер слишком долго отвечает на enumerateDevices().",
        actions: ["Повторить диагностику", "Проверить, подключены ли камера и микрофон"],
        errorCode: "MEDIA_DEVICES_TIMEOUT",
        blocking: false,
      };
    }

    const hasAudioInput = devices.some((device) => device.kind === "audioinput");
    const hasVideoInput = devices.some((device) => device.kind === "videoinput");
    if (!hasAudioInput && !hasVideoInput) {
      return {
        key: "media-devices",
        status: "warn",
        headline: "Браузер не видит устройств ввода",
        reason: "Не найдены ни микрофон, ни камера.",
        actions: ["Подключить устройства", "Перезапустить браузер после подключения"],
        errorCode: "MEDIA_DEVICES_NOT_FOUND",
        blocking: false,
      };
    }

    return {
      key: "media-devices",
      status: hasAudioInput && hasVideoInput ? "ok" : "warn",
      headline: hasAudioInput && hasVideoInput
        ? "Устройства обнаружены"
        : "Обнаружено не всё оборудование",
      reason: hasAudioInput && hasVideoInput
        ? "Камера и микрофон доступны для выбора в браузере."
        : "Найдена только часть устройств, вход возможен с ограничениями.",
      actions: ["Проверить нужные устройства в настройках браузера"],
      errorCode: hasAudioInput && hasVideoInput ? null : "MEDIA_PARTIAL_DEVICE_SET",
      blocking: false,
    };
  } catch {
    return {
      key: "media-devices",
      status: "warn",
      headline: "Не удалось перечислить устройства",
      reason: "enumerateDevices() завершился ошибкой.",
      actions: ["Переподключить устройства", "Повторить диагностику"],
      errorCode: "MEDIA_DEVICES_ENUMERATION_FAILED",
      blocking: false,
    };
  }
}

async function runGetUserMediaCheck(): Promise<JoinReadinessCheck> {
  try {
    const stream = await withTimeout(
      navigator.mediaDevices.getUserMedia({ audio: true, video: true }),
      CHECK_TIMEOUT_MS,
      () => null,
    );

    if (!stream) {
      return {
        key: "media-smoke-check",
        status: "timeout",
        headline: "Smoke-check камеры и микрофона не завершился вовремя",
        reason: "getUserMedia() не вернул результат в пределах таймбокса.",
        actions: ["Повторить диагностику", "Проверить системный диалог браузера"],
        errorCode: "MEDIA_SMOKE_TIMEOUT",
        blocking: false,
      };
    }

    stream.getTracks().forEach((track) => track.stop());
    return {
      key: "media-smoke-check",
      status: "ok",
      headline: "Камера и микрофон отвечают",
      reason: "Smoke-check прошёл успешно, треки сразу освобождены.",
      actions: ["Можно повторять вход во встречу"],
      errorCode: null,
      blocking: false,
    };
  } catch (error) {
    const errorName = error instanceof DOMException ? error.name : "UnknownError";
    const denied = errorName === "NotAllowedError" || errorName === "SecurityError";
    return {
      key: "media-smoke-check",
      status: denied ? "error" : "warn",
      headline: denied
        ? "Браузер отклонил доступ к медиа"
        : "Smoke-check завершился с ограничением",
      reason: denied
        ? "getUserMedia() вернул отказ в доступе к камере или микрофону."
        : `getUserMedia() вернул ${errorName}.`,
      actions: denied
        ? ["Разрешить доступ в настройках сайта", "Повторить диагностику после изменения"]
        : ["Проверить занятость камеры/микрофона другими приложениями", "Повторить диагностику"],
      errorCode: denied ? "MEDIA_PERMISSION_DENIED" : "MEDIA_SMOKE_FAILED",
      blocking: denied,
    };
  }
}

async function runJitsiReachabilityCheck(publicJoinUrl: string | null): Promise<JoinReadinessCheck> {
  if (!publicJoinUrl) {
    return {
      key: "jitsi-edge",
      status: "error",
      headline: "Публичный адрес Jitsi не определён",
      reason: "Backend не передал publicJoinUrl, клиент не может проверить reachability конференции.",
      actions: ["Проверить app.meetings.token.join-url-template", "Повторить диагностику после исправления"],
      errorCode: "JOIN_URL_TEMPLATE_INVALID",
      blocking: true,
    };
  }

  try {
    const reachable = await withTimeout(
      fetch(publicJoinUrl, {
        method: "GET",
        mode: "no-cors",
        credentials: "omit",
      }).then(() => true).catch(() => false),
      CHECK_TIMEOUT_MS,
      () => false,
    );

    return reachable
      ? {
          key: "jitsi-edge",
          status: "ok",
          headline: "Jitsi отвечает из браузера",
          reason: `Браузер смог достучаться до ${publicJoinUrl}.`,
          actions: ["Можно повторять вход во встречу"],
          errorCode: null,
          blocking: false,
        }
      : {
          key: "jitsi-edge",
          status: "error",
          headline: "Jitsi недоступен из браузера",
          reason: `Не удалось открыть ${publicJoinUrl} даже в режиме no-cors.`,
          actions: ["Открыть адрес вручную и подтвердить сертификат", "Проверить доступность 8443/HTTPS"],
          errorCode: "JITSI_UNREACHABLE",
          blocking: true,
        };
  } catch {
    return {
      key: "jitsi-edge",
      status: "error",
      headline: "Jitsi недоступен из браузера",
      reason: `Запрос к ${publicJoinUrl} завершился ошибкой.`,
      actions: ["Открыть адрес вручную и подтвердить сертификат", "Проверить доступность 8443/HTTPS"],
      errorCode: "JITSI_UNREACHABLE",
      blocking: true,
    };
  }
}

async function withTimeout<T>(
  promise: Promise<T>,
  timeoutMs: number,
  onTimeout: () => T,
): Promise<T> {
  return await new Promise<T>((resolve) => {
    const timeoutId = window.setTimeout(() => resolve(onTimeout()), timeoutMs);
    promise
      .then((result) => {
        window.clearTimeout(timeoutId);
        resolve(result);
      })
      .catch(() => {
        window.clearTimeout(timeoutId);
        resolve(onTimeout());
      });
  });
}