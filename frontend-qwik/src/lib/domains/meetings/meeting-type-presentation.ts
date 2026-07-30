export interface MeetingTypePresentation {
  value: string;
  label: string;
  description: string;
}

export const MEETING_TYPE_OPTIONS: readonly MeetingTypePresentation[] = [
  {
    value: "standard",
    label: "Обычная встреча",
    description: "Совместное обсуждение с равным участием приглашённых.",
  },
  {
    value: "webinar",
    label: "Вебинар",
    description: "Выступление ведущих для аудитории с вопросами и ответами.",
  },
  {
    value: "workshop",
    label: "Практическая сессия",
    description: "Работа участников над задачей, материалами или результатом.",
  },
];

export function getMeetingTypePresentation(
  meetingType: string,
): MeetingTypePresentation {
  return (
    MEETING_TYPE_OPTIONS.find((option) => option.value === meetingType) ?? {
      value: meetingType,
      label: "Другой формат",
      description:
        "Формат сохранён в предыдущей версии. Выберите актуальный вариант при редактировании.",
    }
  );
}

export function getMeetingTypeOptions(
  meetingType: string,
): readonly MeetingTypePresentation[] {
  const current = getMeetingTypePresentation(meetingType);
  return MEETING_TYPE_OPTIONS.some((option) => option.value === meetingType)
    ? MEETING_TYPE_OPTIONS
    : [current, ...MEETING_TYPE_OPTIONS];
}
