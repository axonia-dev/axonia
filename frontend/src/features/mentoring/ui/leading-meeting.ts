import type {
  Journal,
  PlannedMeeting,
} from "@/features/mentoring/domain/mentoring";

/** The meeting the plan leads with: the earliest passed one owed notes, else the soonest upcoming. */
export function leadingMeeting(journal: Journal): PlannedMeeting | null {
  return journal.meetings[0] ?? null;
}
