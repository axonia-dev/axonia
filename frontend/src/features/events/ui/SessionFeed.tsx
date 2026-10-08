import type { ReactNode } from "react";

import type { Section } from "@/features/events/domain/section";
import type { Session } from "@/features/events/domain/session";
import { nextSessionMark } from "@/features/events/domain/session";
import {
  sessionPhase,
  sessionStatusChip,
} from "@/features/events/domain/session-phase";
import type { Slot } from "@/features/events/domain/slot";
import { SessionCard } from "@/features/events/ui/SessionCard";
import { useWakeAt } from "@/lib/use-wake-at";

interface Props {
  slug: string;
  sessions: Session[];
  isLoading: boolean;
  isError: boolean;
  canManage: boolean;
  canQueueTopics: boolean;
  renderSessionActions?: (session: Session) => ReactNode;
  renderSlotActions?: (
    slot: Slot,
    section: Section,
    session: Session,
  ) => ReactNode;
}

interface CardProps {
  slug: string;
  session: Session;
  canManage: boolean;
  canQueueTopics: boolean;
  actions?: ReactNode;
  renderSlotActions?: Props["renderSlotActions"];
}

/**
 * One card, reading its own session's clock.
 *
 * A card is its own component rather than a branch of the list so it can register the instants
 * its session turns on: a feed left open otherwise keeps calling a session that has since begun
 * "starting soon", and one that has since finished "running now". The phase and the chip are
 * still derived here and handed down together, so everything one card states is read off a single
 * instant rather than off as many `new Date()` calls as there are readers.
 */
function FeedCard({
  slug,
  session,
  canManage,
  canQueueTopics,
  actions,
  renderSlotActions,
}: Readonly<CardProps>) {
  useWakeAt(nextSessionMark(session));
  return (
    <SessionCard
      slug={slug}
      session={session}
      phase={sessionPhase(session)}
      statusChip={sessionStatusChip(session)}
      canManage={canManage}
      canQueueTopics={canQueueTopics}
      {...(actions ? { actions } : {})}
      {...(renderSlotActions ? { renderSlotActions } : {})}
    />
  );
}

export function SessionFeed({
  slug,
  sessions,
  isLoading,
  isError,
  canManage,
  canQueueTopics,
  renderSessionActions,
  renderSlotActions,
}: Readonly<Props>) {
  return (
    <div className="flex flex-col gap-3">
      {isLoading && <p className="text-muted-foreground text-sm">Loading…</p>}
      {isError && (
        <p role="alert" className="text-danger text-sm">
          Could not load upcoming sessions.
        </p>
      )}
      {!isLoading && sessions.length === 0 && (
        <p className="text-muted-foreground text-sm">
          No upcoming sessions yet.
        </p>
      )}
      <ul className="flex flex-col gap-2">
        {sessions.map((session) => (
          <li key={session.id}>
            <FeedCard
              slug={slug}
              session={session}
              canManage={canManage}
              canQueueTopics={canQueueTopics}
              {...(renderSessionActions
                ? { actions: renderSessionActions(session) }
                : {})}
              {...(renderSlotActions ? { renderSlotActions } : {})}
            />
          </li>
        ))}
      </ul>
    </div>
  );
}
