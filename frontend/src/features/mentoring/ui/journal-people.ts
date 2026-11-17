import type {
  Journal,
  MentoringRole,
  Note,
  PairingSummary,
} from "@/features/mentoring/domain/mentoring";

export function partnerRole(pairing: PairingSummary): string {
  return pairing.myRole === "MENTEE" ? "Mentor" : "Mentee";
}

/** Only two people write in a journal: a note that is not the reader's is the partner's. */
export function authorRole(note: Note, myRole: MentoringRole): MentoringRole {
  if (note.mine) return myRole;
  return myRole === "MENTOR" ? "MENTEE" : "MENTOR";
}

export function notesBy(journal: Journal, role: MentoringRole): Note[] {
  return journal.notes.filter(
    (note) => authorRole(note, journal.pairing.myRole) === role,
  );
}

/** Notes in the order of the meetings they are about, then of their writing. */
export function inMeetingOrder(notes: readonly Note[]): Note[] {
  const ordered = [...notes];
  ordered.sort(
    (a, b) =>
      a.meetingOn.localeCompare(b.meetingOn) ||
      a.writtenAt.localeCompare(b.writtenAt),
  );
  return ordered;
}

/** Each role keeps one colour across the trail, the stats and the logbook. */
export const ROLE_INK: Record<MentoringRole, string> = {
  MENTOR: "var(--mentor)",
  MENTEE: "var(--mentee)",
};

export function notesNaming(journal: Journal, topicId: number): Note[] {
  return journal.notes.filter((note) => note.topicIds.includes(topicId));
}

export function plural(count: number, one: string, many: string): string {
  return `${count} ${count === 1 ? one : many}`;
}

/** The meetings held: one per distinct day a note names. */
export function meetingCount(journal: Journal): number {
  return new Set(journal.notes.map((note) => note.meetingOn)).size;
}
