import type { Session } from "@/features/events/domain/session";
import { sessionPhase } from "@/features/events/domain/session-phase";
import { SessionActionPoints } from "@/features/events/ui/SessionActionPoints";
import { SessionNote } from "@/features/events/ui/SessionNote";
import { SessionToVerify } from "@/features/events/ui/SessionToVerify";
import { SessionOrphanResources } from "@/features/knowledge/ui/SessionOrphanResources";

interface Props {
  slug: string;
  session: Session;
  canManage: boolean;
}

/**
 * What a session owes and what it left behind, ahead of what it plans to do.
 *
 * What this session must verify leads regardless of phase: a checkpoint consumes no agenda time
 * of its own, so an item carried here shows up whether the session is still ahead or already
 * held. A finished session is then visited for its record, not for its agenda, so the record
 * follows: what the session left behind in words, then what it raised, then what it produced.
 * That second block is gated on the phase rather than on the view's mode: gated on `capture`,
 * opening the session for editing took the action points off the page, which is content loss
 * dressed up as a mode change.
 */
export function SessionOutcomes({ slug, session, canManage }: Readonly<Props>) {
  const held = sessionPhase(session) === "held";
  return (
    <>
      <SessionToVerify slug={slug} sessionId={session.id} />
      {held && (
        <div className="flex flex-col gap-3">
          <SessionNote session={session} />
          <section
            id="session-outcomes"
            aria-label="Action points"
            className="scroll-mt-24"
          >
            <SessionActionPoints slug={slug} sessionId={session.id} />
          </section>
          <section
            id="session-resources"
            aria-label="Resources"
            className="scroll-mt-24"
          >
            <SessionOrphanResources
              slug={slug}
              sessionId={session.id}
              knownSegmentIds={session.sections.flatMap((s) =>
                s.slots.map((slot) => slot.id),
              )}
              canManage={canManage}
            />
          </section>
        </div>
      )}
    </>
  );
}
