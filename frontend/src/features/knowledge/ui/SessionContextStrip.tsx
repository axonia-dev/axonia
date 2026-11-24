import { Link } from "react-router-dom";

import { useSession } from "@/features/events/api/use-session";
import { distinctPresenters } from "@/features/events/domain/session";
import { useUsers } from "@/features/identity/api/use-users";

interface Props {
  slug: string;
  sessionId: number;
}

/** An occurrence date is a bare calendar day that parses as UTC midnight, so it is read in UTC
 *  and never rolls back a day west of UTC; a one-off's instant is read on the reader's clock. */
function formatDate(
  occurrenceDate: string | null,
  scheduledAt: string,
): string {
  return new Date(occurrenceDate ?? scheduledAt).toLocaleDateString(undefined, {
    weekday: "short",
    month: "short",
    day: "numeric",
    year: "numeric",
    ...(occurrenceDate === null ? {} : { timeZone: "UTC" }),
  });
}

/**
 * The header for an active session facet: the session's name, occurrence date, presenters,
 * and a link to the session page. Resolved entirely from the events gateway: surface
 * composition, no new backend.
 */
export function SessionContextStrip({ slug, sessionId }: Readonly<Props>) {
  const query = useSession(sessionId);
  const session = query.data;
  const presenterIds = session ? distinctPresenters(session) : [];
  const usersById = new Map(Object.entries(useUsers(presenterIds).data ?? {}));

  if (!session) {
    return null;
  }

  const title = session.displayTitle;
  const presenters = presenterIds.flatMap((id) => {
    const user = usersById.get(id);
    return user ? [user.displayName] : [];
  });

  return (
    <aside className="border-border bg-surface-sunken flex flex-col gap-1 rounded-md border p-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h3 className="font-display text-foreground font-semibold">{title}</h3>
        <Link
          to={`/communities/${slug}/sessions/${sessionId}`}
          className="text-primary-ink text-sm underline-offset-2 hover:underline"
        >
          Open session →
        </Link>
      </div>
      <p className="text-muted-foreground text-sm">
        {formatDate(session.occurrenceDate, session.scheduledAt)}
        {presenters.length > 0 && ` · ${presenters.join(", ")}`}
      </p>
    </aside>
  );
}
