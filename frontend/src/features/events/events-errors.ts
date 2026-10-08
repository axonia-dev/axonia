import { ApiError } from "@/lib/api-client";

const MESSAGES: Record<string, string> = {
  SERIES_NOT_ACTIVE: "This series is not active, so no session can be opened.",
  SESSION_CANCELLED: "This session has been cancelled.",
  SESSION_ALREADY_HELD: "This session has already been held.",
  SESSION_ALREADY_CLOSED: "This session has already been closed.",
  SESSION_NOT_YET_DUE: "This session cannot be closed before it starts.",
  SESSION_NOT_HELD:
    "A recording or note can only be added once the session is held.",
  ALREADY_PRESENTING: "Already presenting in this slot.",
  NOT_PRESENTING: "Not presenting in this slot.",
  ALREADY_GOING: "Already going to this session.",
  ALREADY_VOTED: "Already voted for this topic.",
  TOPIC_COMMUNITY_MISMATCH: "That topic belongs to a different community.",
  NOT_SCHEDULED: "This topic is not scheduled in any session.",
  OCCURRENCE_IN_PAST:
    "This occurrence is in the past and can no longer be skipped; cancel it instead.",
  // The reminder's window. `now` advances while the schedule dialog sits
  // open, so an organizer can reach the first of these with a pick that was perfectly good
  // when they made it - it has to read as a retry, not as a mistake.
  UPCOMING_RELEASE_IN_PAST: "That time has already passed. Pick a later one.",
  UPCOMING_RELEASE_AFTER_START:
    "The reminder has to go out before the session starts.",
  SESSION_NOT_SCHEDULED:
    "This session is no longer scheduled, so it sends no reminder.",
  SESSION_ALREADY_STARTED:
    "This session has started, so its reminder can no longer be changed.",
};

export function eventsErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.code) {
    const message = MESSAGES[error.code];
    if (message) {
      return message;
    }
  }
  return "Something went wrong. Please try again.";
}
