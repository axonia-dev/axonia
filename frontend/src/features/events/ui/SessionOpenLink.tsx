import { ExternalLink } from "lucide-react";

import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { SessionMorphLink } from "@/features/events/ui/SessionMorphLink";

export function SessionOpenLink({
  entry,
  href,
  morphId,
}: Readonly<{ entry: ScheduleEntry; href: string; morphId?: string }>) {
  return (
    <SessionMorphLink
      to={href}
      sessionId={morphId === undefined ? null : entry.sessionId}
      className="text-primary inline-flex items-center gap-1.5 text-sm font-semibold"
    >
      {entry.virtual ? "Open the preview" : "Open the session"}
      <ExternalLink aria-hidden size={13} />
    </SessionMorphLink>
  );
}
