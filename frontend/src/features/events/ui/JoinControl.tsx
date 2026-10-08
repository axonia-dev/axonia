import { Video } from "lucide-react";
import { useId } from "react";

import { buttonClass } from "@/components/ui/Button";
import {
  isJoinOpen,
  type JoinPhase,
  joinPhase,
  nextPhaseChange,
} from "@/features/events/domain/join-window";
import {
  rendersNothing,
  worthStating,
} from "@/features/events/ui/join-control-rules";
import { ShutJoin } from "@/features/events/ui/ShutJoin";
import { cn } from "@/lib/cn";
import { useNow, useWakeAt } from "@/lib/use-wake-at";

interface Props {
  joinUrl: string | null;
  scheduledAt: string;
  /** Whether anybody chose an hour for this occurrence. Without one, `scheduledAt` is midnight in
   *  the series' zone rather than a start, and every phase derived from it is fiction. */
  hasTime: boolean;
  /** Whether the session is still going to happen. Read only for an untimed occurrence, which has
   *  no window to shut and so has nothing else that could ever take its way in away. */
  stillToHappen: boolean;
  durationMinutes: number | null;
  /** A fixed instant to judge against. Left out, the control follows the clock and re-renders
   *  itself at each instant its phase turns. */
  now?: Date;
  /** Lets the card render one instance for wide layouts and one for the phone. */
  className?: string;
  /** The phone layout stacks the chip and, once the way in is open, a full width one. */
  fullWidth?: boolean;
  /**
   * Whether to state the shut way in rather than render nothing while it is. A phone card says
   * nothing, because a shut Join would spend one of its few rows repeating the chip above it. A
   * surface a reader opened to ask about one session - the calendar's panel - says it, because
   * there an absent control reads as "this session has no way in" rather than "not yet".
   */
  statesShut?: boolean;
  /**
   * True when the caller already states the join phase itself elsewhere on the page - a status
   * chip in the header, say - so this control's own chip would only repeat it. The join button,
   * the shut state, and every other behaviour are unaffected.
   */
  phaseStatedElsewhere?: boolean;
}

interface ViewProps {
  joinUrl: string;
  label: string | null;
  open: boolean;
  fullWidth: boolean;
  statesShut: boolean;
  className: string | undefined;
}

/** The phases worth naming on the card. A `Map` rather than a `Record`: a bracket lookup reads
 *  as attacker-controlled property access to `security/detect-object-injection`. */
const CHIP_LABELS = new Map<JoinPhase, string>([
  ["soon", "Starting soon"],
  ["open", "Starting soon"],
  ["live", "Live"],
]);

/** A link recorded as blank space is no way in at all, and nothing to offer. */
function trimJoinUrl(joinUrl: string | null): string {
  return joinUrl === null ? "" : joinUrl.trim();
}

interface ChipProps {
  id: string;
  label: string;
  className: string | undefined;
}

/**
 * `role="status"` because the phase now turns while the page is open: somebody watching the
 * schedule at the start of the hour hears the session go live rather than only seeing it.
 * `w-fit` keeps the pill its own width when the card stacks it in a column.
 */
function Chip({ id, label, className }: Readonly<ChipProps>) {
  return (
    <span
      id={id}
      role="status"
      className={cn(
        "bg-primary/10 text-primary-strong flex w-fit shrink-0 items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-semibold",
        className,
      )}
    >
      <span aria-hidden className="size-1.5 rounded-full bg-current" />
      {label}
    </span>
  );
}

interface LinkProps {
  joinUrl: string;
  describedBy: string | undefined;
  fullWidth: boolean;
  className: string | undefined;
}

function JoinLink({
  joinUrl,
  describedBy,
  fullWidth,
  className,
}: Readonly<LinkProps>) {
  return (
    <a
      href={joinUrl}
      target="_blank"
      rel="noreferrer"
      aria-describedby={describedBy}
      className={cn(
        buttonClass("primary", fullWidth ? "md" : "sm"),
        "motion-safe:animate-[signal-pulse_2.2s_ease-out_infinite]",
        fullWidth && "w-full",
        className,
      )}
    >
      <Video aria-hidden size={fullWidth ? 15 : 13} />
      Join now
    </a>
  );
}

/**
 * The chip is rendered here rather than inside each arm so that its DOM node survives the turn
 * from shut to open. A live region that is torn down and rebuilt announces nothing.
 */
function JoinControls({
  joinUrl,
  label,
  open,
  fullWidth,
  statesShut,
  className,
}: Readonly<ViewProps>) {
  const chipId = useId();
  const hintId = useId();
  const chip = label === null ? undefined : chipId;

  return (
    <>
      {label !== null && (
        <Chip id={chipId} label={label} className={className} />
      )}
      {open && (
        <JoinLink
          joinUrl={joinUrl}
          describedBy={chip}
          fullWidth={fullWidth}
          className={className}
        />
      )}
      {!open && (!fullWidth || statesShut) && (
        <ShutJoin
          describedBy={chip === undefined ? hintId : `${chip} ${hintId}`}
          hintId={hintId}
          roomy={fullWidth}
          className={className}
        />
      )}
    </>
  );
}

interface Judged {
  scheduledAt: string;
  durationMinutes: number | null;
  now: Date;
}

/**
 * What the clock has to say about this occurrence, and - for one nobody chose an hour for -
 * nothing at all.
 *
 * An untimed occurrence stores midnight in its series' zone, so every phase read off that instant
 * is an assertion nobody made: the chip said "Starting soon" at 23:55 and "Live" at midnight,
 * beside a card reading "Time to be confirmed". Suppressed rather than guessed, and the way in is
 * offered outright rather than shut behind a five-minute window that has no start to count back
 * from: withholding it would leave a member with no way in on the day, which is worse than
 * offering one a little early. What holds that open offer to sessions it is still for is
 * {@link offersNoWayIn}, not the clock.
 */
function phaseView(hasTime: boolean, judged: Readonly<Judged>) {
  if (!hasTime) {
    return { phase: null, label: null, open: true, wakeAt: null };
  }
  const phase = joinPhase(judged);
  return {
    phase,
    label: CHIP_LABELS.get(phase) ?? null,
    open: isJoinOpen(phase),
    wakeAt: nextPhaseChange(judged),
  };
}

/**
 * The way into the session, open only for the window it is for: shut until five minutes before the
 * start, open through the session, shut again once it has ended.
 */
export function JoinControl({
  joinUrl,
  scheduledAt,
  hasTime,
  stillToHappen,
  durationMinutes,
  now,
  className,
  fullWidth = false,
  statesShut = false,
  phaseStatedElsewhere = false,
}: Readonly<Props>) {
  const link = trimJoinUrl(joinUrl);
  const clock = useNow();
  const judged = { scheduledAt, durationMinutes, now: now ?? clock };
  const { phase, label, open, wakeAt } = phaseView(hasTime, judged);
  // Neutralised once, here, rather than threaded as a second flag through the view and
  // `saysNothing`: a suppressed chip is indistinguishable from one this phase never had.
  const chipLabel = phaseStatedElsewhere ? null : label;
  const following = link !== "" && now === undefined;
  const states = worthStating(statesShut, phase, stillToHappen);

  useWakeAt(following ? wakeAt : null);

  if (
    rendersNothing({
      link,
      hasTime,
      stillToHappen,
      fullWidth,
      stance: { open, label: chipLabel, statesShut: states },
    })
  ) {
    return null;
  }

  return (
    <JoinControls
      joinUrl={link}
      label={chipLabel}
      open={open}
      fullWidth={fullWidth}
      statesShut={states}
      className={className}
    />
  );
}
