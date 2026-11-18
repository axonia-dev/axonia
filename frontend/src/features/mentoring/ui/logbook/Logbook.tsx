import { NotebookPen } from "lucide-react";
import { type ReactNode, useEffect, useRef, useState } from "react";

import type { Journal } from "@/features/mentoring/domain/mentoring";
import { leadingMeeting } from "@/features/mentoring/ui/leading-meeting";
import {
  FollowNote,
  type MovedNote,
} from "@/features/mentoring/ui/logbook/follow-note";
import { MeetingCard } from "@/features/mentoring/ui/logbook/MeetingCard";
import { meetingsOf } from "@/features/mentoring/ui/logbook/meetings";
import { NextMeetingTicket } from "@/features/mentoring/ui/logbook/NextMeetingTicket";
import { NoteComposer } from "@/features/mentoring/ui/logbook/NoteComposer";
import {
  dayOfMonth,
  monthOf,
  weekdayOf,
} from "@/features/mentoring/ui/meeting-day";
import { useFollowFocus } from "@/features/mentoring/ui/use-follow-focus";
import { cn } from "@/lib/cn";

/** A request to write: the meeting day it is for, and a count so asking twice still moves focus. */
interface Compose {
  on?: string;
  asked: number;
}

function When({ on }: Readonly<{ on?: string | undefined }>) {
  if (on === undefined) return <span aria-hidden className="hidden sm:block" />;
  return (
    <p aria-hidden className="hidden pt-3 text-right sm:block">
      <span className="font-display text-foreground block text-2xl leading-none font-bold">
        {dayOfMonth(on)}
      </span>
      <span className="text-muted-foreground text-xs">
        {monthOf(on)} · {weekdayOf(on)}
      </span>
    </p>
  );
}

function RailRow({
  on,
  dot,
  children,
}: Readonly<{
  on?: string | undefined;
  dot: "met" | "ahead";
  children: ReactNode;
}>) {
  return (
    <li className="grid grid-cols-1 gap-x-4 sm:grid-cols-[5.5rem_minmax(0,1fr)]">
      <When on={on} />
      <div className="border-border-strong relative border-l-2 border-dashed pb-5 pl-5">
        <span
          aria-hidden
          className={cn(
            "absolute top-4 -left-[9px] size-4 rounded-full shadow-[0_0_0_4px_var(--surface)]",
            dot === "met"
              ? "bg-warning"
              : "bg-surface border-warning border-2 border-dashed",
          )}
        />
        {children}
      </div>
    </li>
  );
}

function Composer({
  journal,
  compose,
  onOpen,
  onClose,
}: Readonly<{
  journal: Journal;
  compose: Compose | null;
  onOpen: () => void;
  onClose: () => void;
}>) {
  const box = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const wasOpen = useRef(false);
  useEffect(() => {
    if (compose === null) {
      if (wasOpen.current) trigger.current?.focus();
      wasOpen.current = false;
      return;
    }
    wasOpen.current = true;
    box.current?.scrollIntoView?.({ block: "nearest" });
    box.current?.querySelector("textarea")?.focus();
  }, [compose]);
  if (compose === null) {
    return (
      <button
        ref={trigger}
        type="button"
        onClick={onOpen}
        className="border-border-strong bg-background text-muted-foreground hover:border-control-edge focus-visible:ring-focus-ring flex w-full cursor-pointer items-center gap-2 rounded-2xl border px-4 py-3 text-left text-sm outline-none focus-visible:ring-2"
      >
        <NotebookPen aria-hidden className="size-4" />
        Write a note
      </button>
    );
  }
  return (
    <div ref={box}>
      <NoteComposer
        key={`${compose.on ?? journal.today}-${compose.asked}`}
        journal={journal}
        {...(compose.on === undefined ? {} : { meetingOn: compose.on })}
        onDone={onClose}
      />
    </div>
  );
}

/** A note whose day changed takes focus back on its Edit once it stands in its new card. */
function movedNoteLanding({
  noteId,
  meetingOn,
}: MovedNote): HTMLElement | null {
  return document.querySelector<HTMLElement>(
    `[data-note-id="${noteId}"][data-meeting-on="${meetingOn}"] [data-note-edit]`,
  );
}

/** The pair's notes laid out in time: the next meeting waiting on top, then one card per meeting held. */
export function Logbook({ journal }: Readonly<{ journal: Journal }>) {
  const [compose, setCompose] = useState<Compose | null>(null);
  const followNote = useFollowFocus(journal.notes, movedNoteLanding);
  const meetings = meetingsOf(journal);
  const write = (on?: string) =>
    setCompose((current) => ({
      ...(on === undefined ? {} : { on }),
      asked: (current?.asked ?? 0) + 1,
    }));
  return (
    <section aria-labelledby="journal-logbook" className="flex flex-col gap-3">
      <h2
        id="journal-logbook"
        className="font-display text-foreground text-base font-semibold"
      >
        Logbook
      </h2>
      <FollowNote.Provider value={followNote}>
        <ol className="flex flex-col">
          {journal.writable && (
            <RailRow on={leadingMeeting(journal)?.on} dot="ahead">
              <div className="flex flex-col gap-3">
                <NextMeetingTicket journal={journal} onWriteNotes={write} />
                <Composer
                  journal={journal}
                  compose={compose}
                  onOpen={() => write()}
                  onClose={() => setCompose(null)}
                />
              </div>
            </RailRow>
          )}
          {meetings.map((meeting) => (
            <RailRow key={meeting.on} on={meeting.on} dot="met">
              <MeetingCard
                journal={journal}
                meeting={meeting}
                onAddNote={write}
              />
            </RailRow>
          ))}
        </ol>
      </FollowNote.Provider>
      {meetings.length === 0 && (
        <p className="text-muted-foreground text-sm">
          No notes yet. Each meeting gets a card here once a note names its day.
        </p>
      )}
    </section>
  );
}
