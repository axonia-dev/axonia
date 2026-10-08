import { LoadFailed } from "@/components/patterns/LoadFailed";
import { Select } from "@/components/ui/Select";
import { useUpcomingSessions } from "@/features/events/api/use-sessions";
import type { Session } from "@/features/events/domain/session";
import { checkpointDayLabel } from "@/features/events/ui/checkpoint-dates";
import { failedToLoad, stillLoading } from "@/lib/failed-to-load";
import { useAnsweredAfterFailing } from "@/lib/use-answered-after-failing";

interface Props {
  slug: string;
  value: number | null;
  onChange: (sessionId: number | null) => void;
}

/** Sentinel option id for "late-bind to whichever session comes next", the same meaning as a
 *  `null` checkpointSessionId. Kept out of the number space real session ids live in, rather
 *  than borrowing a magic number from it. */
const NEXT_SESSION = "next-session";

type CheckpointId = number | typeof NEXT_SESSION;

const UPCOMING_PARAMS = { page: 0, size: 100 } as const;

/** The session is in hand here, so its own governing zone is, which a bare checkpoint instant
 *  elsewhere cannot supply. */
function formatSessionDate(session: Session): string {
  return checkpointDayLabel(session.scheduledAt, session.seriesTimeZone);
}

function nextSessionLabel(sessions: readonly Session[]): string {
  const soonest = sessions[0];
  return soonest === undefined
    ? "Next session"
    : `Next session (${formatSessionDate(soonest)})`;
}

/**
 * Where an action item is answered: the room, not necessarily the room it was raised in.
 * "Next session" is always the first, default choice - it late-binds to whichever session
 * comes next rather than leaving the item unbound - followed by the community's real
 * upcoming sessions. Nothing here is ever projected: an occurrence with no session row yet
 * cannot be chosen, and picking one is not this control's job.
 */
export function CheckpointSelect({ slug, value, onChange }: Readonly<Props>) {
  const upcoming = useUpcomingSessions(slug, UPCOMING_PARAMS);
  const upcomingFailed = failedToLoad(upcoming);
  const upcomingRecovered = useAnsweredAfterFailing(upcomingFailed);
  const sessions = upcoming.data?.content ?? [];

  const options = [
    { id: NEXT_SESSION as CheckpointId, label: nextSessionLabel(sessions) },
    ...sessions.map((session) => ({
      id: session.id as CheckpointId,
      label: formatSessionDate(session),
    })),
  ];

  function handleChange(id: CheckpointId) {
    onChange(id === NEXT_SESSION ? null : id);
  }

  if (upcomingFailed) {
    return (
      <LoadFailed
        inline
        message="Could not load the upcoming sessions."
        query={upcoming}
      />
    );
  }

  return (
    <Select
      label="Answered at"
      value={value ?? NEXT_SESSION}
      options={options}
      onChange={handleChange}
      isLoading={stillLoading(upcoming)}
      takesFocus={upcomingRecovered}
    />
  );
}
