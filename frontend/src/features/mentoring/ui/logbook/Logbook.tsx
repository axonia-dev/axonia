import { useState } from "react";

import type { Journal } from "@/features/mentoring/domain/mentoring";
import {
  FollowNote,
  type MovedNote,
} from "@/features/mentoring/ui/logbook/follow-note";
import { MeetingCard } from "@/features/mentoring/ui/logbook/MeetingCard";
import { meetingsOf } from "@/features/mentoring/ui/logbook/meetings";
import { OwedMeetingCard } from "@/features/mentoring/ui/logbook/OwedMeetingCard";
import {
  behindOf,
  owedOf,
  upcomingOf,
} from "@/features/mentoring/ui/logbook/plan";
import { PlanAhead } from "@/features/mentoring/ui/logbook/PlanAhead";
import { RailRow } from "@/features/mentoring/ui/logbook/RailRow";
import {
  type Compose,
  TodayMarker,
} from "@/features/mentoring/ui/logbook/TodayMarker";
import { useFollowFocus } from "@/features/mentoring/ui/use-follow-focus";

/** A note whose day changed takes focus back on its Edit once it stands in its new card. */
function movedNoteLanding({
  noteId,
  meetingOn,
}: MovedNote): HTMLElement | null {
  return document.querySelector<HTMLElement>(
    `[data-note-id="${noteId}"][data-meeting-on="${meetingOn}"] [data-note-edit]`,
  );
}

/** Write a note takes focus once the removed owed meeting has left the rail. */
function writeNoteLanding(removedOn: string): HTMLElement | null {
  if (document.querySelector(`[data-owed-on="${removedOn}"]`) !== null) {
    return null;
  }
  return document.querySelector<HTMLElement>("[data-write-note]");
}

/** The pair's logbook as a rail split at Today: the plan above, the meetings that passed below. */
export function Logbook({ journal }: Readonly<{ journal: Journal }>) {
  const [compose, setCompose] = useState<Compose | null>(null);
  const followNote = useFollowFocus(journal.notes, movedNoteLanding);
  const landOnWrite = useFollowFocus(journal.meetings, writeNoteLanding);
  const held = meetingsOf(journal);
  const behind = behindOf(journal.writable ? owedOf(journal) : [], held);
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
            <>
              <RailRow on={upcomingOf(journal)[0]?.on} dot="ahead">
                <PlanAhead journal={journal} />
              </RailRow>
              <TodayMarker
                journal={journal}
                compose={compose}
                onWrite={() => write()}
                onClose={() => setCompose(null)}
              />
            </>
          )}
          {behind.map((entry) =>
            entry.kind === "owed" ? (
              <RailRow key={entry.meeting.on} on={entry.meeting.on} dot="owed">
                <OwedMeetingCard
                  journal={journal}
                  meeting={entry.meeting}
                  onAddNote={write}
                  onRemoved={() => landOnWrite(entry.meeting.on)}
                />
              </RailRow>
            ) : (
              <RailRow key={entry.meeting.on} on={entry.meeting.on} dot="met">
                <MeetingCard
                  journal={journal}
                  meeting={entry.meeting}
                  onAddNote={write}
                />
              </RailRow>
            ),
          )}
        </ol>
      </FollowNote.Provider>
      {held.length === 0 && (
        <p className="text-muted-foreground text-sm">
          No notes yet. Each meeting gets a card here once a note names its day.
        </p>
      )}
    </section>
  );
}
