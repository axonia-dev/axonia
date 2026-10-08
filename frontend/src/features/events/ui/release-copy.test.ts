import { describe, expect, it } from "vitest";

import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { ReleaseStatus, Session } from "@/features/events/domain/session";
import {
  formatReleaseInstant,
  wrapUpCopy,
} from "@/features/events/ui/release-copy";

function aStatus(overrides: Partial<ReleaseStatus> = {}): ReleaseStatus {
  return {
    state: "SCHEDULED",
    recipientCount: 14,
    sent: 0,
    couldNotReach: 0,
    ...overrides,
  };
}

function aSession(overrides: Partial<Session> = {}): Session {
  return {
    id: 7,
    seriesId: 1,
    communityId: 1,
    status: "HELD",
    title: null,
    occurrenceDate: null,
    scheduledAt: "2026-03-09T10:00:00Z",
    hasTime: true,
    durationMinutes: 60,
    facilitatorUserId: null,
    attendeeCount: 0,
    notes: null,
    recordingUrl: null,
    place: EMPTY_PLACE,
    placeOverride: EMPTY_PLACE,
    seriesPlace: EMPTY_PLACE,
    seriesTimeZone: "Europe/Paris",
    sections: [],
    createdAt: "x",
    updatedAt: "x",
    closedAt: "2026-03-09T12:00:00Z",
    wrapUpReleaseAt: "2026-03-12T09:00:00Z",
    wrapUpStatus: null,
    upcomingReleaseAt: null,
    upcomingStatus: null,
    ...overrides,
  };
}

describe("formatReleaseInstant", () => {
  it("renders the viewer's own wall clock and zone, not a forced UTC reading", () => {
    // Under the suite's TZ=America/Los_Angeles pin, 2026-03-12T09:00:00Z is
    // 2026-03-12T02:00:00-07:00 (PDT began 2026-03-08).
    expect(formatReleaseInstant("2026-03-12T09:00:00Z")).toBe(
      "Thu, Mar 12, 2:00 AM PDT",
    );
  });
});

describe("wrapUpCopy - OFF", () => {
  it("says plainly there is no wrap-up, and names how many it would reach if turned on", () => {
    const copy = wrapUpCopy(
      aSession({ wrapUpReleaseAt: null }),
      aStatus({ state: "OFF", recipientCount: 1 }),
    );

    expect(copy.statement).toBe("No wrap-up for this session.");
    expect(copy.subline).toBe("Turn it on to send one to 1 member.");
  });
});

describe("wrapUpCopy - SCHEDULED", () => {
  it("states the exact instant and names the recipients", () => {
    const copy = wrapUpCopy(
      aSession({ wrapUpReleaseAt: "2026-03-12T09:00:00Z" }),
      aStatus({ state: "SCHEDULED", recipientCount: 14 }),
    );

    expect(copy.statement).toBe(
      "The wrap-up goes out Thu, Mar 12, 2:00 AM PDT.",
    );
    expect(copy.subline).toMatch(/^To 14 members/);
  });

  it("falls back to a generic statement rather than throw on a null release instant", () => {
    // Not a shape the backend contract allows (SCHEDULED implies a release instant), but this
    // stays honest instead of crashing the card if that invariant is ever violated.
    const copy = wrapUpCopy(
      aSession({ wrapUpReleaseAt: null }),
      aStatus({ state: "SCHEDULED", recipientCount: 14 }),
    );

    expect(copy.statement).toBe("The wrap-up is scheduled.");
    expect(copy.subline).toBe("To 14 members.");
  });
});

describe("wrapUpCopy - QUEUED", () => {
  it("shows the generic estimate before any row has landed", () => {
    const copy = wrapUpCopy(
      aSession(),
      aStatus({ state: "QUEUED", recipientCount: 14, sent: 0 }),
    );

    expect(copy.subline).toBe(
      "Sending to 14 members, usually within a minute.",
    );
  });

  it("shows the live count once the sweep has started delivering", () => {
    const copy = wrapUpCopy(
      aSession(),
      aStatus({ state: "QUEUED", recipientCount: 14, sent: 9 }),
    );

    expect(copy.statement).toBe("The wrap-up is queued.");
    expect(copy.subline).toBe("Sending to 14 members · 9 sent so far.");
  });
});

describe("wrapUpCopy - SENT", () => {
  it("reports the delivered count and the unreachable count", () => {
    const copy = wrapUpCopy(
      aSession({ wrapUpReleaseAt: "2026-03-12T09:00:00Z" }),
      aStatus({ state: "SENT", sent: 12, couldNotReach: 2 }),
    );

    expect(copy.statement).toBe(
      "The wrap-up went out Thu, Mar 12, 2:00 AM PDT.",
    );
    expect(copy.subline).toBe(
      "Delivered to 12 members · 2 could not be reached.",
    );
  });

  it("drops the unreachable clause when nobody was unreachable", () => {
    const copy = wrapUpCopy(
      aSession(),
      aStatus({ state: "SENT", sent: 1, couldNotReach: 0 }),
    );

    expect(copy.subline).toBe("Delivered to 1 member.");
  });

  it("never reads as a success when every recipient was suppressed - sent can be 0 in SENT", () => {
    const copy = wrapUpCopy(
      aSession(),
      aStatus({ state: "SENT", sent: 0, couldNotReach: 0 }),
    );

    expect(copy.subline).toBe(
      "Nobody received it. Every member had opted out.",
    );
  });

  it("names the unreachable count too when both suppression and failure happened", () => {
    const copy = wrapUpCopy(
      aSession(),
      aStatus({ state: "SENT", sent: 0, couldNotReach: 3 }),
    );

    expect(copy.subline).toBe(
      "Nobody received it. 3 could not be reached, and anyone else had opted out.",
    );
  });

  it("stays true even when the unreachable count accounts for everyone - no remainder implied", () => {
    const copy = wrapUpCopy(
      aSession(),
      aStatus({ state: "SENT", sent: 0, recipientCount: 3, couldNotReach: 3 }),
    );

    expect(copy.subline).toBe(
      "Nobody received it. 3 could not be reached, and anyone else had opted out.",
    );
  });

  it("falls back to a generic statement when the release instant is unavailable", () => {
    const copy = wrapUpCopy(
      aSession({ wrapUpReleaseAt: null }),
      aStatus({ state: "SENT", sent: 4 }),
    );

    expect(copy.statement).toBe("The wrap-up was sent.");
  });
});
