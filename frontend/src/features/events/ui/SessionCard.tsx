import type { ComponentProps, ReactNode } from "react";

import type { StatusTone } from "@/components/ui/StatusChip";
import { StatusChip } from "@/components/ui/StatusChip";
import { governingZone } from "@/features/events/domain/reschedule-target";
import type { Section } from "@/features/events/domain/section";
import type { Session } from "@/features/events/domain/session";
import { sessionTitle } from "@/features/events/domain/session";
import type { SessionPhase } from "@/features/events/domain/session-phase";
import type { Slot } from "@/features/events/domain/slot";
import { instantToZoned } from "@/features/events/domain/zoned-instant";
import { JoinControl } from "@/features/events/ui/JoinControl";
import {
  RecordingChip,
  statesRecording,
} from "@/features/events/ui/RecordingChip";
import { sessionRelease } from "@/features/events/ui/release-chip";
import { ReminderChip } from "@/features/events/ui/ReminderChip";
import { RunSheet } from "@/features/events/ui/RunSheet";
import { shortDayLabel } from "@/features/events/ui/schedule-dates";
import type { SessionMode } from "@/features/events/ui/session-mode";
import { SessionFacts } from "@/features/events/ui/SessionFacts";
import { SessionOutcomes } from "@/features/events/ui/SessionOutcomes";
import { SessionPlace, stillToHappen } from "@/features/events/ui/SessionPlace";
import { WrapUpChip } from "@/features/events/ui/WrapUpChip";

interface Props {
  slug: string;
  session: Session;
  phase: SessionPhase;
  statusChip: { tone: StatusTone; label: string };
  actions?: ReactNode;
  headerActions?: ReactNode;
  canManage?: boolean;
  canManageSchedule?: boolean;
  canQueueTopics?: boolean;
  /** The community's `canManageCommunication` axis: whether this reader may be told about, and
   *  preview, the session's own outgoing mail. */
  canManageMessages?: boolean;
  mode?: SessionMode;
  /** The map of this session's own contents, drawn once it has enough of them to earn one. */
  rail?: ReactNode;
  renderSlotActions?: (
    slot: Slot,
    section: Section,
    session: Session,
  ) => ReactNode;
}

interface FooterProps {
  slug: string;
  session: Session;
  canManage: boolean;
}

/**
 * The date this session meets, on the community's own clock rather than the reader's - the hour,
 * and whether one was ever set, is the facts strip's job now, stated once and in the same zone.
 * Read through `governingZone` rather than `toLocaleString(undefined, ...)`: a bare instant
 * carries no zone of its own, so a reader outside the community's zone would otherwise see a date
 * that can disagree with the strip's own hour by a whole calendar day.
 */
function whenLabel(session: Session): string {
  const zone = governingZone(session);
  const date = instantToZoned(session.scheduledAt, zone).date;
  return shortDayLabel(date);
}

function CardFooter({ slug, session, canManage }: Readonly<FooterProps>) {
  return (
    <SessionOutcomes slug={slug} session={session} canManage={canManage} />
  );
}

interface HeaderProps {
  session: Session;
  statusChip: { tone: StatusTone; label: string };
  headerActions?: ReactNode;
}

/**
 * The title, told once as the page's own heading, beside the facts and controls a wide layout
 * has room for next to it. The phone layout gets the date again below, in its own row, because
 * the header's copy is hidden there rather than reflowed.
 */
function SessionCardHeader({
  session,
  statusChip,
  headerActions,
}: Readonly<HeaderProps>) {
  const when = {
    scheduledAt: session.scheduledAt,
    hasTime: session.hasTime,
    durationMinutes: session.durationMinutes,
  };
  return (
    <>
      <header className="flex flex-wrap items-center gap-2">
        <h2 className="text-foreground font-display min-w-0 text-xl font-bold break-words max-sm:basis-full sm:flex-1 sm:truncate">
          {sessionTitle(session)}
        </h2>
        <span className="text-muted-foreground hidden shrink-0 text-sm sm:inline">
          {whenLabel(session)}
        </span>
        <JoinControl
          joinUrl={session.place.joinUrl}
          {...when}
          stillToHappen={stillToHappen(session.status)}
          className="max-sm:hidden"
          phaseStatedElsewhere
        />
        <span className="flex max-sm:me-auto">
          <StatusChip tone={statusChip.tone} label={statusChip.label} />
        </span>
        {headerActions}
      </header>
      <p className="text-muted-foreground text-sm sm:hidden">
        {whenLabel(session)}
      </p>
    </>
  );
}

