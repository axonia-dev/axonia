import { useState } from "react";
import { useParams } from "react-router-dom";

import { Button } from "@/components/ui/Button";
import { SaveAction } from "@/features/bookmarks/ui/SaveAction";
import { useCommunityCrumbs } from "@/features/community/ui/community-chrome";
import { useSession } from "@/features/events/api/use-session";
import { sectionAnchorId } from "@/features/events/domain/run-sheet";
import type { Session } from "@/features/events/domain/session";
import {
  isCancelled,
  isHeld,
  isSkipped,
  nextSessionMark,
  sessionTitle,
} from "@/features/events/domain/session";
import type { SessionPhase } from "@/features/events/domain/session-phase";
import {
  sessionPhase,
  sessionStatusChip,
} from "@/features/events/domain/session-phase";
import type { SessionMode } from "@/features/events/ui/session-mode";
import { SessionActions } from "@/features/events/ui/SessionActions";
import { SessionCard } from "@/features/events/ui/SessionCard";
import { SessionClosePanel } from "@/features/events/ui/SessionClosePanel";
import {
  RAIL_MIN_SECTIONS,
  SessionRail,
} from "@/features/events/ui/SessionRail";
import { SkippedBanner } from "@/features/events/ui/SkippedBanner";
import { useScrollSpy } from "@/features/events/ui/use-scroll-spy";
import { useEventActions } from "@/features/events/ui/useEventActions";
import { useWakeAt } from "@/lib/use-wake-at";

function resolveMode(session: Session, editing: boolean): SessionMode {
  if (isSkipped(session) || isCancelled(session)) return "readonly";
  if (isHeld(session)) return editing ? "edit" : "capture";
  return "contribute";
}

/** A held session's structure editor stays behind an explicit toggle. */
function canToggleEditStructure(
  session: Session,
  canEditStructure: boolean,
): boolean {
  return isHeld(session) && canEditStructure;
}

/** The ids a scroll spy watches for this session. A session that has not loaded yet has none,
 *  rather than skipping the hook call the loading state below still has to reach. */
function anchorIdsFor(session: Session | null): string[] {
  if (!session) return [];
  return [
    ...(sessionPhase(session) === "held"
      ? ["session-outcomes", "session-resources"]
      : []),
    ...session.sections.map((s) => sectionAnchorId(s.id)),
  ];
}

interface PhasePanelProps {
  slug: string;
  session: Session;
  phase: SessionPhase;
  canManage: boolean;
}

/**
 * The one thing that still leads: on a session nobody has answered for, the question is the
 * page. A session already closed out as skipped gets the same lead spot, for the same reason -
 * everything else an organizer needs to decide sits below the record.
 *
 * There is no trailing counterpart any more. Both panels that used to follow the record carried a
 * release, and a release is now stated, answered and previewed in one place: the facts strip's own
 * chip, level with the title rather than a run sheet's length away from it.
 */
function LeadingPanel({
  slug,
  session,
  phase,
  canManage,
}: Readonly<PhasePanelProps>) {
  if (phase === "awaiting") {
    return (
      <SessionClosePanel slug={slug} session={session} canManage={canManage} />
    );
  }
  if (phase === "closed" && isSkipped(session)) {
    return (
      <SkippedBanner slug={slug} session={session} canManage={canManage} />
    );
  }
  return null;
}

interface HeaderActionsProps {
  slug: string;
  session: Session;
  canManage: boolean;
  canEditStructure: boolean;
  canToggleEdit: boolean;
  skipped: boolean;
  editing: boolean;
  onToggleEdit: () => void;
}

function HeaderActions({
  slug,
  session,
  canManage,
  canEditStructure,
  canToggleEdit,
  skipped,
  editing,
  onToggleEdit,
}: Readonly<HeaderActionsProps>) {
  return (
    <>
      <SaveAction target={{ kind: "session", id: session.id }} />
      {canToggleEdit && (
        <Button variant="secondary" size="sm" onPress={onToggleEdit}>
          {editing ? "Done" : "Edit"}
        </Button>
      )}
      {!skipped && (
        <SessionActions
          slug={slug}
          session={session}
          canEditStructure={canEditStructure}
          canManage={canManage}
          editing={editing}
        />
      )}
    </>
  );
}

export function SessionDetailPage({ slug }: Readonly<{ slug: string }>) {
  const { sessionId } = useParams();
  const id = Number(sessionId);
  const {
    canManage,
    canManageMessages,
    canEditStructure,
    canQueueTopics,
    canManageSchedule,
    renderSessionActions,
    renderSlotActions,
  } = useEventActions(slug);
  const query = useSession(id);
  const session = query.data ?? null;
  const [editing, setEditing] = useState(false);
  useCommunityCrumbs(session ? [{ label: sessionTitle(session) }] : []);
  // Hooks run every render regardless of the loading/error returns below, so the ids a session
  // has not loaded yet resolve to none rather than skip a hook call.
  const activeId = useScrollSpy(anchorIdsFor(session));
  // The page states what the session is doing right now, so it turns over on the session's own
  // clock rather than on a reload: "starting soon" stops the moment it starts, and "running now"
  // the moment it finishes, which is also when the close question is finally owed.
  useWakeAt(session ? nextSessionMark(session) : null);

  if (query.isLoading) {
    return <p className="text-muted-foreground text-sm">Loading…</p>;
  }
  if (query.isError || !session) {
    return <p className="text-muted-foreground text-sm">Session not found.</p>;
  }

  const mode = resolveMode(session, editing);
  // Keyed on the session's own status, never on the mode: a cancelled session shares
  // `readonly` with a skipped one, but still needs its actions menu reachable to reopen
  // or reschedule, which a mode-derived flag would silently take away from it too.
  const skipped = isSkipped(session);
  const phase = sessionPhase(session);
  const chip = sessionStatusChip(session);
  const showRail = session.sections.length >= RAIL_MIN_SECTIONS;
  const headerActions = (
    <HeaderActions
      slug={slug}
      session={session}
      canManage={canManage}
      canEditStructure={canEditStructure}
      canToggleEdit={canToggleEditStructure(session, canEditStructure)}
      skipped={skipped}
      editing={editing}
      onToggleEdit={() => setEditing((on) => !on)}
    />
  );
  return (
    <section className="flex flex-col gap-6">
      <LeadingPanel
        slug={slug}
        session={session}
        phase={phase}
        canManage={canManage}
      />

      <SessionCard
        slug={slug}
        session={session}
        phase={phase}
        statusChip={chip}
        mode={mode}
        canManage={canManage}
        canManageSchedule={canManageSchedule}
        canQueueTopics={canQueueTopics}
        canManageMessages={canManageMessages}
        headerActions={headerActions}
        rail={
          showRail ? (
            <SessionRail session={session} phase={phase} activeId={activeId} />
          ) : null
        }
        {...(renderSessionActions
          ? { actions: renderSessionActions(session) }
          : {})}
        {...(renderSlotActions ? { renderSlotActions } : {})}
      />
    </section>
  );
}
