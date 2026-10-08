import { describe, expect, it } from "vitest";

import type { ReleaseStatus, Session } from "@/features/events/domain/session";
import { releaseChip, sessionRelease } from "@/features/events/ui/release-chip";
import { aScheduledSession } from "@/test/session-fixture";

function status(overrides: Partial<ReleaseStatus> = {}): ReleaseStatus {
  return {
    state: "SCHEDULED",
    recipientCount: 2,
    sent: 0,
    couldNotReach: 0,
    ...overrides,
  };
}

describe("releaseChip", () => {
  it("states a sent message in the success tone", () => {
    expect(releaseChip("Wrap-up", "SENT", false)).toEqual({
      tone: "success",
      label: "Wrap-up sent",
      previewable: true,
    });
  });

  it("states a queued message as a warning, since it is mid-flight", () => {
    expect(releaseChip("Reminder", "QUEUED", false)).toEqual({
      tone: "warning",
      label: "Reminder queued",
      previewable: true,
    });
  });

  it("states a scheduled message plainly", () => {
    expect(releaseChip("Reminder", "SCHEDULED", false)).toEqual({
      tone: "neutral",
      label: "Reminder scheduled",
      previewable: true,
    });
  });

  it("warns that a stranded message needs a time rather than calling it scheduled", () => {
    expect(releaseChip("Reminder", "SCHEDULED", true)).toEqual({
      tone: "warning",
      label: "Reminder needs a time",
      previewable: true,
    });
  });

  it("offers no preview of a message that will never be sent", () => {
    expect(releaseChip("Wrap-up", "OFF", false)).toEqual({
      tone: "neutral",
      label: "Wrap-up off",
      previewable: false,
    });
  });
});

describe("sessionRelease", () => {
  it("reads the wrap-up on a held session", () => {
    const session: Session = {
      ...aScheduledSession(),
      status: "HELD",
      wrapUpStatus: status({ state: "SENT" }),
      upcomingStatus: status({ state: "SENT" }),
    };

    expect(sessionRelease(session, "held")?.chip.label).toBe("Wrap-up sent");
  });

  it("reads the reminder while the session is still ahead", () => {
    const session: Session = {
      ...aScheduledSession(),
      upcomingStatus: status({ state: "SCHEDULED" }),
    };

    expect(sessionRelease(session, "upcoming")?.chip.label).toBe(
      "Reminder scheduled",
    );
  });

  it("still reads a reminder that went out while the session owes a close answer", () => {
    const session: Session = {
      ...aScheduledSession(),
      upcomingStatus: status({ state: "SENT" }),
    };

    expect(sessionRelease(session, "awaiting")?.chip.label).toBe(
      "Reminder sent",
    );
  });

  /**
   * The start has passed, so no instant before it is left to pick and the change menu that would
   * offer one is gone. Stating "Reminder needs a time" there would be a warning with no remedy,
   * which is exactly the state the release card was built to abolish.
   */
  it("says nothing of a reminder the passing start has already settled the fate of", () => {
    const session: Session = {
      ...aScheduledSession(),
      scheduledAt: "2026-03-10T09:00:00Z",
      upcomingReleaseAt: "2026-03-11T09:00:00Z",
      upcomingStatus: status({ state: "SCHEDULED" }),
    };

    expect(sessionRelease(session, "awaiting")).toBeNull();
  });

  it("has nothing to state on a cancelled or skipped session", () => {
    const session: Session = {
      ...aScheduledSession(),
      status: "SKIPPED",
      upcomingStatus: status({ state: "SENT" }),
    };

    expect(sessionRelease(session, "closed")).toBeNull();
  });

  // A null status means "not the detail read", never a state - the same rule ReleaseCard follows.
  it("has nothing to state when the status was never projected", () => {
    expect(
      sessionRelease(
        { ...aScheduledSession(), upcomingStatus: null },
        "upcoming",
      ),
    ).toBeNull();
  });

  it("carries the preview copy of the message type it read", () => {
    const session: Session = {
      ...aScheduledSession(),
      upcomingStatus: status({ state: "SCHEDULED" }),
    };

    expect(sessionRelease(session, "upcoming")?.preview.messageType).toBe(
      "session_upcoming",
    );
  });

  /**
   * The chip's own panel states the release in full, so the sentence the deleted card used to
   * carry has to reach it from here. Without this the panel would have a state and no words for
   * it, which is the split the card existed to prevent.
   */
  it("carries the full statement the panel puts above its answers", () => {
    const session: Session = {
      ...aScheduledSession(),
      upcomingReleaseAt: "2026-03-12T09:00:00Z",
      upcomingStatus: status({ state: "SCHEDULED" }),
    };

    expect(sessionRelease(session, "upcoming")?.copy.statement).toBe(
      "The reminder goes out Thu, Mar 12, 2:00 AM PDT.",
    );
  });

  // The panel's answers post different payloads per message type, so the caller has to be told
  // which one it read without re-deriving the phase rule this module already applied.
  it("names the message type it read, so the caller can offer its own answers", () => {
    const held: Session = {
      ...aScheduledSession(),
      status: "HELD",
      wrapUpStatus: status({ state: "SCHEDULED" }),
    };

    const upcoming: Session = {
      ...aScheduledSession(),
      upcomingStatus: status({ state: "SCHEDULED" }),
    };

    expect(sessionRelease(held, "held")?.kind).toBe("wrapUp");
    expect(sessionRelease(upcoming, "upcoming")?.kind).toBe("reminder");
  });

  /**
   * Answerability is the panel's own question and is not the chip's `previewable`: a suppressed
   * message offers every answer and no preview, and a sent one the exact reverse.
   */
  it("offers answers only while the release is still open to one", () => {
    const answerable = (state: ReleaseStatus["state"]) =>
      sessionRelease(
        { ...aScheduledSession(), upcomingStatus: status({ state }) },
        "upcoming",
      )?.answerable;

    expect(answerable("SCHEDULED")).toBe(true);
    expect(answerable("OFF")).toBe(true);
    expect(answerable("QUEUED")).toBe(false);
    expect(answerable("SENT")).toBe(false);
  });

  it("warns when a reschedule has stranded the reminder past the session's own start", () => {
    const session: Session = {
      ...aScheduledSession(),
      scheduledAt: "2026-03-10T09:00:00Z",
      upcomingReleaseAt: "2026-03-11T09:00:00Z",
      upcomingStatus: status({ state: "SCHEDULED" }),
    };

    expect(sessionRelease(session, "upcoming")?.chip).toMatchObject({
      tone: "warning",
      label: "Reminder needs a time",
    });
  });
});
