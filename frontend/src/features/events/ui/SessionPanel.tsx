import { ExternalLink, MapPin, X } from "lucide-react";
import { type CSSProperties, useCallback, useEffect, useRef } from "react";
import { Link } from "react-router-dom";

import { IconButton } from "@/components/ui/IconButton";
import { entryDayIso } from "@/features/events/domain/occurrence-day";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { openSpotLabel, timeLabel } from "@/features/events/domain/schedule";
import { JoinControl } from "@/features/events/ui/JoinControl";
import { ProjectedNotice } from "@/features/events/ui/ProjectedNotice";
import { longDayLabel } from "@/features/events/ui/schedule-dates";
import { SessionAgenda } from "@/features/events/ui/SessionAgenda";
import { stillToHappen } from "@/features/events/ui/SessionPlace";
import { SpotsPill } from "@/features/events/ui/SpotsPill";
import type { User } from "@/features/identity/domain/user";
import { cn } from "@/lib/cn";

interface SessionPanelProps {
  entry: ScheduleEntry;
  /** The panel's own id, which the chip that opened it points `aria-controls` at. */
  id: string;
  label: string;
  /** Where the footer link goes, or null for a projection with no route to offer. */
  href: string | null;
  users: Record<string, User>;
  onClose: () => void;
  /** Whether this panel is the one the reader can actually see, and so the one that takes the
   *  focus when it opens. Both panels are mounted at every width; only one is on screen. */
  focusOnOpen?: boolean;
  /** A fixed instant for the tests to judge against; the live control follows the clock without it. */
  now?: Date;
  className?: string;
  style?: CSSProperties;
}

const NO_TIME = "Time to be confirmed";

function endLabel(entry: ScheduleEntry): string | null {
  if (entry.scheduledAt === null || entry.durationMinutes === null) return null;
  const end = new Date(
    new Date(entry.scheduledAt).getTime() + entry.durationMinutes * 60_000,
  );
  return end.toLocaleTimeString("en-GB", {
    hour: "2-digit",
    minute: "2-digit",
  });
}

function zoneLabel(): string {
  const parts = new Intl.DateTimeFormat(undefined, {
    timeZoneName: "short",
  }).formatToParts(new Date());
  return parts.find((p) => p.type === "timeZoneName")?.value ?? "";
}

/** The hours the session runs between, read on the reader's own clock. */
function whenLine(entry: ScheduleEntry): string {
  const start = timeLabel(entry);
  if (start === null) return NO_TIME;
  const end = endLabel(entry);
  const range = end === null ? start : `${start} - ${end}`;
  const zone = zoneLabel();
  return zone === "" ? range : `${range} · ${zone}`;
}

/** The weekday and date the occurrence falls on, named by the one formatter every schedule view
 *  uses, and read off the same date the chip's own cell is picked by. */
function dayLine(entry: ScheduleEntry): string | null {
  const iso = entryDayIso(entry);
  return iso === "" ? null : longDayLabel(iso);
}

/**
 * Where the session meets: the room's own address, and the joining details a guest needs to get
 * into it. The address is stated whatever the way in is doing. It answers a different question -
 * which room to walk to - and it stays worth reading on a session that has already happened, which
 * is exactly when `JoinControl` has nothing left to render.
 *
 * The way in itself is never printed here. `JoinControl`, mounted above, is the single authority
 * on it: it withholds the link until five minutes before the start and takes it away once the
 * session is no longer to happen. A plain anchor beside it would hand out the way into a session
 * two weeks off, and print the same URL twice while one is running.
 *
 * The joining details follow the same rule the session page applies, on the same predicate: a
 * passcode for a cancelled, skipped or closed-out session is a spent credential taking up a line
 * with nothing left to open. The location is gated by neither, because "this met in Room 4B" stays
 * worth reading on the record of a session that happened.
 */
function Where({ entry }: Readonly<{ entry: ScheduleEntry }>) {
  const showDetails =
    entry.place.joiningDetails !== null && stillToHappen(entry.status);
  if (entry.place.location === null && !showDetails) {
    return null;
  }
  return (
    <div className="border-border text-muted-foreground border-t pt-3 text-sm">
      <p className="flex items-start gap-2">
        <MapPin aria-hidden size={14} className="mt-0.5 shrink-0" />
        <span className="min-w-0">
          {entry.place.location !== null && (
            <span className="block">{entry.place.location}</span>
          )}
          {showDetails && (
            <span className="mt-1 block text-xs whitespace-pre-line">
              {entry.place.joiningDetails}
            </span>
          )}
        </span>
      </p>
    </div>
  );
}