interface AgendaProps {
  rail: ReactNode;
  sheetProps: ComponentProps<typeof RunSheet>;
}

/**
 * The agenda itself: a rail beside it once there is enough of one to be worth a map, the run
 * sheet alone otherwise. One branch rather than a rail rendered empty, so a session below the
 * threshold never reserves a column nothing sits in.
 */
function SessionCardAgenda({ rail, sheetProps }: Readonly<AgendaProps>) {
  if (!rail) {
    return <RunSheet {...sheetProps} />;
  }
  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-[13rem_minmax(0,1fr)]">
      <div className="max-lg:hidden">{rail}</div>
      <RunSheet {...sheetProps} />
    </div>
  );
}

/**
 * The facts strip's release slot, or nothing at all.
 *
 * Nothing about the session's mail reaches a reader without the community's communication axis:
 * whether a notification went out is operational, and the endpoint behind the preview refuses
 * anyone else in any case. Absent rather than empty, so the strip spends no divider on it.
 */
function releaseProps(
  slug: string,
  session: Session,
  phase: SessionPhase,
  canManageMessages: boolean,
) {
  if (!canManageMessages) return {};
  const release = sessionRelease(session, phase);
  if (release === null) return {};
  // The chip's panel answers for whichever message the phase put in play, and the two post
  // different payloads, so the message type picks the component that owns its mutations.
  return {
    release:
      release.kind === "wrapUp" ? (
        <WrapUpChip slug={slug} session={session} release={release} />
      ) : (
        <ReminderChip slug={slug} session={session} release={release} />
      ),
  };
}

/** The strip's recording slot, absent rather than empty so no divider stands against nothing. */
function recordingProps(slug: string, session: Session, canManage: boolean) {
  if (!statesRecording(session, canManage)) return {};
  return {
    recording: (
      <RecordingChip slug={slug} session={session} canManage={canManage} />
    ),
  };
}

/** The quietest reading of every optional prop: no rights, nothing to contribute with, no map.
 *  One object rather than a default per parameter, which eslint counts as a branch each. */
const QUIET = {
  canManage: false,
  canManageSchedule: false,
  canQueueTopics: false,
  canManageMessages: false,
  mode: "contribute",
  rail: null,
} as const satisfies Partial<Props>;

export function SessionCard(props: Readonly<Props>) {
  const {
    slug,
    session,
    phase,
    statusChip,
    actions,
    headerActions,
    canManage,
    canManageSchedule,
    canQueueTopics,
    canManageMessages,
    mode,
    rail,
    renderSlotActions,
  } = { ...QUIET, ...props };
  const inPlaceWindow = phase === "upcoming" || phase === "awaiting";
  const sheetProps = {
    slug,
    session,
    canManage,
    canManageSchedule,
    canQueueTopics,
    mode,
    ...(renderSlotActions ? { renderSlotActions } : {}),
  };
  return (
    <article className="border-border-strong bg-surface flex flex-col gap-3 rounded border p-4 shadow-sm">
      <SessionCardHeader
        session={session}
        statusChip={statusChip}
        headerActions={headerActions}
      />
      <SessionFacts
        session={session}
        phase={phase}
        {...releaseProps(slug, session, phase, canManageMessages)}
        {...recordingProps(slug, session, canManage)}
      />
      {/* Above the run sheet, not below it: where the session meets and how to get in answers a
          question only worth asking while the session is still ahead. */}
      {inPlaceWindow && (
        <SessionPlace
          place={session.place}
          scheduledAt={session.scheduledAt}
          hasTime={session.hasTime}
          durationMinutes={session.durationMinutes}
          status={session.status}
          joinClassName="sm:hidden"
          joinFullWidth
          phaseStatedElsewhere
        />
      )}
      <CardFooter slug={slug} session={session} canManage={canManage} />
      <SessionCardAgenda rail={rail} sheetProps={sheetProps} />
      {actions && <div>{actions}</div>}
    </article>
  );
}
