import { ArrowRight } from "lucide-react";
import { Link } from "react-router-dom";

import type { NextUp } from "@/features/community/domain/session-recap";
import { relativeTime } from "@/lib/period";
import { plural } from "@/lib/plural";
import { useNow } from "@/lib/use-wake-at";

interface Props {
  slug: string;
  next: NextUp;
}

/** What the foot offers to do about the next session, in the order of how much it needs doing. */
function callToAction(next: NextUp): string {
  if (next.empty) {
    return "Propose a talk";
  }
  return next.openSpots > 0 ? "Take a spot" : "See the agenda";
}

function agendaOf(next: NextUp): string {
  if (next.empty) {
    return "No agenda yet";
  }
  const booked = plural(next.booked, "talk") + " booked";
  return next.openSpots > 0
    ? `${booked} · ${plural(next.openSpots, "spot")} open`
    : booked;
}

/**
 * The marquee's closing line: what follows the session above it.
 *
 * It stands on its own, so a community with nothing held yet still has its next session stated.
 * This is the only place the overview says so since the hero chip and the rail's own card were
 * removed, and it is the one thing on the page a reader can act on straight away.
 */
export function MarqueeNext({ slug, next }: Readonly<Props>) {
  const now = useNow();
  return (
    <div className="border-border-strong bg-background/40 flex flex-wrap items-center justify-between gap-x-4 gap-y-2 border-t px-5 py-3">
      <p className="text-muted-foreground min-w-0 text-sm">
        <span className="text-foreground font-semibold">Next up</span>
        <span className="mx-1.5">·</span>
        <Link
          to={`/communities/${slug}/sessions/${next.sessionId}`}
          className="hover:text-foreground underline-offset-2 hover:underline"
        >
          {next.title}
        </Link>
        <span className="mx-1.5">·</span>
        {relativeTime(next.scheduledAt, now)}
        <span className="mx-1.5">·</span>
        {agendaOf(next)}
      </p>
      <Link
        to={`/communities/${slug}/sessions/${next.sessionId}`}
        className="text-primary inline-flex shrink-0 items-center gap-1 text-sm font-semibold hover:underline"
      >
        {callToAction(next)}
        <ArrowRight size={13} />
      </Link>
    </div>
  );
}
