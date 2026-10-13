import { describe, expect, it } from "vitest";

import { textTargets } from "./sample-text";

describe("textTargets", () => {
  it("answers no points where the browser has no canvas", () => {
    // given jsdom, whose canvas has no 2D context
    const frame = document.createElement("div");
    const text = document.createElement("h1");
    text.textContent = "Backend Guild";
    frame.append(text);

    // when
    const actual = textTargets(text, frame);

    // then
    expect(actual).toEqual([]);
  });
});
