import type { ReactNode } from "react";

import type { Journal } from "@/features/mentoring/domain/mentoring";
import {
  meetingCount,
  notesBy,
  ROLE_INK,
} from "@/features/mentoring/ui/journal-people";
import {
  dayOfMonth,
  monthOf,
  weekdayOf,
} from "@/features/mentoring/ui/meeting-day";

function Stat({
  value,
  label,
  children,
}: Readonly<{ value: string; label: string; children?: ReactNode }>) {
  return (
    <li className="bg-surface-sunken flex min-w-0 grow flex-col rounded-2xl px-3.5 py-2.5 sm:min-w-28">
      <span className="font-display text-foreground text-2xl leading-none font-bold tabular-nums">
        {value}
      </span>{" "}
      <span className="text-muted-foreground mt-1 text-xs">{label}</span>
      {children}
    </li>
  );
}

/** Both people's share of the notes as two colours, never as a count per person. */
function SplitBar({ journal }: Readonly<{ journal: Journal }>) {
  const mentor = notesBy(journal, "MENTOR").length;
  const total = journal.notes.length;
  return (
    <span
      aria-hidden
      data-testid="notes-split"
      className="mt-2 flex h-2 w-full overflow-hidden rounded-full"
    >
      <span
        style={{
          width: `${(mentor / total) * 100}%`,
          background: ROLE_INK.MENTOR,
        }}
      />
      <span className="flex-1" style={{ background: ROLE_INK.MENTEE }} />
    </span>
  );
}

function NextMeeting({ journal }: Readonly<{ journal: Journal }>) {
  const [next, ...later] = journal.meetings;
  if (next === undefined) return null;
  const upcomingLater = later.length;
  return (
    <li className="col-span-2 flex grow items-center gap-2.5 rounded-2xl bg-[color-mix(in_srgb,var(--warning)_16%,var(--surface))] py-2 pr-3.5 pl-2">
      <span
        aria-hidden
        className="bg-surface w-10 overflow-hidden rounded-lg text-center shadow-[0_1px_0_var(--border-strong)]"
      >
        <span className="bg-warning text-warning-foreground block text-[9px] leading-[14px] font-bold tracking-[0.08em] uppercase">
          {monthOf(next.on)}
        </span>
        <span className="font-display text-foreground block text-lg leading-[22px] font-bold">
          {dayOfMonth(next.on)}
        </span>
      </span>
      <span className="flex flex-col">
        <span className="text-foreground text-sm font-semibold">
          {next.passed ? "Last meeting" : "Next meeting"}
        </span>
        <span className="text-muted-foreground text-xs">
          {weekdayOf(next.on)} {dayOfMonth(next.on)} {monthOf(next.on)}
          {next.passed && " · passed"}
          {upcomingLater > 0 && ` · ${upcomingLater} more planned`}
        </span>
      </span>
    </li>
  );
}

function linksShared(journal: Journal): number {
  const onTopics = journal.topics.reduce(
    (sum, topic) => sum + topic.pointers.length,
    0,
  );
  const onNotes = journal.notes.reduce(
    (sum, note) => sum + note.pointers.length,
    0,
  );
  return onTopics + onNotes;
}

/** What the trail adds up to: topics reached, meetings, notes, links and the next meeting. */
export function TrailStats({ journal }: Readonly<{ journal: Journal }>) {
  const done = journal.topics.filter((topic) => topic.state === "DONE").length;
  const meetings = meetingCount(journal);
  const notes = journal.notes.length;
  const links = linksShared(journal);
  return (
    <ul
      aria-label="The pairing so far"
      className="grid grid-cols-2 gap-2.5 sm:flex sm:flex-wrap"
    >
      <Stat
        value={`${done} of ${journal.topics.length}`}
        label="topics reached"
      />
      <Stat
        value={String(meetings)}
        label={meetings === 1 ? "meeting" : "meetings"}
      />
      <Stat value={String(notes)} label={notes === 1 ? "note" : "notes"}>
        {notes > 0 && <SplitBar journal={journal} />}
      </Stat>
      <Stat
        value={String(links)}
        label={links === 1 ? "link shared" : "links shared"}
      />
      <NextMeeting journal={journal} />
    </ul>
  );
}
