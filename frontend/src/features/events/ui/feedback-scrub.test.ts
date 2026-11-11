import { describe, expect, it } from "vitest";

import {
  barTone,
  feedbackWord,
  valueAt,
} from "@/features/events/ui/feedback-scrub";

const ROW = { left: 100, width: 200 };

describe("valueAt", () => {
  it("reads the bar under the pointer, one tenth of the row each", () => {
    // when / then
    expect(valueAt(101, ROW)).toBe(1);
    expect(valueAt(120, ROW)).toBe(1);
    expect(valueAt(121, ROW)).toBe(2);
    expect(valueAt(275, ROW)).toBe(9);
    expect(valueAt(300, ROW)).toBe(10);
  });

  it("holds the ends when the pointer drags past the row", () => {
    // when / then
    expect(valueAt(40, ROW)).toBe(1);
    expect(valueAt(100, ROW)).toBe(1);
    expect(valueAt(420, ROW)).toBe(10);
  });

  it("reads nothing from a row that has no layout", () => {
    // when / then
    expect(valueAt(10, { left: 0, width: 0 })).toBeNull();
  });
});

describe("barTone", () => {
  it("fills the bars up to the answer", () => {
    // when / then
    expect(barTone(3, 3, null)).toBe("on");
    expect(barTone(4, 3, null)).toBe("off");
  });

  it("leaves every bar off before any answer", () => {
    // when / then
    expect(barTone(1, null, null)).toBe("off");
  });

  it("ghosts the bars a preview reaches past the answer", () => {
    // when / then
    expect(barTone(2, 3, 6)).toBe("on");
    expect(barTone(5, 3, 6)).toBe("peek");
    expect(barTone(7, 3, 6)).toBe("off");
    expect(barTone(5, null, 6)).toBe("peek");
  });

  it("drops the solid fill above a preview below the answer", () => {
    // when / then
    expect(barTone(2, 8, 2)).toBe("on");
    expect(barTone(5, 8, 2)).toBe("off");
  });
});

describe("feedbackWord", () => {
  it("reads each question's answer as its own word", () => {
    // when / then
    expect(feedbackWord("enjoyed", 9)).toBe("Loved it");
    expect(feedbackWord("learned", 10)).toBe("Eye opening");
  });
});
