import { describe, expect, it } from "vitest";

import { journalSchema } from "@/features/mentoring/domain/mentoring";
import { journal, pairingOf } from "@/features/mentoring/test-data";

describe("the journal schema", () => {
  it("parses the journal of the pinned contract", () => {
    // given
    const payload = {
      pairing: pairingOf({ stops: ["DONE"] }),
      writable: true,
      today: "2026-11-16",
      startedOn: "2026-09-02",
      topics: [
        {
          id: 3,
          label: "Code review habits",
          area: "code-review",
          state: "IN_PROGRESS",
          position: 0,
          pointers: [],
        },
      ],
      notes: [
        {
          id: 9,
          authorId: "u-1",
          authorName: "Alice",
          authorAvatarUrl: null,
          mine: true,
          body: "Met.",
          meetingOn: "2026-11-12",
          topicIds: [3],
          writtenAt: "2026-11-12T10:00:00Z",
          editedAt: null,
          pointers: [],
        },
      ],
      meetings: [
        {
          id: 12,
          on: "2026-11-19",
          topicIds: [3],
          plannedById: "u-2",
          plannedByName: "Bob",
          passed: true,
        },
        {
          id: 13,
          on: "2026-11-26",
          topicIds: [],
          plannedById: null,
          plannedByName: "Deleted member",
          passed: false,
        },
      ],
      wordsWritten: false,
      canWriteWords: false,
      myWords: null,
      showing: { visibility: "ASKING", iSaidYes: true },
    };

    // when
    const actual = journalSchema.parse(payload);

    // then
    expect(actual).toEqual(payload);
  });

  it("drops a nextMeeting the journal no longer carries", () => {
    // given
    const payload = { ...journal, nextMeeting: { on: "2026-11-19" } };

    // when
    const actual = journalSchema.parse(payload);

    // then
    expect(actual).not.toHaveProperty("nextMeeting");
  });

  it("refuses a journal without its meetings", () => {
    // given
    const payload = { ...journal, meetings: undefined };

    // when
    const actual = journalSchema.safeParse(payload);

    // then
    expect(actual.success).toBe(false);
  });
});