export function SessionPanel({
  entry,
  id,
  label,
  href,
  users,
  onClose,
  focusOnOpen = false,
  now,
  className,
  style,
}: Readonly<SessionPanelProps>) {
  const panelRef = useRef<HTMLElement>(null);
  const openerRef = useRef<HTMLElement | null>(null);

  // Opening moves the reader into the panel, so the footer's link to the session is one Tab from
  // the chip rather than the width of a month grid away, and a screen reader is told the panel is
  // there at all. The opener is recorded first, so Escape hands them back to the chip the panel
  // they are looking at was opened from.
  //
  // Keyed on the shown entry as well as the surface, because picking a second chip replaces the
  // entry without unmounting the panel or flipping `focusOnOpen`: on the surface alone the effect
  // never runs again, the reader is left behind on the grid from the second pick on, and Escape
  // returns them to the chip of the session before. The entry is the object the selection holds,
  // replaced only by a pick, so a background refetch, a hover or the presenter query resolving all
  // re-render the panel with the same one and cannot take the focus from wherever the reader is.
  useEffect(() => {
    if (!focusOnOpen) return;
    const opener = document.activeElement;
    openerRef.current = opener instanceof HTMLElement ? opener : null;
    panelRef.current?.focus();
  }, [focusOnOpen, entry]);

  /** Dismissing from the keyboard or from the panel's own control hands the reader back to the
   *  chip they came from. A click away is left alone: focus belongs wherever the pointer put it. */
  const dismiss = useCallback(() => {
    const opener = openerRef.current;
    if (opener !== null && opener.isConnected) opener.focus();
    onClose();
  }, [onClose]);

  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if (event.key === "Escape") dismiss();
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [dismiss]);

  const spots = openSpotLabel(entry);
  const day = dayLine(entry);

  return (
    <aside
      ref={panelRef}
      id={id}
      tabIndex={-1}
      data-session-panel
      data-testid="session-panel"
      style={style}
      aria-label={`${label}, session details`}
      className={cn(
        "bg-surface border-border-strong focus-visible:ring-focus-ring flex flex-col overflow-hidden rounded-xl border shadow-lg outline-none focus-visible:ring-2",
        className,
      )}
    >
      {/* The mask fades the last few pixels of the scroll region so a line of the agenda is
          never sliced flat against the footer. */}
      <div className="relative flex flex-col gap-3 overflow-y-auto [mask-image:linear-gradient(to_bottom,black_calc(100%-1.5rem),transparent)] p-4">
        <div className="flex items-start justify-between gap-2">
          <div>
            {day !== null && (
              <p
                data-testid="panel-day"
                className="text-muted-foreground text-[10px] font-bold tracking-widest uppercase"
              >
                {day}
              </p>
            )}
            <h2 className="font-display text-foreground mt-1 text-lg leading-tight font-bold">
              {label}
            </h2>
            <p
              data-testid="panel-when"
              className="text-foreground mt-1 text-sm tabular-nums"
            >
              {whenLine(entry)}
            </p>
          </div>
          <IconButton label="Close" onPress={dismiss}>
            <X aria-hidden size={16} />
          </IconButton>
        </div>

        {spots !== null && (
          <div className="flex flex-wrap items-center gap-1.5">
            <SpotsPill label={spots} />
          </div>
        )}

        {entry.scheduledAt !== null && (
          <JoinControl
            joinUrl={entry.place.joinUrl}
            scheduledAt={entry.scheduledAt}
            hasTime={entry.hasTime}
            stillToHappen={stillToHappen(entry.status)}
            durationMinutes={entry.durationMinutes}
            fullWidth
            statesShut
            {...(now && { now })}
          />
        )}

        {entry.virtual && <ProjectedNotice />}

        <SessionAgenda entry={entry} users={users} />
        <Where entry={entry} />
      </div>

      {href !== null && (
        <div className="border-border bg-surface-sunken/40 border-t px-4 py-3">
          <Link
            to={href}
            className="text-primary inline-flex items-center gap-1.5 text-sm font-semibold"
          >
            {entry.virtual ? "Open the preview" : "Open the session"}
            <ExternalLink aria-hidden size={13} />
          </Link>
        </div>
      )}
    </aside>
  );
}
