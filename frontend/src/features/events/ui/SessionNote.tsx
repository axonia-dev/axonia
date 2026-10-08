import type { Session } from "@/features/events/domain/session";

interface Props {
  session: Session;
}

/**
 * The organizer's own words about a held session, quoted under its facts.
 *
 * No heading: a quoted line beneath a session's title is understood without being named, and the
 * word it used to be named with covered two unrelated things at once. No control either - the
 * recording chip asks for the first note, and the actions menu edits it afterwards.
 */
export function SessionNote({ session }: Readonly<Props>) {
  if (session.notes === null) {
    return null;
  }
  return (
    <p className="text-foreground border-primary/40 border-l-2 pl-3 text-sm leading-relaxed whitespace-pre-line">
      {session.notes}
    </p>
  );
}
