import { describe, expect, it } from "vitest";

import { journalOf, noteOf, pairingOf } from "@/features/mentoring/test-data";
import { meetingsOf } from "@/features/mentoring/ui/logbook/meetings";

describe("meetingsOf", () => {
  it("groups notes into meetings, newest first, numbered", () => {
    // given
    const journal = journalOf({
      notes: [
        noteOf({ id: 1, meetingOn: "2026-11-12", topicIds: [3] }),
        noteOf({ id: 2, meetingOn: "2026-10-01", topicIds: [1] }),
        noteOf({
          id: 3,
          meetingOn: "2026-11-12",
          mine: false,
          topicIds: [3, 4],
        }),
      ],
    });

    // when
    const actual = meetingsOf(journal);

    // then
    expect(
      actual.map(({ on, number, topicIds }) => ({ on, number, topicIds })),
    ).toEqual([
      { on: "2026-11-12", number: 2, topicIds: [3, 4] },
      { on: "2026-10-01", number: 1, topicIds: [1] },
    ]);
  });

  it("puts each note on its author's half", () => {
    // given
    const journal = journalOf({
      pairing: pairingOf({ myRole: "MENTEE" }),
      notes: [
        noteOf({ id: 1, mine: true }),
        noteOf({ id: 2, mine: false }),
        noteOf({ id: 3, mine: true }),
      ],
    });

    // when
    const [actual] = meetingsOf(journal);

    // then
    expect(actual!.menteeNotes.map((note) => note.id)).toEqual([1, 3]);
    expect(actual!.mentorNotes.map((note) => note.id)).toEqual([2]);
  });
});
