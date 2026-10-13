import { describe, expect, it } from "vitest";

import { applyCues, type Cue, fadeIn, part, popIn, rise, sheen } from "./cues";

function stage(): HTMLElement {
  const root = document.createElement("div");
  root.innerHTML = `<h1 data-ceremony-part="name">Backend Guild</h1>`;
  return root;
}

const nameFades: Cue = {
  selector: part("name"),
  beat: { start: 1, duration: 1 },
  style: fadeIn,
};

describe("applyCues", () => {
  it("holds a part in its starting state before its beat", () => {
    // given
    const root = stage();

    // when
    applyCues(root, [nameFades], 0);

    // then
    expect(root.querySelector("h1")?.style.opacity).toBe("0");
  });

  it("styles a part by its progress through the beat", () => {
    // given
    const root = stage();

    // when
    applyCues(root, [nameFades], 1.5);

    // then
    expect(root.querySelector("h1")?.style.opacity).toBe("0.5");
  });

  it("leaves a part in its final state after its beat", () => {
    // given
    const root = stage();

    // when
    applyCues(root, [nameFades], 5);

    // then
    expect(root.querySelector("h1")?.style.opacity).toBe("1");
  });
});

describe("style builders", () => {
  it("rises a part into place", () => {
    // when
    const actual = rise(24)(0);

    // then
    expect(actual).toEqual({ opacity: "0", transform: "translateY(24.00px)" });
  });

  it("pops a part in at full size", () => {
    // when
    const actual = popIn(1);

    // then
    expect(actual).toEqual({ opacity: "1", transform: "scale(1.000)" });
  });

  it("paints a glint through the text while the sheen runs", () => {
    // when
    const actual = sheen(0.5);

    // then
    expect(actual.color).toBe("transparent");
    expect(actual["background-clip"]).toBe("text");
  });

  it("clears the glint before and after the sheen", () => {
    // when
    const actual = [sheen(0), sheen(1)];

    // then
    expect(actual[0]?.color).toBe("");
    expect(actual[1]?.["background-image"]).toBe("");
  });
});
