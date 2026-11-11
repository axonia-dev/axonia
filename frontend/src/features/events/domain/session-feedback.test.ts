import { describe, expect, it } from "vitest";

import type { SessionFeedback } from "@/features/events/domain/session-feedback";
import {
  feedbackSectionShows,
  sessionFeedbackSchema,
  shownResult,
} from "@/features/events/domain/session-feedback";

const contractSample = {
  window: "OPEN",
  closesAt: "2026-11-13T12:00:00Z",
  role: "ATTENDEE",
  mine: { enjoyed: 9, learned: 7 },
  result: {
    state: "REVEALED",
    answers: 9,
    enjoyed: 9.1,
    learned: 7.4,
    importedFrom: "Microsoft Forms",
  },
};

describe("sessionFeedbackSchema", () => {
  it("parses the contract sample", () => {
    expect(sessionFeedbackSchema.parse(contractSample)).toEqual(contractSample);
  });

  it("parses a quiet answer with nothing given and no result", () => {
    // given
    const quiet = {
      window: "NONE",
      closesAt: null,
      role: "OTHER",
      mine: null,
      result: null,
    };

    // when
    const parsed = sessionFeedbackSchema.parse(quiet);

    // then
    expect(parsed).toEqual(quiet);
  });

  it("parses a pending result with no averages", () => {
    const pending = {
      ...contractSample,
      role: "PRESENTER",
      mine: null,
      result: {
        state: "PENDING",
        answers: 0,
        enjoyed: null,
        learned: null,
        importedFrom: null,
      },
    };

    expect(sessionFeedbackSchema.parse(pending).result?.state).toBe("PENDING");
  });

  it("rejects an unknown window", () => {
    expect(() =>
      sessionFeedbackSchema.parse({ ...contractSample, window: "LATER" }),
    ).toThrow();
  });

  it("rejects an unknown result state", () => {
    expect(() =>
      sessionFeedbackSchema.parse({
        ...contractSample,
        result: { ...contractSample.result, state: "HIDDEN" },
      }),
    ).toThrow();
  });
});

function feedback(overrides: Partial<SessionFeedback>): SessionFeedback {
  return {
    window: "OPEN",
    closesAt: "2026-11-13T12:00:00Z",
    role: "ATTENDEE",
    mine: null,
    result: null,
    ...overrides,
  };
}

describe("feedbackSectionShows", () => {
  it.each<[string, SessionFeedback | undefined, boolean]>([
    ["nothing loaded yet", undefined, false],
    ["an attendee asked while open", feedback({}), true],
    [
      "an attendee holding a receipt once closed",
      feedback({ window: "CLOSED", mine: { enjoyed: 9, learned: 7 } }),
      true,
    ],
    ["an attendee who never answered", feedback({ window: "CLOSED" }), false],
    ["a presenter asked while open", feedback({ role: "PRESENTER" }), true],
    [
      "a presenter holding a receipt once closed",
      feedback({
        role: "PRESENTER",
        window: "CLOSED",
        mine: { enjoyed: 8, learned: 6 },
      }),
      true,
    ],
    [
      "a presenter who never answered",
      feedback({ role: "PRESENTER", window: "CLOSED" }),
      false,
    ],
    ["anyone else", feedback({ role: "OTHER" }), false],
  ])("for %s is %s", (_, state, expected) => {
    expect(feedbackSectionShows(state)).toBe(expected);
  });
});

describe("shownResult", () => {
  const revealed = {
    state: "REVEALED" as const,
    answers: 9,
    enjoyed: 9.1,
    learned: 7.4,
    importedFrom: null,
  };

  it("shows a revealed result", () => {
    expect(shownResult(feedback({ result: revealed }))).toBe(revealed);
  });

  it("hides a pending result, and anything not loaded", () => {
    expect(
      shownResult(
        feedback({
          result: { ...revealed, state: "PENDING", answers: 0 },
        }),
      ),
    ).toBeNull();
    expect(shownResult(feedback({}))).toBeNull();
    expect(shownResult(undefined)).toBeNull();
  });
});
