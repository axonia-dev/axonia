import { Link } from "react-router-dom";

import { useSeriesList } from "@/features/events/api/use-series";
import { useSession } from "@/features/events/api/use-session";
import {
  distinctPresenters,
  sessionTitle,
} from "@/features/events/domain/session";

interface Props {
  slug: string;
  sessionId: number;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, {
    weekday: "short",
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

/**
 * The header for an active session facet: the session's series title (or its own
 * title), occurrence date, presenters, and a link to the session page. Resolved
 * entirely from the events gateway — surface composition, no new backend.
 */
export function SessionContextStrip({ slug, sessionId }: Readonly<Props>) {
  const query = useSession(sessionId);
  const series = useSeriesList(slug);
  const session = query.data;

  if (!session) {
    return null;
  }

  const seriesTitle =
    session.seriesId !== null
      ? series.data?.content.find((entry) => entry.id === session.seriesId)
          ?.title
      : undefined;
  const title = seriesTitle ?? sessionTitle(session);
  const presenters = distinctPresenters(session);

  return (
    <aside className="border-border bg-surface-sunken flex flex-col gap-1 rounded-md border p-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h3 className="font-display text-foreground font-semibold">{title}</h3>
        <Link
          to={`/communities/${slug}/sessions/${sessionId}`}
          className="text-primary text-sm underline-offset-2 hover:underline"
        >
          Open session →
        </Link>
      </div>
      <p className="text-muted-foreground text-sm">
        {formatDate(session.occurrenceDate ?? session.scheduledAt)}
        {presenters.length > 0 && ` · ${presenters.join(", ")}`}
      </p>
    </aside>
  );
}
