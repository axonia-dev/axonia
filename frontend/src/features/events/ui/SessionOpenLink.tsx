import { ExternalLink } from "lucide-react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";

import { MorphLink } from "@/components/patterns/morph/MorphLink";
import { useReadySession } from "@/features/events/api/use-ready-session";
import type { ScheduleEntry } from "@/features/events/domain/schedule";

const OPEN_LINK_CLASS =
  "text-primary inline-flex items-center gap-1.5 text-sm font-semibold";

export function SessionOpenLink({
  entry,
  href,
  morphId,
}: Readonly<{ entry: ScheduleEntry; href: string; morphId?: string }>) {
  const body = (
    <>
      {entry.virtual ? "Open the preview" : "Open the session"}
      <ExternalLink aria-hidden size={13} />
    </>
  );
  if (morphId === undefined || entry.sessionId === null) {
    return (
      <Link to={href} className={OPEN_LINK_CLASS}>
        {body}
      </Link>
    );
  }
  return (
    <MorphSessionLink sessionId={entry.sessionId} href={href}>
      {body}
    </MorphSessionLink>
  );
}

function MorphSessionLink({
  sessionId,
  href,
  children,
}: Readonly<{ sessionId: number; href: string; children: ReactNode }>) {
  const session = useReadySession();
  return (
    <MorphLink
      to={href}
      ready={() => session.ready(sessionId)}
      onIntent={() => session.prefetch(sessionId)}
      className={OPEN_LINK_CLASS}
    >
      {children}
    </MorphLink>
  );
}
