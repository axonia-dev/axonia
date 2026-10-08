import { describe, expect, it } from "vitest";

import {
  nextUpOf,
  recapOf,
  STRIP_SIZE,
} from "@/features/community/domain/session-recap";
import { anActionItem } from "@/test/action-item-fixture";
import { aScheduledSession, aSection, aSlot } from "@/test/session-fixture";

const NOW = new Date("2026-09-18T12:00:00Z");

function held(overrides = {}) {
  return aScheduledSession({
    status: "HELD",
    scheduledAt: "2026-09-15T18:00:00Z",
    ...overrides,
  });
}

function recap(session = held(), rest = {}) {
  return recapOf({
    session,
    actionPoints: [],
    resourceCount: 0,
    meId: null,
    now: NOW,
    ...rest,
  });
}

describe("recapOf", () => {
  it("reads every talk of every section, in running order", () => {
    const session = held({
      sections: [
        aSection({
          id: 1,
          slots: [aSlot({ id: 1, title: "Cutting our p95 in half" })],
        }),
        aSection({
          id: 2,
          slots: [
            aSlot({ id: 2, title: "Incident review walkthrough" }),
            aSlot({ id: 3, title: "Who owns the golden path" }),
          ],
        }),
      ],
    });

    expect(recap(session)?.talks.map((talk) => talk.title)).toEqual([
      "Cutting our p95 in half",
      "Incident review walkthrough",
      "Who owns the golden path",
    ]);
  });

  it("counts a round table as a section that held no talk", () => {
    const session = held({ sections: [aSection({ slots: [] })] });

    const actual = recap(session);

    expect(actual?.talks).toEqual([]);
    expect(actual?.strip).toEqual([]);
  });

  it("carries each talk's own section accent, so the strip reads as the agenda did", () => {
    const session = held({
      sections: [
        aSection({
          sectionType: "Lightning Talk",
          sectionTypeIcon: "zap",
          sectionTypeAccentColor: "#d9871f",
        }),
      ],
    });

    expect(recap(session)?.talks[0]).toMatchObject({
      sectionLabel: "Lightning Talk",
      sectionTypeId: 2,
      icon: "zap",
      accentColor: "#d9871f",
    });
  });

  it("holds the strip to three talks and counts the rest", () => {
    const slots = [1, 2, 3, 4, 5, 6].map((id) =>
      aSlot({ id, title: `Talk ${id}` }),
    );
    const session = held({ sections: [aSection({ slots })] });

    const actual = recap(session);

    expect(actual?.strip).toHaveLength(STRIP_SIZE);
    expect(actual?.moreTalks).toBe(2);
    expect(actual?.talks).toHaveLength(6);
  });

  it("leaves the talk the headline already names out of the strip", () => {
    const session = held({
      sections: [
        aSection({
          slots: [
            aSlot({ id: 1, title: "Cutting our p95 in half" }),
            aSlot({ id: 2, title: "Incident review walkthrough" }),
          ],
        }),
      ],
    });

    const actual = recap(session);

    expect(actual?.title).toBe("Cutting our p95 in half");
    expect(actual?.strip.map((talk) => talk.title)).toEqual([
      "Incident review walkthrough",
    ]);
  });

  it("draws no strip for a session that held one talk", () => {
    const session = held({
      sections: [
        aSection({
          slots: [aSlot({ id: 1, title: "Cutting our p95 in half" })],
        }),
      ],
    });

    expect(recap(session)?.strip).toEqual([]);
  });

  it("keeps every talk in the strip when the headline is the session's own name", () => {
    const session = held({
      title: "October guild",
      sections: [
        aSection({
          slots: [aSlot({ id: 1, title: null }), aSlot({ id: 2, title: null })],
        }),
      ],
    });

    const actual = recap(session);

    expect(actual?.title).toBe("October guild");
    expect(actual?.strip).toHaveLength(2);
  });

  it("credits each presenter once, in the order they first presented", () => {
    const session = held({
      sections: [
        aSection({
          slots: [
            aSlot({ id: 1, presenterUserIds: ["carol", "bob"] }),
            aSlot({ id: 2, presenterUserIds: ["bob"] }),
            aSlot({ id: 3, presenterUserIds: ["dana"] }),
          ],
        }),
      ],
    });

    expect(recap(session)?.presenterUserIds).toEqual(["carol", "bob", "dana"]);
  });

  it("reads as fresh while the session is recent", () => {
    expect(recap(held({ scheduledAt: "2026-09-15T18:00:00Z" }))?.stale).toBe(
      false,
    );
  });

  /** A loud hero about a session from months ago tells a visitor the community is alive when it is not. */
  it("reads as stale once the session is further back than the freshness window", () => {
    expect(recap(held({ scheduledAt: "2026-05-12T18:00:00Z" }))?.stale).toBe(
      true,
    );
  });

  it("counts the action points still open, ignoring the ones already settled", () => {
    // given
    const actionPoints = [
      anActionItem({ id: 1, outcome: "OPEN" }),
      anActionItem({ id: 2, outcome: "DONE" }),
      anActionItem({ id: 3, outcome: "DROPPED" }),
      anActionItem({ id: 4, outcome: "OPEN" }),
    ];

    // when
    const actual = recap(held(), { actionPoints });

    // then
    expect(actual.openActionPoints).toBe(2);
  });

  it("singles out an open action point that is the reader's own", () => {
    // given
    const actionPoints = [
      anActionItem({ id: 1, assigneeUserId: "bob" }),
      anActionItem({ id: 2, assigneeUserId: "carol" }),
    ];

    // when
    const actual = recap(held(), { actionPoints, meId: "carol" });

    // then
    expect(actual.mine?.id).toBe(2);
  });

  it("singles out nothing when the reader's own action point is already done", () => {
    // given
    const actionPoints = [
      anActionItem({ id: 1, assigneeUserId: "carol", outcome: "DONE" }),
    ];

    // when
    const actual = recap(held(), { actionPoints, meId: "carol" });

    // then
    expect(actual.mine).toBeNull();
  });

  it("falls back to the talks' own lengths when the session records no duration", () => {
    const session = held({
      durationMinutes: null,
      sections: [
        aSection({
          slots: [
            aSlot({ id: 1, durationMinutes: 10 }),
            aSlot({ id: 2, durationMinutes: 25 }),
          ],
        }),
      ],
    });

    expect(recap(session)?.totalMinutes).toBe(35);
  });

  it("prefers the session's own recorded duration over the sum of its talks", () => {
    const session = held({
      durationMinutes: 50,
      sections: [aSection({ slots: [aSlot({ durationMinutes: 10 })] })],
    });

    expect(recap(session)?.totalMinutes).toBe(50);
  });
});

describe("nextUpOf", () => {
  it("has nothing to say when nothing is scheduled", () => {
    expect(nextUpOf(null)).toBeNull();
  });

  it("separates the talks already booked from the spots still open", () => {
    const session = aScheduledSession({
      sections: [
        aSection({
          slots: [
            aSlot({ id: 1, presenterUserIds: ["bob"] }),
            aSlot({ id: 2, presenterUserIds: [] }),
            aSlot({ id: 3, presenterUserIds: [] }),
          ],
        }),
      ],
    });

    expect(nextUpOf(session)).toMatchObject({ booked: 1, openSpots: 2 });
  });

  it("reads a session with no agenda at all as open rather than as full", () => {
    const actual = nextUpOf(aScheduledSession({ sections: [] }));

    expect(actual).toMatchObject({ booked: 0, openSpots: 0, empty: true });
  });
});
