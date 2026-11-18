import { afterEach, describe, expect, it, vi } from "vitest";

import { httpMentoringGateway } from "@/features/mentoring/adapters/http-mentoring-gateway";
import {
  availability,
  journal,
  journalOf,
  mine,
  page,
  pageOf,
  pairing,
  proposal,
  shownPairingOf,
  words,
} from "@/features/mentoring/test-data";
import { bodyOf, jsonResponse, stubFetchJson, urlOf } from "@/test/fetch";

function methodOf(init: RequestInit | undefined): string {
  return init?.method ?? "GET";
}

function stubNoContent() {
  return vi
    .spyOn(globalThis, "fetch")
    .mockResolvedValue(jsonResponse(null, 204));
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("reads", () => {
  it.each([
    [
      "page",
      () => httpMentoringGateway.page("platform"),
      "/axonia/communities/platform/mentoring",
      page,
    ],
    [
      "page with who is helping whom",
      () => httpMentoringGateway.page("platform"),
      "/axonia/communities/platform/mentoring",
      pageOf({ pairings: [shownPairingOf()] }),
    ],
    ["mine", () => httpMentoringGateway.mine(), "/axonia/mentoring/me", mine],
    [
      "presence",
      () => httpMentoringGateway.presence(),
      "/axonia/mentoring/me/presence",
      { inAMentoringGuild: true },
    ],
    [
      "journal",
      () => httpMentoringGateway.journal(11),
      "/axonia/mentoring/pairings/11",
      journal,
    ],
    [
      "journal asking to be shown",
      () => httpMentoringGateway.journal(11),
      "/axonia/mentoring/pairings/11",
      journalOf({ showing: { visibility: "ASKING", iSaidYes: true } }),
    ],
    [
      "myWords",
      () => httpMentoringGateway.myWords(),
      "/axonia/mentoring/me/words",
      [words],
    ],
  ])("%s reads and parses its resource", async (_name, call, path, payload) => {
    // given
    const fetchSpy = stubFetchJson(payload);

    // when
    const actual = await call();

    // then
    const [input, init] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(input)).toContain(path);
    expect(methodOf(init)).toBe("GET");
    expect(actual).toEqual(payload);
  });

  it.each([
    [
      "page",
      () => httpMentoringGateway.page("platform"),
      { ...page, mentors: [{ userId: "u-1" }] },
    ],
    [
      "mine",
      () => httpMentoringGateway.mine(),
      { ...mine, ongoing: [{ id: 1 }] },
    ],
    [
      "presence",
      () => httpMentoringGateway.presence(),
      { inAMentoringGuild: "yes" },
    ],
    [
      "journal",
      () => httpMentoringGateway.journal(11),
      { ...journal, topics: [{ id: 1, state: "PAUSED" }] },
    ],
    [
      "journal with a note missing its meeting day",
      () => httpMentoringGateway.journal(11),
      {
        ...journal,
        notes: journal.notes.map((note) => ({
          ...note,
          meetingOn: undefined,
        })),
      },
    ],
    [
      "journal without wordsWritten",
      () => httpMentoringGateway.journal(11),
      { ...journal, wordsWritten: undefined },
    ],
    ["myWords", () => httpMentoringGateway.myWords(), [{ pairingId: 1 }]],
  ])(
    "%s refuses a payload that breaks the contract",
    async (_name, call, payload) => {
      // given
      stubFetchJson(payload);

      // when
      const actual = call();

      // then
      await expect(actual).rejects.toThrow();
    },
  );
});

describe("setAvailability", () => {
  it("puts the availability and parses the stored one", async () => {
    // given
    const fetchSpy = stubFetchJson(availability);

    // when
    const actual = await httpMentoringGateway.setAvailability(
      "platform",
      availability,
    );

    // then
    const [input, init] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(input)).toContain(
      "/axonia/communities/platform/mentoring/availability",
    );
    expect(methodOf(init)).toBe("PUT");
    expect(bodyOf(fetchSpy)).toEqual(availability);
    expect(actual).toEqual(availability);
  });

  it("refuses an availability without the limit field", async () => {
    // given
    stubFetchJson({ takingMentees: true, lookingForMentor: false });

    // when
    const actual = httpMentoringGateway.setAvailability(
      "platform",
      availability,
    );

    // then
    await expect(actual).rejects.toThrow();
  });
});

describe("propose", () => {
  it("posts the proposal and parses the created one", async () => {
    // given
    const fetchSpy = stubFetchJson(proposal, 201);
    const input = {
      toUserId: "u-1",
      asRole: "MENTEE" as const,
      topics: ["Giving feedback"],
      message: "Happy to pair up?",
    };

    // when
    const actual = await httpMentoringGateway.propose("platform", input);

    // then
    const [url, init] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain(
      "/axonia/communities/platform/mentoring/proposals",
    );
    expect(methodOf(init)).toBe("POST");
    expect(bodyOf(fetchSpy)).toEqual(input);
    expect(actual).toEqual(proposal);
  });
});

describe("answer", () => {
  it("accepts and parses the new pairing", async () => {
    // given
    const fetchSpy = stubFetchJson(pairing);

    // when
    const actual = await httpMentoringGateway.answer(7, "accept");

    // then
    const [url, init] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain("/axonia/mentoring/proposals/7/accept");
    expect(methodOf(init)).toBe("POST");
    expect(actual).toEqual(pairing);
  });

  it("refuses an accepted pairing that breaks the contract", async () => {
    // given
    stubFetchJson({ id: 1 });

    // when
    const actual = httpMentoringGateway.answer(7, "accept");

    // then
    await expect(actual).rejects.toThrow();
  });

  it.each(["decline", "withdraw", "reopen"] as const)(
    "%s posts and yields nothing",
    async (answer) => {
      // given
      const fetchSpy = stubNoContent();

      // when
      const actual = await httpMentoringGateway.answer(7, answer);

      // then
      const [url, init] = fetchSpy.mock.calls[0] ?? [];
      expect(urlOf(url)).toContain(`/axonia/mentoring/proposals/7/${answer}`);
      expect(methodOf(init)).toBe("POST");
      expect(actual).toBeNull();
    },
  );
});
