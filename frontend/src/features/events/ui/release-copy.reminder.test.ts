import { describe, expect, it } from "vitest";

import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { ReleaseStatus, Session } from "@/features/events/domain/session";
import { upcomingCopy, wrapUpCopy } from "@/features/events/ui/release-copy";

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

// The reminder shares all four state functions with the wrap-up and differs only in the noun
// and in the one OFF subline that genuinely reads differently, so these assert exactly that:
// the noun everywhere, the OFF subline, and that it reads the session's OWN reminder intent
// rather than the wrap-up's. Everything below the noun is already covered above.
const aReminderSession = (overrides: Partial<Session> = {}) =>
  aSession({
    status: "SCHEDULED",
    // The reminder's intent sits before this start, which is the ordinary case. The stranded
    // one, where a reschedule has left it after the start, is its own describe below.
    scheduledAt: "2026-03-14T18:00:00Z",
    // Deliberately different from wrapUpReleaseAt above: crossing the two fields has to fail.
    upcomingReleaseAt: "2026-03-10T14:30:00Z",
    ...overrides,
  });

describe("upcomingCopy - the message is called the reminder, in every state", () => {
  it("says plainly there is no reminder, and offers to remind rather than to send one", () => {
    const copy = upcomingCopy(
      aReminderSession({ upcomingReleaseAt: null }),
      aStatus({ state: "OFF", recipientCount: 14 }),
    );

    expect(copy.statement).toBe("No reminder for this session.");
    expect(copy.subline).toBe("Turn it on to remind 14 members.");
  });

  it("states the reminder's own release instant, not the wrap-up's", () => {
    const copy = upcomingCopy(aReminderSession(), aStatus());

    // 2026-03-10T14:30Z is 7:30 AM under the suite's PDT pin; the wrap-up's own instant on
    // the same session is Thu, Mar 12, 2:00 AM, so a crossed field shows up here.
    expect(copy.statement).toBe(
      "The reminder goes out Tue, Mar 10, 7:30 AM PDT.",
    );
    expect(copy.subline).toMatch(/^To 14 members/);
  });

  it("names the reminder while it is queued, with the shared progress sublines", () => {
    expect(
      upcomingCopy(aReminderSession(), aStatus({ state: "QUEUED" })),
    ).toEqual({
      statement: "The reminder is queued.",
      subline: "Sending to 14 members, usually within a minute.",
    });
    expect(
      upcomingCopy(aReminderSession(), aStatus({ state: "QUEUED", sent: 9 }))
        .subline,
    ).toBe("Sending to 14 members · 9 sent so far.");
  });

  it("names the reminder once it has gone out, keeping the wrap-up's delivery sublines", () => {
    const copy = upcomingCopy(
      aReminderSession(),
      aStatus({ state: "SENT", sent: 12, couldNotReach: 2 }),
    );

    expect(copy.statement).toBe(
      "The reminder went out Tue, Mar 10, 7:30 AM PDT.",
    );
    expect(copy.subline).toBe(
      "Delivered to 12 members · 2 could not be reached.",
    );
  });

  it("never reads as a success when every recipient was suppressed", () => {
    const copy = upcomingCopy(
      aReminderSession(),
      aStatus({ state: "SENT", sent: 0, couldNotReach: 0 }),
    );

    expect(copy.subline).toBe(
      "Nobody received it. Every member had opted out.",
    );
    expect(copy.subline).not.toMatch(/Delivered to/);
  });

  it("falls back to the noun without an instant on shapes the contract does not allow", () => {
    const noIntent = aReminderSession({ upcomingReleaseAt: null });

    expect(upcomingCopy(noIntent, aStatus()).statement).toBe(
      "The reminder is scheduled.",
    );
    expect(upcomingCopy(noIntent, aStatus({ state: "SENT" })).statement).toBe(
      "The reminder was sent.",
    );
  });
});

