import { describe, expect, it } from "vitest";

import {
  availabilitySchema,
  directoryPersonSchema,
  journalSchema,
  mentoringPageSchema,
  myMentoringSchema,
  nextMeetingSchema,
  noteSchema,
  pairingSummarySchema,
  placeTopic,
  pointerSchema,
  proposalSchema,
  roleSchema,
  topicSchema,
  wordsSchema,
} from "@/features/mentoring/domain/mentoring";
import {
  availability,
  journal,
  mine,
  noteOf,
  page,
  pageOf,
  pairing,
  pairingOf,
  personOf,
  pointerOf,
  proposal,
  proposalOf,
  topicOf,
  words,
  wordsOf,
} from "@/features/mentoring/test-data";

const topic = topicOf();
const note = noteOf();
const person = personOf();
const pointer = pointerOf();

describe("the mentoring schemas", () => {
  it.each([
    ["role", roleSchema, "MENTOR"],
    ["availability", availabilitySchema, availability],
    ["directory person", directoryPersonSchema, person],
    ["page", mentoringPageSchema, page],
    ["proposal", proposalSchema, proposal],
    ["pairing summary", pairingSummarySchema, pairing],
    ["my mentoring", myMentoringSchema, mine],
    ["pointer", pointerSchema, pointer],
    ["topic", topicSchema, topic],
    ["note", noteSchema, note],
    ["journal", journalSchema, journal],
    ["words", wordsSchema, words],
    [
      "next meeting",
      nextMeetingSchema,
      { on: "2026-11-19", setById: "u-2", setByName: "Bob", passed: false },
    ],
  ])("parses a %s as the backend sends it", (_name, schema, payload) => {
    // when
    const actual = schema.parse(payload);

    // then
    expect(actual).toEqual(payload);
  });

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
      nextMeeting: {
        on: "2026-11-19",
        setById: "u-2",
        setByName: "Bob",
        passed: false,
      },
      wordsWritten: false,
      canWriteWords: false,
      myWords: null,
    };

    // when
    const actual = journalSchema.parse(payload);

    // then
    expect(actual).toEqual(payload);
  });

  it("parses the page's doors, guided start and a declined person's wait", () => {
    // given
    const payload = pageOf({
      guidedStart: true,
      mentors: [
        personOf({
          proposalState: "DECLINED_RECENTLY",
          proposalsOpenOn: "2026-12-16",
        }),
      ],
    });

    // when
    const actual = mentoringPageSchema.parse(payload);

    // then
    expect(actual).toEqual(payload);
  });

  it("parses a reopenable proposal and opened words", () => {
    // given
    const reopenable = proposalOf({
      status: "DECLINED",
      reopenableUntil: "2026-12-16T10:00:00Z",
    });
    const opened = wordsOf({ openedAt: "2026-11-21T10:00:00Z" });

    // when
    const actual = [
      proposalSchema.parse(reopenable),
      wordsSchema.parse(opened),
    ];

    // then
    expect(actual).toEqual([reopenable, opened]);
  });

  it("parses My mentoring with the trailhead facts", () => {
    // given
    const payload = {
      ...mine,
      today: "2026-11-17",
      received: [{ ...proposal, lapsesOn: "2026-12-16" }],
      ongoing: [
        {
          ...pairing,
          nowTopic: "Giving feedback",
          nextMeeting: { on: "2026-11-19", passed: false },
          meetings: 2,
          notes: 3,
          lastMetOn: "2026-11-12",
        },
      ],
    };

    // when
    const actual = myMentoringSchema.parse(payload);

    // then
    expect(actual).toEqual(payload);
  });

  it("parses a mentee's own words in the journal", () => {
    // given
    const payload = { ...journal, myWords: "Thanks for the time." };

    // when
    const actual = journalSchema.parse(payload);

    // then
    expect(actual.myWords).toBe("Thanks for the time.");
  });

  it.each([
    ["an unknown role", roleSchema, "OBSERVER"],
    [
      "an availability without the limit",
      availabilitySchema,
      { takingMentees: true, lookingForMentor: false },
    ],
    ["an unknown topic state", topicSchema, { ...topic, state: "PAUSED" }],
    ["a note without an author", noteSchema, { ...note, authorId: undefined }],
    [
      "a note without its meeting day",
      noteSchema,
      { ...note, meetingOn: undefined },
    ],
    ["a retired topic state", topicSchema, { ...topic, state: "ONGOING" }],
    [
      "a pairing without its stops",
      pairingSummarySchema,
      { ...pairing, stops: undefined },
    ],
    [
      "a pointer of an unknown kind",
      pointerSchema,
      { ...pointer, kind: "VIDEO" },
    ],
    [
      "a journal without the caller's words",
      journalSchema,
      { ...journal, myWords: undefined },
    ],
    [
      "My mentoring without the caller's day",
      myMentoringSchema,
      { ...mine, today: undefined },
    ],
    [
      "a proposal with an unknown status",
      proposalSchema,
      { ...proposal, status: "PENDING" },
    ],
  ])("refuses %s", (_name, schema, payload) => {
    // when
    const actual = () => schema.parse(payload);

    // then
    expect(actual).toThrow();
  });
});

describe("placeTopic", () => {
  const todo = [0, 1, 2].map((position) =>
    topicOf({ id: position + 1, state: "TODO", position }),
  );

  function slots(topics: ReturnType<typeof placeTopic>) {
    return topics.map(({ id, state, position }) => [id, state, position]);
  }

  it("moves a sticker within its column and shifts the others", () => {
    // when
    const actual = placeTopic(todo, 3, { state: "TODO", position: 0 });

    // then
    expect(slots(actual)).toEqual([
      [3, "TODO", 0],
      [1, "TODO", 1],
      [2, "TODO", 2],
    ]);
  });

  it("closes the gap it leaves and opens a slot where it lands", () => {
    // given
    const topics = [...todo, topicOf({ id: 9, state: "DONE", position: 0 })];

    // when
    const actual = placeTopic(topics, 2, { state: "DONE", position: 0 });

    // then
    expect(slots(actual)).toEqual([
      [1, "TODO", 0],
      [3, "TODO", 1],
      [2, "DONE", 0],
      [9, "DONE", 1],
    ]);
  });

  it("puts a position past the end of the column last", () => {
    // when
    const actual = placeTopic(todo, 1, { state: "IN_PROGRESS", position: 99 });

    // then
    expect(slots(actual)).toEqual([
      [2, "TODO", 0],
      [3, "TODO", 1],
      [1, "IN_PROGRESS", 0],
    ]);
  });

  it("leaves the board as it is for an unknown topic", () => {
    // when
    const actual = placeTopic(todo, 42, { state: "DONE", position: 0 });

    // then
    expect(actual).toEqual(todo);
  });
});
