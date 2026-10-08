import { describe, expect, it } from "vitest";

import { eventsErrorMessage } from "@/features/events/events-errors";
import { ApiError } from "@/lib/api-client";

describe("eventsErrorMessage", () => {
  it("maps known conflict codes", () => {
    expect(
      eventsErrorMessage(new ApiError(409, "x", "SESSION_CANCELLED")),
    ).toMatch(/cancelled/i);
    expect(
      eventsErrorMessage(new ApiError(409, "x", "ALREADY_PRESENTING")),
    ).toMatch(/already presenting/i);
    expect(
      eventsErrorMessage(new ApiError(409, "x", "NOT_PRESENTING")),
    ).toMatch(/not presenting/i);
    expect(eventsErrorMessage(new ApiError(409, "x", "ALREADY_GOING"))).toMatch(
      /already going/i,
    );
    expect(eventsErrorMessage(new ApiError(409, "x", "ALREADY_VOTED"))).toMatch(
      /already voted/i,
    );
    expect(
      eventsErrorMessage(new ApiError(409, "x", "SERIES_NOT_ACTIVE")),
    ).toMatch(/not active/i);
    expect(
      eventsErrorMessage(new ApiError(409, "x", "SESSION_ALREADY_CLOSED")),
    ).toMatch(/already been closed/i);
    expect(
      eventsErrorMessage(new ApiError(409, "x", "SESSION_NOT_YET_DUE")),
    ).toMatch(/before it starts/i);
    expect(
      eventsErrorMessage(new ApiError(409, "x", "OCCURRENCE_IN_PAST")),
    ).toMatch(/in the past/i);
  });

  // The reminder's window. These are the one set of refusals the product
  // expects an organizer to hit in normal use - `now` advances while the schedule dialog sits
  // open - so falling through to the generic message is the exact failure they exist to
  // prevent, and nothing else would go red if a key were misspelled or dropped.
  it("maps the reminder's four window refusals", () => {
    expect(
      eventsErrorMessage(new ApiError(400, "x", "UPCOMING_RELEASE_IN_PAST")),
    ).toMatch(/already passed\. Pick a later one/i);
    expect(
      eventsErrorMessage(
        new ApiError(400, "x", "UPCOMING_RELEASE_AFTER_START"),
      ),
    ).toMatch(/before the session starts/i);
    expect(
      eventsErrorMessage(new ApiError(409, "x", "SESSION_NOT_SCHEDULED")),
    ).toMatch(/no longer scheduled/i);
    expect(
      eventsErrorMessage(new ApiError(409, "x", "SESSION_ALREADY_STARTED")),
    ).toMatch(/has started/i);
  });

  // Deliberately unmapped: the union behind `UpcomingReleaseInput` makes the payload that
  // earns this code unrepresentable, so reaching it means a client that is not this one.
  it("falls back for the ambiguous-payload refusal this client cannot produce", () => {
    expect(
      eventsErrorMessage(new ApiError(400, "x", "UPCOMING_RELEASE_AMBIGUOUS")),
    ).toMatch(/something went wrong/i);
  });

  it("falls back for unknown errors", () => {
    expect(eventsErrorMessage(new ApiError(500, "boom"))).toMatch(
      /something went wrong/i,
    );
    expect(eventsErrorMessage(new Error("nope"))).toMatch(
      /something went wrong/i,
    );
  });
});
