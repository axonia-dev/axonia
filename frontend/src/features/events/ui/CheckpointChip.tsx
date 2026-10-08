import { CalendarDays } from "lucide-react";
import { Link } from "react-router-dom";

import type { ActionItem } from "@/features/events/domain/action-item";
import { checkpointDayLabel } from "@/features/events/ui/checkpoint-dates";

type Checkpoint = ActionItem["checkpoint"];

const chipClass =
  "bg-muted text-muted-foreground inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[10px] font-semibold";

/** A pinned checkpoint states the session it was pinned to. An unpinned one late-binds to
 *  whichever session comes next - "Next session" alone until the backend can resolve which one
 *  that is, then the resolved date beside it. Never "unbound": a null `sessionId` still names an
 *  answer, just not yet a specific occurrence. */
function checkpointLabel(checkpoint: Checkpoint): string {
  if (checkpoint.scheduledAt === null) {
    return "Next session";
  }
  const date = checkpointDayLabel(
    checkpoint.scheduledAt,
    checkpoint.governingTimeZone,
  );
  return checkpoint.pinned ? date : `Next session, ${date}`;
}

interface Props {
  checkpoint: Checkpoint;
  slug: string;
}

/** States which session this item is answered at, and goes there. A checkpoint with no resolved
 *  session still names an answer, just not yet an occurrence, so it is plain text rather than a
 *  link to nowhere. */
export function CheckpointChip({ checkpoint, slug }: Readonly<Props>) {
  const body = (
    <>
      <CalendarDays className="h-3 w-3" />
      {checkpointLabel(checkpoint)}
    </>
  );
  if (checkpoint.sessionId === null) {
    return <span className={chipClass}>{body}</span>;
  }
  return (
    <Link
      to={`/communities/${slug}/sessions/${checkpoint.sessionId}`}
      className={`${chipClass} hover:text-foreground transition-colors`}
    >
      {body}
    </Link>
  );
}
