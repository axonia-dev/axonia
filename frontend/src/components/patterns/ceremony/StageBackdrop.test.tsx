import { render } from "@testing-library/react";
import { StrictMode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { raiseCurtain } from "./curtain";
import { StageBackdrop } from "./StageBackdrop";

const animate = vi.fn(() => ({}) as Animation);

beforeEach(() => {
  HTMLElement.prototype.animate = animate;
});

afterEach(() => {
  document.querySelector("[data-ceremony-curtain]")?.remove();
  Reflect.deleteProperty(HTMLElement.prototype, "animate");
  animate.mockClear();
});

function entrances(): unknown[] {
  return animate.mock.calls.filter(
    ([keyframes]: unknown[]) =>
      JSON.stringify(keyframes) ===
      JSON.stringify([{ opacity: 0 }, { opacity: 1 }]),
  );
}

describe("StageBackdrop", () => {
  it("fades in over the page", () => {
    // when
    render(<StageBackdrop>stage</StageBackdrop>);

    // then
    expect(entrances()).toHaveLength(1);
  });

  it("stands in for an arrival curtain already dark, without fading in", () => {
    // given
    raiseCurtain();

    // when
    render(
      <StrictMode>
        <StageBackdrop>stage</StageBackdrop>
      </StrictMode>,
    );

    // then
    expect(document.querySelector("[data-ceremony-curtain]")).toBeNull();
    expect(document.querySelector("[data-ceremony-backdrop]")).not.toBeNull();
    expect(entrances()).toHaveLength(0);
  });

  it("fades in from where a lifting curtain had reached", () => {
    // given
    raiseCurtain();
    document
      .querySelector<HTMLElement>("[data-ceremony-curtain]")
      ?.style.setProperty("opacity", "0.4");

    // when
    render(<StageBackdrop>stage</StageBackdrop>);

    // then
    expect(animate).toHaveBeenCalledWith(
      [{ opacity: 0.4 }, { opacity: 1 }],
      expect.objectContaining({ duration: 180 }),
    );
  });
});
