import type { Journal, Note } from "@/features/mentoring/domain/mentoring";
import {
  authorRole,
  inMeetingOrder,
} from "@/features/mentoring/ui/journal-people";

/** A day the pair met, named by the notes written about it. */
export interface Meeting {
  on: string;
  number: number;
  mentorNotes: Note[];
  menteeNotes: Note[];
  /** The topics its notes name, each once, in the order they were first named. */
  topicIds: number[];
}

/** One meeting per distinct meeting day, newest first, numbered from the oldest. */
export function meetingsOf(journal: Journal): Meeting[] {
  const byDay = new Map<string, Note[]>();
  inMeetingOrder(journal.notes).forEach((note) => {
    byDay.set(note.meetingOn, [...(byDay.get(note.meetingOn) ?? []), note]);
  });
  const { myRole } = journal.pairing;
  const meetings = [...byDay.entries()].map(([on, notes], index) => ({
    on,
    number: index + 1,
    mentorNotes: notes.filter((note) => authorRole(note, myRole) === "MENTOR"),
    menteeNotes: notes.filter((note) => authorRole(note, myRole) === "MENTEE"),
    topicIds: [...new Set(notes.flatMap((note) => note.topicIds))],
  }));
  meetings.reverse();
  return meetings;
}
