/**
 * Format ISO date string to locale-friendly representation.
 */
export const APPLICATION_TIME_ZONE = "Europe/Minsk";

const localInputFormatter = new Intl.DateTimeFormat("en-CA", {
  timeZone: APPLICATION_TIME_ZONE,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hourCycle: "h23",
});

function getDatePart(
  parts: Intl.DateTimeFormatPart[],
  type: Intl.DateTimeFormatPartTypes,
): string {
  return parts.find((part) => part.type === type)?.value ?? "";
}

function parseDateTimeLocalParts(value: string): {
  year: number;
  month: number;
  day: number;
  hour: number;
  minute: number;
} | null {
  const match =
    /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(value);
  if (!match) {
    return null;
  }

  const [year, month, day, hour, minute] = match
    .slice(1)
    .map(Number);
  const probe = new Date(
    Date.UTC(year, month - 1, day, hour, minute),
  );

  if (
    probe.getUTCFullYear() !== year ||
    probe.getUTCMonth() !== month - 1 ||
    probe.getUTCDate() !== day ||
    probe.getUTCHours() !== hour ||
    probe.getUTCMinutes() !== minute
  ) {
    return null;
  }

  return { year, month, day, hour, minute };
}

function getApplicationTimeZoneOffset(date: Date): number {
  const parts = localInputFormatter.formatToParts(date);
  const asUtc = Date.UTC(
    Number(getDatePart(parts, "year")),
    Number(getDatePart(parts, "month")) - 1,
    Number(getDatePart(parts, "day")),
    Number(getDatePart(parts, "hour")),
    Number(getDatePart(parts, "minute")),
    Number(getDatePart(parts, "second")),
  );

  return asUtc - date.getTime();
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("ru-RU", {
    timeZone: APPLICATION_TIME_ZONE,
    year: "numeric",
    month: "long",
    day: "numeric",
  });
}

/**
 * Format ISO date-time string to locale-friendly date and time.
 */
export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString("ru-RU", {
    timeZone: APPLICATION_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function formatDateTimeLocalInput(iso: string | undefined): string {
  if (!iso) {
    return "";
  }

  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return "";
  }

  const parts = localInputFormatter.formatToParts(date);
  return [
    getDatePart(parts, "year"),
    "-",
    getDatePart(parts, "month"),
    "-",
    getDatePart(parts, "day"),
    "T",
    getDatePart(parts, "hour"),
    ":",
    getDatePart(parts, "minute"),
  ].join("");
}

export function parseDateTimeLocalInput(value: string): string {
  const parts = parseDateTimeLocalParts(value);
  if (!parts) {
    return "";
  }

  const localAsUtc = Date.UTC(
    parts.year,
    parts.month - 1,
    parts.day,
    parts.hour,
    parts.minute,
  );
  let instant = localAsUtc;

  for (let iteration = 0; iteration < 2; iteration += 1) {
    instant =
      localAsUtc -
      getApplicationTimeZoneOffset(new Date(instant));
  }

  return new Date(instant).toISOString();
}
