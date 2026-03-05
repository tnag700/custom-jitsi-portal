import { component$, type QRL } from "@qwik.dev/core";
import type { UpcomingMeetingCard as UpcomingMeetingCardType } from "../types";
import { UpcomingMeetingCard } from "./UpcomingMeetingCard";

interface UpcomingMeetingsListProps {
  meetings: UpcomingMeetingCardType[];
  joiningMeetingId: string | null;
  disabled?: boolean;
  onJoin$: QRL<(meetingId: string) => void>;
}

export const UpcomingMeetingsList = component$<UpcomingMeetingsListProps>(
  ({ meetings, joiningMeetingId, disabled, onJoin$ }) => {
    return (
      <section>
        <h2 class="mb-4 text-xl font-bold text-text">Мои встречи</h2>

        {meetings.length === 0 ? (
          <p class="text-muted">Нет предстоящих встреч</p>
        ) : (
          <div class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
            {meetings.map((card) => (
              <UpcomingMeetingCard
                key={card.meetingId}
                card={card}
                isJoining={joiningMeetingId === card.meetingId}
                disabled={disabled}
                onJoin$={onJoin$}
              />
            ))}
          </div>
        )}
      </section>
    );
  },
);
