import { describe, expect, it } from "vitest";

import { FEEDBACK_WORDS } from "@/features/events/domain/feedback-words";

describe("FEEDBACK_WORDS", () => {
  it("indexes both scales by the answer, leaving index 0 empty", () => {
    expect(FEEDBACK_WORDS.enjoyed).toHaveLength(11);
    expect(FEEDBACK_WORDS.learned).toHaveLength(11);
    expect(FEEDBACK_WORDS.enjoyed[0]).toBe("");
    expect(FEEDBACK_WORDS.learned[0]).toBe("");
  });

  it("names each enjoyed answer", () => {
    expect(FEEDBACK_WORDS.enjoyed.slice(1)).toEqual([
      "Not for me",
      "Meh",
      "Meh",
      "So so",
      "Fine",
      "Good",
      "Good",
      "Great",
      "Loved it",
      "Loved it",
    ]);
  });

  it("names each learned answer", () => {
    expect(FEEDBACK_WORDS.learned.slice(1)).toEqual([
      "Nothing new",
      "A little",
      "A little",
      "A bit",
      "Some",
      "Useful",
      "Useful",
      "A lot",
      "A lot",
      "Eye opening",
    ]);
  });

  it("never says going", () => {
    const words = [...FEEDBACK_WORDS.enjoyed, ...FEEDBACK_WORDS.learned];
    expect(words.join(" ").toLowerCase()).not.toContain("going");
  });
});
