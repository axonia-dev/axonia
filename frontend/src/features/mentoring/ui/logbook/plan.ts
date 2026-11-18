import type {
  Journal,
  PlannedMeeting,
} from "@/features/mentoring/domain/mentoring";
import type { Meeting } from "@/features/mentoring/ui/logbook/meetings";

export const MAX_MEETING_TOPICS = 5;

const PLAN_WINDOW_DAYS = 366;

/** The meetings still ahead, today's included, soonest first. */
export function upcomingOf(journal: Journal): PlannedMeeting[] {
  return journal.meetings.filter((meeting) => !meeting.passed);
}

/** The planned days that passed with no note yet, newest first. */
export function owedOf(journal: Journal): PlannedMeeting[] {
  return journal.meetings.filter((meeting) => meeting.passed).reverse();
}

/** The topics a meeting planned for that day means to cover; none when nothing is planned. */
export function plannedTopicsOn(journal: Journal, day: string): number[] {
  return journal.meetings.find((meeting) => meeting.on === day)?.topicIds ?? [];
}

/** The soonest upcoming day that names the topic, or null. */
export function plannedDayOf(journal: Journal, topicId: number): string | null {
  return (
    upcomingOf(journal).find((meeting) => meeting.topicIds.includes(topicId))
      ?.on ?? null
  );
}

/** The last day a meeting can be planned for. */
export function latestPlannableDay(today: string): string {
  const day = new Date(today);
  day.setUTCDate(day.getUTCDate() + PLAN_WINDOW_DAYS);
  return day.toISOString().slice(0, 10);
}

export type Behind =
  | { kind: "owed"; meeting: PlannedMeeting }
  | { kind: "held"; meeting: Meeting };

/** What lies below Today: meetings owed notes and meetings held, newest first. */
export function behindOf(owed: PlannedMeeting[], held: Meeting[]): Behind[] {
  const entries: Behind[] = [
    ...owed.map((meeting) => ({ kind: "owed" as const, meeting })),
    ...held.map((meeting) => ({ kind: "held" as const, meeting })),
  ];
  return entries.sort((a, b) => b.meeting.on.localeCompare(a.meeting.on));
}