// Only reachable after a reschedule, which is why no state test above produces it: the
// backend deliberately preserves an intent an organizer set by hand rather
// than correcting it, and `SessionServiceTest.update_leavesADeliberateReminderEvenWhenItNow
// FallsAfterTheNewStart` pins that it does. What it cannot do is fire: the producer needs
// `scheduled_at > now` and `upcoming_release_at <= now` at once, so this reminder is never
// claimed and never sent. The card saying so is the whole of the promised report.
describe("upcomingCopy - a reminder a reschedule left after the session's start", () => {
  it("says it will not go out, and names both remedies", () => {
    const copy = upcomingCopy(
      aReminderSession({ upcomingReleaseAt: "2026-03-15T09:00:00Z" }),
      aStatus({ state: "SCHEDULED" }),
    );

    expect(copy.statement).toBe(
      "The reminder is set for after the session starts.",
    );
    // The session starts 2026-03-14T18:00Z, which is 11:00 AM under the suite's PDT pin.
    expect(copy.subline).toBe(
      "It will not go out. Pick a time before Sat, Mar 14, 11:00 AM PDT, or send it now.",
    );
  });

  it("counts an intent exactly at the start as stranded, matching the server's own window", () => {
    const copy = upcomingCopy(
      aReminderSession({ upcomingReleaseAt: "2026-03-14T18:00:00Z" }),
      aStatus({ state: "SCHEDULED" }),
    );

    // UpcomingReleaseWindow admits an instant strictly before the start, so one landing on it
    // is refused there and has to read as stranded here.
    expect(copy.statement).toBe(
      "The reminder is set for after the session starts.",
    );
  });

  it("leaves an intent still ahead of the start reading as an ordinary schedule", () => {
    const copy = upcomingCopy(
      aReminderSession({ upcomingReleaseAt: "2026-03-14T17:59:00Z" }),
      aStatus({ state: "SCHEDULED" }),
    );

    expect(copy.statement).toBe(
      "The reminder goes out Sat, Mar 14, 10:59 AM PDT.",
    );
  });
});

// The state comes from the outbox and the instant from the stored intent. A reschedule moves
// the intent and leaves the terminal outbox row, so the two can end up describing a send that
// already happened with a date that has not arrived.
describe("upcomingCopy - a sent reminder whose stored instant no longer describes the send", () => {
  it("drops a date in the future rather than claiming a send happened then", () => {
    const copy = upcomingCopy(
      aReminderSession({
        scheduledAt: "2099-01-20T18:00:00Z",
        upcomingReleaseAt: "2099-01-17T09:00:00Z",
      }),
      aStatus({ state: "SENT", sent: 12, couldNotReach: 0 }),
    );

    expect(copy.statement).toBe("The reminder was sent.");
    expect(copy.statement).not.toMatch(/went out/);
    // The delivery facts are the outbox's own and stay exactly as they were.
    expect(copy.subline).toBe("Delivered to 12 members.");
  });

  it("drops a date the session's own start has overtaken", () => {
    const copy = upcomingCopy(
      aReminderSession({
        scheduledAt: "2026-03-09T10:00:00Z",
        upcomingReleaseAt: "2026-03-10T14:30:00Z",
      }),
      aStatus({ state: "SENT", sent: 12 }),
    );

    expect(copy.statement).toBe("The reminder was sent.");
  });

  it("still names the instant of a send the stored intent does support", () => {
    const copy = upcomingCopy(
      aReminderSession(),
      aStatus({ state: "SENT", sent: 12 }),
    );

    expect(copy.statement).toBe(
      "The reminder went out Tue, Mar 10, 7:30 AM PDT.",
    );
  });
});

// Of the two rules above, only the one needing the session's start is the reminder's. The
// wrap-up supplies no bound, so that one is inert for it; "a send cannot have happened at an
// instant that has not arrived" needs no bound and holds for any message.
describe("wrapUpCopy - which of the two rules reaches the wrap-up", () => {
  it("still names an instant that falls after the session's own start", () => {
    const copy = wrapUpCopy(
      aSession({
        scheduledAt: "2026-03-09T10:00:00Z",
        wrapUpReleaseAt: "2026-03-12T09:00:00Z",
      }),
      aStatus({ state: "SCHEDULED" }),
    );

    // A wrap-up goes out *after* the session by construction, so this is its normal case and
    // reading it as stranded would break the shipped card.
    expect(copy.statement).toBe(
      "The wrap-up goes out Thu, Mar 12, 2:00 AM PDT.",
    );
  });

  it("drops a future date on a sent wrap-up too, unreachable as that is today", () => {
    const copy = wrapUpCopy(
      aSession({ wrapUpReleaseAt: "2099-01-17T09:00:00Z" }),
      aStatus({ state: "SENT", sent: 4 }),
    );

    expect(copy.statement).toBe("The wrap-up was sent.");
  });
});
