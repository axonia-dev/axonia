import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { typeAccent, TypeIcon } from "@/features/knowledge/ui/type-visual";

describe("typeAccent", () => {
  it("is deterministic for a given type id", () => {
    // when
    const actual = typeAccent(1);

    // then
    expect(actual).toBe(typeAccent(1));
    expect(actual).toMatch(/^#[0-9a-f]{6}$/i);
  });
});

describe("TypeIcon", () => {
  // DynamicIcon loads its glyph asynchronously, so assert on the wrapper that
  // carries the accent rather than the inner <svg>, which may not be present yet.
  it("renders an aria-hidden wrapper carrying the stored accent", () => {
    // when
    const { container } = render(
      <TypeIcon typeId={1} label="Slides" accentColor="#6e3ceb" />,
    );

    // then
    const wrapper = container.querySelector("span[aria-hidden]");
    expect(wrapper).toBeInTheDocument();
    expect(wrapper?.getAttribute("style")).toContain("#6e3ceb");
  });

  it("falls back to the hashed accent when none is stored", () => {
    // when
    const { container } = render(<TypeIcon typeId={1} label="Mystery" />);

    // then
    const wrapper = container.querySelector("span[aria-hidden]");
    expect(wrapper?.getAttribute("style")).toContain(typeAccent(1));
  });
});
