import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { HeartIcon } from "@/components/ui/HeartIcon";

describe("HeartIcon", () => {
  it("renders a decorative svg hidden from assistive tech", () => {
    // when
    const { container } = render(<HeartIcon />);

    // then
    const svg = container.querySelector("svg");
    expect(svg).toHaveAttribute("aria-hidden", "true");
  });

  it("is hollow by default so an un-loved heart reads as an outline", () => {
    // when
    const { container } = render(<HeartIcon />);

    // then
    expect(container.querySelector("svg")).toHaveAttribute("fill", "none");
  });

  it("fills with the current colour when loved", () => {
    // when
    const { container } = render(<HeartIcon filled />);

    // then
    expect(container.querySelector("svg")).toHaveAttribute(
      "fill",
      "currentColor",
    );
  });
});
